#!/usr/bin/env bash
# 由 deploy.sh 和 teardown.sh 引用，依赖 aliyun CLI 与 jq。
# shellcheck disable=SC2034

config=.claude/skills/yoga-cloud-deploy/config/sae.config.json
region=$(jq -er '.region' "$config")
namespace=$(jq -er '.namespace' "$config")
prefix=$(jq -er '.app_prefix' "$config")
dns_zone=$(jq -er '.public_domain.zone' "$config")
pc_rr=$(jq -er '.public_domain.records["web-pc"]' "$config")
h5_rr=$(jq -er '.public_domain.records["web-h5"]' "$config")
services=(mysql redis backend web-pc web-h5)
repo_url="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-duo2530/techplant-yoga}"
deploy_workflow_url="$repo_url/actions/workflows/deploy-sae.yml"
teardown_workflow_url="$repo_url/actions/workflows/teardown-sae.yml"
declare -A app_ids

die() {
  printf '::error::%s\n' "$*" >&2
  exit 1
}

# 只读接口遇到网络错误时重试；写接口不重试，避免重复提交变更。
aliyun_call() {
  local attempts=1 attempt
  case $2 in
    Describe*|List*) attempts=4 ;;
  esac
  for ((attempt = 1; ; attempt++)); do
    aliyun "$@" && return 0
    (( attempt < attempts )) || return 1
    echo "::warning::aliyun $1 $2 调用失败，5 秒后重试（$attempt/$((attempts - 1))）" >&2
    sleep 5
  done
}

sae() {
  local response
  response=$(aliyun_call sae "$@" --region "$region" --header Content-Type=application/json) || return
  if ! jq -e '.Success == true and (.Code | tostring) == "200"' <<< "$response" >/dev/null; then
    jq -r --arg operation "$1" '"\($operation) failed (\(.ErrorCode // .Code), request \(.RequestId))"' <<< "$response" >&2
    return 1
  fi
  jq -e '.Data' <<< "$response"
}

wait_order() {
  local app=$1 order_id=$2 status attempt
  echo "$app SAE change order: $order_id"
  for ((attempt = 0; attempt < 120; attempt++)); do
    status=$(sae DescribeChangeOrder --ChangeOrderId "$order_id" | jq -er '.Status')
    case "$status" in
      2) return 0 ;;
      0|1|9|12) sleep 5 ;;
      8|11) die "$app SAE change order $order_id requires manual action (status $status)" ;;
      *) die "$app SAE change order $order_id failed (status $status)" ;;
    esac
  done
  die "$app SAE change order $order_id timed out"
}

load_app_ids() {
  local apps app name
  apps=$(sae ListApplications --RegionId "$region" --NamespaceId "$namespace" \
    --FieldType appName --FieldValue "$prefix" --CurrentPage 1 --PageSize 50)
  for app in "${services[@]}"; do
    name="$prefix$app"
    app_ids[$app]=$(jq -er --arg name "$name" '
      [.Applications[] | select(.AppName == $name and .AppType == "Image")]
      | select(length == 1) | .[0].AppId' <<< "$apps") || die "Expected one image-based SAE app named $name"
  done
}

# SAE 代购的 CLB 随解绑释放；自购实例解绑后仍保留在 CLB 控制台。
unbind_internet() {
  local app=$1 order_id binding attempt
  order_id=$(sae UnbindSlb --AppId "${app_ids[$app]}" --Internet true | jq -er '.ChangeOrderId')
  wait_order "$app" "$order_id" >&2
  for ((attempt = 0; attempt < 24; attempt++)); do
    binding=$(sae DescribeApplicationSlbs --AppId "${app_ids[$app]}")
    if jq -e '(.InternetSlbId // "") == "" and ((.Internet // []) | length == 0)' <<< "$binding" >/dev/null; then
      return 0
    fi
    sleep 5
  done
  die "$app 解绑公网 CLB 后，SAE 仍返回公网绑定"
}

alidns() {
  aliyun_call alidns "$@" --region "$region"
}

dns_records() {
  local rr=$1
  alidns DescribeDomainRecords --DomainName "$dns_zone" --RRKeyWord "$rr" --PageSize 500 \
    | jq -ce --arg rr "$rr" '[.DomainRecords.Record[] | select(.RR == $rr)]'
}

# 输出 created、updated 或 unchanged。
dns_upsert() {
  local rr=$1 ip=$2 records record_id
  records=$(dns_records "$rr")
  jq -e 'all(.[]; .Type == "A")' <<< "$records" >/dev/null \
    || die "$rr.$dns_zone 已有非 A 类型的解析记录，请先在云解析控制台处理"
  case $(jq length <<< "$records") in
    0)
      alidns AddDomainRecord --DomainName "$dns_zone" --RR "$rr" --Type A --Value "$ip" --TTL 600 >/dev/null
      echo created
      ;;
    1)
      if jq -e --arg ip "$ip" '.[0].Value == $ip' <<< "$records" >/dev/null; then
        echo unchanged
      else
        record_id=$(jq -er '.[0].RecordId' <<< "$records")
        alidns UpdateDomainRecord --RecordId "$record_id" --RR "$rr" --Type A --Value "$ip" --TTL 600 >/dev/null
        echo updated
      fi
      ;;
    *) die "$rr.$dns_zone 有多条 A 记录，请先在云解析控制台处理" ;;
  esac
}

# 输出被删除记录的 IP，以空格分隔；没有记录时输出空串。
dns_delete() {
  local rr=$1 records record_id deleted=()
  records=$(dns_records "$rr")
  while IFS=$'\t' read -r record_id value; do
    [[ -n $record_id ]] || continue
    alidns DeleteDomainRecord --RecordId "$record_id" >/dev/null
    deleted+=("$value")
  done < <(jq -r '.[] | select(.Type == "A") | [.RecordId, .Value] | @tsv' <<< "$records")
  printf '%s' "${deleted[*]}"
}

# 向域名的权威服务器确认 A 记录已生效，绕开递归解析器的缓存。
wait_dns() {
  local fqdn=$1 ip=$2 nameserver attempt
  if ! command -v dig >/dev/null; then
    echo "::warning::未找到 dig，跳过 $fqdn 的权威解析检查"
    return 0
  fi
  nameserver=$(dig +short NS "$dns_zone" | head -n 1)
  [[ -n $nameserver ]] || die "无法查询 $dns_zone 的权威 DNS 服务器"
  for ((attempt = 0; attempt < 24; attempt++)); do
    if [[ $(dig +short "@$nameserver" "$fqdn" A) == "$ip" ]]; then
      return 0
    fi
    sleep 5
  done
  die "$fqdn 在权威服务器 $nameserver 上未解析到 $ip"
}
