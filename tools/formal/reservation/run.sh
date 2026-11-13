#!/usr/bin/env bash
# 用 TLC 穷举检查预约并发协议模型 ReservationProtocol.tla。
# 用法：run.sh            默认检查与已知发现
#       run.sh all        另加变异检查，证明各不变量能失败
#       run.sh <配置名>   只跑一个配置并打印 TLC 完整输出（含反例）
set -uo pipefail

TLA_VERSION=1.8.0
TLA_SHA256=e1f11b75c4b5bc4478ba0ac5cb6ae28e40a01b0016bc33e0aa97da22dfdbcd7f
TLA_URL=https://github.com/tlaplus/tlaplus/releases/download/v$TLA_VERSION/tla2tools.jar
IMAGE=eclipse-temurin:17-jre

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
cache=$root/tools/formal/.cache
jar=tla2tools-$TLA_VERSION.jar
out=$cache/out/reservation
shown=tools/formal/.cache/out/reservation

# 当前代码下必须通过
checks=(QueueRace RefundRace AutoCancel LastSeat StatusRace CrossQueue)
# 当前代码已知会违反：配置:不变量:标签
findings=(
    "FindingStatusTerminal:CancelledIsTerminal:#36"
    "FindingCrossQueueLockCycle:NoLockCycle:#40"
)
# 破坏版本必须被抓到：配置:不变量
mutations=(
    "MutationRR:RefundExactlyOnce"
    "MutationNoScheduleLock:NoOversell"
    "MutationDuplicate:NoDuplicateOpen"
)

hostpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

dock() { MSYS_NO_PATHCONV=1 docker run --rm -v "$(hostpath "$root"):/work" "$@"; }

ensure_jar() {
    mkdir -p "$cache" "$out"
    if [ ! -f "$cache/$jar" ]; then
        echo "downloading tla2tools v$TLA_VERSION"
        dock "$IMAGE" curl -fsSL -o "/work/tools/formal/.cache/$jar" "$TLA_URL" || return 1
    fi
    local got
    got=$(dock "$IMAGE" sha256sum "/work/tools/formal/.cache/$jar" | cut -d' ' -f1)
    if [ "$got" != "$TLA_SHA256" ]; then
        echo "FAIL tla2tools SHA-256 $got, expected $TLA_SHA256" >&2
        echo "     v$TLA_VERSION is a GitHub pre-release that upstream rebuilds; see SKILL.md before changing the pin" >&2
        return 1
    fi
}

# 跑一个配置，结果写入 $out/<配置>.out；输出“违反的规则|状态数|耗时”
tlc() {
    dock -w /work/tools/formal/reservation "$IMAGE" \
        java -XX:+UseParallelGC -cp "/work/tools/formal/.cache/$jar" tlc2.TLC \
        -workers auto -metadir /tmp/tlc -noGenerateSpecTE -config "$1.cfg" ReservationProtocol.tla \
        >"$out/$1.out" 2>&1
    local broke states took
    broke=$(grep -oE 'Invariant [A-Za-z]+ is violated|Deadlock reached' "$out/$1.out" | head -1 \
            | sed -e 's/Invariant \([A-Za-z]*\) is violated/\1/' -e 's/Deadlock reached/deadlock/')
    if [ -z "$broke" ] && ! grep -q 'No error has been found' "$out/$1.out"; then broke=error; fi
    states=$(grep -oE '[0-9]+ distinct states found' "$out/$1.out" | tail -1 | cut -d' ' -f1)
    took=$(sed -n 's/^Finished in \([^ ]*\) .*/\1/p' "$out/$1.out" | tail -1)
    printf '%s|%s|%s' "$broke" "${states:-?}" "${took:-?}"
}

failed=0
report() {  # 状态 配置 说明
    printf '%-30s %-28s %s\n' "$1" "$2" "$3"
    case $1 in FAIL*) failed=1 ;; esac
}

expect_pass() {
    local r b s t
    r=$(tlc "$1"); IFS='|' read -r b s t <<<"$r"
    if [ -z "$b" ]; then report PASS "$1" "$s states, $t"
    else report FAIL "$1" "$b violated, $s states, see $shown/$1.out"; fi
}

expect_break() {  # 配置 不变量 成功时的状态标签
    local r b s t
    r=$(tlc "$1"); IFS='|' read -r b s t <<<"$r"
    if [ "$b" = "$2" ]; then report "$3" "$1" "$2 violated, $s states, $t, trace in $shown/$1.out"
    elif [ -z "$b" ]; then report "FAIL no longer breaks" "$1" "$2 holds; update SKILL.md if the code changed on purpose"
    else report FAIL "$1" "expected $2, got $b, see $shown/$1.out"; fi
}

ensure_jar || exit 2

if [ $# -eq 1 ] && [ "$1" != all ]; then
    [ -f "$here/$1.cfg" ] || { echo "no config $1.cfg" >&2; exit 2; }
    tlc "$1" >/dev/null
    cat "$out/$1.out"
    grep -q 'No error has been found' "$out/$1.out"
    exit $?
fi

for c in "${checks[@]}"; do expect_pass "$c"; done
for f in "${findings[@]}"; do IFS=: read -r c i tag <<<"$f"; expect_break "$c" "$i" "KNOWN FINDING $tag"; done
if [ "${1:-}" = all ]; then
    for m in "${mutations[@]}"; do IFS=: read -r c i <<<"$m"; expect_break "$c" "$i" "CAUGHT"; done
fi
exit $failed
