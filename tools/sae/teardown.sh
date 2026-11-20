#!/usr/bin/env bash
set -euo pipefail

source tools/sae/lib.sh

# 先停依赖方，再停被依赖的中间件。
stop_order=(web-h5 web-pc backend redis mysql)
summary_lines=()

note() {
  summary_lines+=("$(printf "$@")")
  printf "$@" | sed 's/`//g'
  printf '\n'
}

write_summary() {
  local status=$?
  [[ -n ${GITHUB_STEP_SUMMARY:-} ]] || return 0
  {
    if (( status == 0 )); then
      printf '## 瑜伽项目环境已释放\n\n'
    else
      printf '## 瑜伽项目环境释放未完成\n\n> [!WARNING]\n> 有步骤失败（退出码 %s），查看下面的结果和运行日志后重跑本 workflow。\n\n' "$status"
    fi
    printf '%s\n' "${summary_lines[@]}"
    printf '\nNAS 数据和 ACR 镜像保留。需要再次使用时运行 [Deploy to Alibaba Cloud SAE](%s)。\n' "$deploy_workflow_url"
  } >> "$GITHUB_STEP_SUMMARY"
}
trap write_summary EXIT

stop_app() {
  local app=$1 status order_id attempt
  status=$(sae DescribeApplicationStatus --AppId "${app_ids[$app]}")
  if jq -e '.LastChangeOrderRunning == true' <<< "$status" >/dev/null; then
    die "$prefix$app 有进行中的变更单，等它结束后重跑"
  fi
  case $(jq -er '.CurrentStatus' <<< "$status") in
    STOPPED)
      note -- '- `%s` 已是停止状态' "$prefix$app"
      return 0
      ;;
    RUNNING) ;;
    *) die "$prefix$app 状态未知" ;;
  esac
  order_id=$(sae StopApplication --AppId "${app_ids[$app]}" | jq -er '.ChangeOrderId')
  wait_order "$app" "$order_id"
  for ((attempt = 0; attempt < 60; attempt++)); do
    if sae DescribeApplicationStatus --AppId "${app_ids[$app]}" \
      | jq -e '.CurrentStatus == "STOPPED"' >/dev/null; then
      note -- '- `%s` 已停止，变更单 `%s`' "$prefix$app" "$order_id"
      return 0
    fi
    sleep 5
  done
  die "$prefix$app 停止后状态仍不是 STOPPED"
}

load_app_ids

note '### 域名解析\n'
for rr in "$pc_rr" "$h5_rr"; do
  removed=$(dns_delete "$rr")
  if [[ -n $removed ]]; then
    note -- '- `%s.%s` 已删除（原指向 `%s`）' "$rr" "$dns_zone" "$removed"
  else
    note -- '- `%s.%s` 没有解析记录' "$rr" "$dns_zone"
  fi
done

note '\n### 公网 CLB\n'
for app in "${services[@]}"; do
  binding=$(sae DescribeApplicationSlbs --AppId "${app_ids[$app]}")
  clb_id=$(jq -r '.InternetSlbId // empty' <<< "$binding")
  if [[ -z $clb_id ]] && jq -e '(.Internet // []) | length == 0' <<< "$binding" >/dev/null; then
    continue
  fi
  charge_type=$(jq -r '.InternetSlbChargeType // empty' <<< "$binding")
  unbind_internet "$app"
  if [[ -n $charge_type ]]; then
    note -- '- `%s` 已解绑 CLB `%s`，SAE 代购实例随解绑释放' "$prefix$app" "$clb_id"
  else
    note -- '- `%s` 已解绑自购 CLB `%s`，实例仍保留，需要时到 CLB 控制台释放' "$prefix$app" "$clb_id"
  fi
  found_clb=1
done
if [[ -z ${found_clb:-} ]]; then
  note -- '- 没有应用绑定公网 CLB'
fi

note '\n### SAE 应用\n'
for app in "${stop_order[@]}"; do
  stop_app "$app"
done
