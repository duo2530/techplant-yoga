#!/usr/bin/env bash
set -euo pipefail

source tools/sae/lib.sh

images_dir=${1:?Missing image artifact directory}
: "${ACR_USERNAME:?Set the sae environment secret ACR_USERNAME}"
: "${ACR_PASSWORD:?Set the sae environment secret ACR_PASSWORD}"
: "${GITHUB_SHA:?}" "${GITHUB_RUN_ID:?}" "${GITHUB_RUN_ATTEMPT:?}"

push_host=$(jq -er '.registry.push_host' "$config")
pull_host=$(jq -er '.registry.pull_host' "$config")
registry=${push_host%%/*}
release_tag="$GITHUB_SHA-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
release_apps=(backend web-pc web-h5)
declare -A image_urls pull_secrets previous_images

ensure_running() {
  local app=$1 status order_id attempt
  status=$(sae DescribeApplicationStatus --AppId "${app_ids[$app]}")
  case $(jq -er '.CurrentStatus' <<< "$status") in
    STOPPED)
      order_id=$(sae StartApplication --AppId "${app_ids[$app]}" | jq -er '.ChangeOrderId')
      wait_order "$app" "$order_id"
      ;;
    RUNNING) ;;
    *) die "$app application state is unknown" ;;
  esac

  for ((attempt = 0; attempt < 60; attempt++)); do
    status=$(sae DescribeApplicationStatus --AppId "${app_ids[$app]}")
    if jq -e '.CurrentStatus == "RUNNING" and (.RunningInstances > 0)' <<< "$status" >/dev/null; then
      return 0
    fi
    sleep 5
  done
  die "$app has no running instance"
}

ensure_public_binding() {
  local app_id=${app_ids[web-pc]} binding clb_id charge_type listeners order_id attempt
  binding=$(sae DescribeApplicationSlbs --AppId "$app_id")
  if jq -e '.InternetSlbExpired == true' <<< "$binding" >/dev/null; then
    echo "公网 CLB $(jq -r '.InternetSlbId' <<< "$binding") 已失效，先解除旧绑定" >&2
    unbind_internet web-pc
    binding=$(sae DescribeApplicationSlbs --AppId "$app_id")
  fi
  clb_id=$(jq -r '.InternetSlbId // empty' <<< "$binding")
  if [[ -z $clb_id ]] || ! jq -e 'any((.Internet // [])[]; .TargetPort == 8081)' <<< "$binding" >/dev/null; then
    listeners=$(jq -cer '
      (.Internet // [])
      | if length == 0 then [{Port:80,TargetPort:80,Protocol:"TCP"}] else . end
      | select(all(.[]; .Port != 8081))
      | . + [{Port:8081,TargetPort:8081,Protocol:"TCP"}]
      | map(del(.CreateTime) | with_entries(.key |= (.[0:1] | ascii_downcase) + .[1:]))
    ' <<< "$binding") || die "公网端口 8081 已被现有监听使用"
    local args=(BindSlb --AppId "$app_id" --Internet "$listeners")
    charge_type=$(jq -r '.InternetSlbChargeType // empty' <<< "$binding")
    if [[ -n $clb_id && -z $charge_type ]]; then
      args+=(--InternetSlbId "$clb_id")
    else
      args+=(--InternetSlbChargeType "${charge_type:-PayByCLCU}")
    fi
    order_id=$(sae "${args[@]}" | jq -er '.ChangeOrderId')
    wait_order web-pc "$order_id" >&2
    for ((attempt = 0; attempt < 12; attempt++)); do
      binding=$(sae DescribeApplicationSlbs --AppId "$app_id")
      if jq -e '(.InternetIp // "") != "" and any(.Internet[]; .TargetPort == 8081)' <<< "$binding" >/dev/null; then
        break
      fi
      sleep 5
    done
    if [[ -n $clb_id ]] && ! jq -e --arg id "$clb_id" '.InternetSlbId == $id' <<< "$binding" >/dev/null; then
      die "更新监听后，SAE 返回的 CLB 实例与发布前不一致"
    fi
  fi
  printf '%s\n' "$binding"
}

public_ip() {
  local address
  address=$(jq -er '.InternetIp | select(type == "string" and length > 0)' <<< "$1") \
    || die "SAE 尚未返回公网 IP"
  [[ $address != *:* ]] || die "公网地址 $address 不是 IPv4，无法写入 A 记录"
  printf '%s\n' "$address"
}

public_port() {
  local binding=$1 target_port=$2
  jq -er --argjson target "$target_port" '
    [(.Internet // [])[] | select(.TargetPort == $target) | select(.Protocol | IN("TCP", "HTTP"))]
    | sort_by(.Port != $target, .Port) | first | select(. != null) | .Port
  ' <<< "$binding" || die "公网 CLB 缺少转发到容器端口 $target_port 的 HTTP 监听"
}

site_url() {
  local host=$1 port=$2
  if [[ $port == 80 ]]; then
    printf 'http://%s\n' "$host"
  else
    printf 'http://%s:%s\n' "$host" "$port"
  fi
}

# 用 --resolve 把域名直接指向 CLB，验证经由公网入口的访问，不受递归解析缓存影响。
check_site() {
  local host=$1 port=$2 ip=$3 page=$4 api=$5 base
  local curl_options=(--fail --silent --show-error --retry 8 --retry-all-errors --max-time 15
    --resolve "$host:$port:$ip")
  base=$(site_url "$host" "$port")
  curl "${curl_options[@]}" -o /dev/null "$base$page"
  curl "${curl_options[@]}" "$base$api" | jq -e '.code == 200' >/dev/null
}

summary_details=$(mktemp)
pc_url='' h5_url='' public_info=''

summary() {
  printf "$@" >> "$summary_details"
}

write_summary() {
  local status=$?
  docker logout "$registry" >/dev/null 2>&1 || true
  if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
    {
      printf '## 瑜伽项目工作台\n\n'
      if [[ -n $pc_url ]]; then
        printf '| 操作 | 入口 |\n| --- | --- |\n'
        printf '| 打开管理端（PC） | [%s/login](%s/login) |\n' "$pc_url" "$pc_url"
        printf '| 打开 H5 用户端 | [%s/](%s/) |\n' "$h5_url" "$h5_url"
        printf '| 释放环境 | [停止应用、释放 CLB、删除解析](%s)，打开后点 **Run workflow** |\n' "$teardown_workflow_url"
        printf '\n%s\n' "$public_info"
      else
        printf '> [!WARNING]\n> 本次发布未完成（退出码 %s），没有可用的访问地址。' "$status"
        printf '已启动的应用和 CLB 仍在计费，可以[释放环境](%s)后重新发布。\n' "$teardown_workflow_url"
      fi
      printf '\n<details><summary>发布明细</summary>\n\n'
      cat "$summary_details"
      printf '\n</details>\n'
    } >> "$GITHUB_STEP_SUMMARY"
  fi
  rm -f "$summary_details"
}
trap write_summary EXIT

load_app_ids
for app in "${services[@]}"; do
  status=$(sae DescribeApplicationStatus --AppId "${app_ids[$app]}")
  jq -e '(.CurrentStatus == "RUNNING" or .CurrentStatus == "STOPPED") and .LastChangeOrderRunning != true' \
    <<< "$status" >/dev/null || die "$prefix$app has a change in progress or an unknown state"
done

printf '%s' "$ACR_PASSWORD" | docker login "$registry" --username "$ACR_USERNAME" --password-stdin
unset ACR_PASSWORD ACR_USERNAME

for app in "${release_apps[@]}"; do
  repo=$(jq -er --arg app "$app" '.apps[$app].image | split(":")[0]' "$config")
  tag="$repo:$app-$release_tag"
  push_url="$push_host/$tag"
  image_urls[$app]="$pull_host/$tag"
  existing=$(sae DescribeApplicationConfig --AppId "${app_ids[$app]}")
  pull_secrets[$app]=$(jq -r '.ImagePullSecrets // empty' <<< "$existing")
  previous_images[$app]=$(jq -er '.ImageUrl' <<< "$existing")

  docker load --input "$images_dir/$app.tar.gz"
  docker tag "yoga-release:$app" "$push_url"
  docker push "$push_url"
done

summary '### SAE 镜像版本\n\n'
for app in "${release_apps[@]}"; do
  summary -- '- `%s` 发布镜像 `%s`，发布前镜像 `%s`\n' "$app" "${image_urls[$app]}" "${previous_images[$app]}"
done
summary '\n### 发布单\n\n'

ensure_running mysql
ensure_running redis

for app in "${release_apps[@]}"; do
  args=(DeployApplication --AppId "${app_ids[$app]}" --ImageUrl "${image_urls[$app]}"
    --ChangeOrderDesc "GitHub $release_tag")
  if [[ -n ${pull_secrets[$app]} ]]; then
    args+=(--ImagePullSecrets "${pull_secrets[$app]}")
  fi
  order_id=$(sae "${args[@]}" | jq -er '.ChangeOrderId')
  wait_order "$app" "$order_id"
  ensure_running "$app"
  summary -- '- `%s` 已运行，发布单 `%s`\n' "$app" "$order_id"
done

binding=$(ensure_public_binding)
ip=$(public_ip "$binding")
pc_port=$(public_port "$binding" 80)
h5_port=$(public_port "$binding" 8081)
pc_host="$pc_rr.$dns_zone"
h5_host="$h5_rr.$dns_zone"

summary '\n### 域名解析\n\n'
for rr in "$pc_rr" "$h5_rr"; do
  result=$(dns_upsert "$rr" "$ip")
  summary -- '- `%s.%s` A `%s`（%s）\n' "$rr" "$dns_zone" "$ip" "$result"
done
wait_dns "$pc_host" "$ip"
wait_dns "$h5_host" "$ip"

check_site "$pc_host" "$pc_port" "$ip" /login /prod-api/captchaImage
check_site "$h5_host" "$h5_port" "$ip" / /api/coaches/featured

pc_url=$(site_url "$pc_host" "$pc_port")
h5_url=$(site_url "$h5_host" "$h5_port")
public_info=$(printf '公网 IP `%s` · CLB `%s`。新解析可能要几分钟才能在本地网络生效，域名打不开时可以先用 [管理端 IP 入口](%s) 和 [H5 IP 入口](%s)。' \
  "$ip" "$(jq -r '.InternetSlbId' <<< "$binding")" "$(site_url "$ip" "$pc_port")/login" "$(site_url "$ip" "$h5_port")/")
printf '管理端 %s/login\nH5 %s/\n' "$pc_url" "$h5_url"
