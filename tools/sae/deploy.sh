#!/usr/bin/env bash
set -euo pipefail

config=.claude/skills/yoga-cloud-deploy/config/sae.config.json
images_dir=${1:?Missing image artifact directory}
: "${ACR_USERNAME:?Set the sae environment secret ACR_USERNAME}"
: "${ACR_PASSWORD:?Set the sae environment secret ACR_PASSWORD}"
: "${GITHUB_SHA:?}" "${GITHUB_RUN_ID:?}" "${GITHUB_RUN_ATTEMPT:?}"

region=$(jq -er '.region' "$config")
namespace=$(jq -er '.namespace' "$config")
prefix=$(jq -er '.app_prefix' "$config")
push_host=$(jq -er '.registry.push_host' "$config")
pull_host=$(jq -er '.registry.pull_host' "$config")
registry=${push_host%%/*}
release_tag="$GITHUB_SHA-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
services=(mysql redis backend web-pc web-h5)
release_apps=(backend web-pc web-h5)
declare -A app_ids image_urls pull_secrets previous_images

die() {
  printf '::error::%s\n' "$*" >&2
  exit 1
}

sae() {
  local response
  response=$(aliyun sae "$@" --region "$region" --header Content-Type=application/json) || return
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
    die "公网 CLB 已失效，请先在 SAE 解除旧绑定再重试"
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

public_url() {
  local binding=$1 target_port=$2 address listener scheme port host
  address=$(jq -er '.InternetIp | select(type == "string" and length > 0)' <<< "$binding") \
    || die "SAE 尚未返回公网 IP"
  listener=$(jq -er --argjson target "$target_port" '
    [(.Internet // [])[] | select(.TargetPort == $target) | select(.Protocol | IN("TCP", "HTTP", "HTTPS"))]
    | sort_by(.Port != $target, .Port) | first | select(. != null)
    | [(.Protocol | if . == "HTTPS" then "https" else "http" end), .Port] | @tsv
  ' <<< "$binding") || die "公网 CLB 缺少转发到容器端口 $target_port 的监听"
  IFS=$'\t' read -r scheme port <<< "$listener"
  host=$address
  if [[ $address == *:* ]]; then
    host="[$address]"
  fi
  printf '%s://%s:%s\n' "$scheme" "$host" "$port"
}

report_public_url() {
  local label=$1 url=$2 api_url=$3
  local curl_options=(--fail --silent --show-error --retry 8 --retry-all-errors --max-time 15)
  curl "${curl_options[@]}" -o /dev/null "$url"
  curl "${curl_options[@]}" "$api_url" | jq -e '.code == 200' >/dev/null
  printf '%s %s\n' "$label" "$url"
  if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
    printf -- '- [%s](%s)\n' "$label" "$url" >> "$GITHUB_STEP_SUMMARY"
  fi
}

apps=$(sae ListApplications --RegionId "$region" --NamespaceId "$namespace" \
  --FieldType appName --FieldValue "$prefix" --CurrentPage 1 --PageSize 50)
for app in "${services[@]}"; do
  name="$prefix$app"
  app_ids[$app]=$(jq -er --arg name "$name" '
    [.Applications[] | select(.AppName == $name and .AppType == "Image")]
    | select(length == 1) | .[0].AppId' <<< "$apps") || die "Expected one image-based SAE app named $name"
  status=$(sae DescribeApplicationStatus --AppId "${app_ids[$app]}")
  jq -e '(.CurrentStatus == "RUNNING" or .CurrentStatus == "STOPPED") and .LastChangeOrderRunning != true' \
    <<< "$status" >/dev/null || die "$name has a change in progress or an unknown state"
done

printf '%s' "$ACR_PASSWORD" | docker login "$registry" --username "$ACR_USERNAME" --password-stdin
unset ACR_PASSWORD ACR_USERNAME
trap 'docker logout "$registry" >/dev/null' EXIT

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

if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
  {
    printf '### SAE 镜像版本\n\n'
    for app in "${release_apps[@]}"; do
      printf -- '- `%s` 发布镜像 `%s`，发布前镜像 `%s`\n' \
        "$app" "${image_urls[$app]}" "${previous_images[$app]}"
    done
  } >> "$GITHUB_STEP_SUMMARY"
fi

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
  if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
    printf -- '- `%s` 已运行，发布单 `%s`\n' "$app" "$order_id" >> "$GITHUB_STEP_SUMMARY"
  fi
done

binding=$(ensure_public_binding)
pc_url=$(public_url "$binding" 80)
h5_url=$(public_url "$binding" 8081)
if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
  printf '\n### 公网访问\n\n公网 IP `%s` · CLB `%s`\n\n' \
    "$(jq -r '.InternetIp' <<< "$binding")" "$(jq -r '.InternetSlbId' <<< "$binding")" >> "$GITHUB_STEP_SUMMARY"
fi
report_public_url '打开管理端登录页' "$pc_url/login" "$pc_url/prod-api/captchaImage"
report_public_url '打开 H5 用户端' "$h5_url/" "$h5_url/api/coaches/featured"
