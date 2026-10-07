#!/usr/bin/env bash
# 告警自校验：确认 Prometheus 已加载自定义告警规则（Idlefish*），并校验关键指标 counter 已被应用暴露。
# 用法：./scripts/observability/alert-selfcheck.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

command -v curl >/dev/null 2>&1 || die "未检测到 curl"
command -v python3 >/dev/null 2>&1 || die "未检测到 python3"

log "== 校验 Prometheus 告警规则已加载 =="
rules_json="$(curl -fsS --max-time 5 "$PROM_URL/api/v1/rules" 2>/dev/null)" \
  || die "无法访问 Prometheus ($PROM_URL)，请确认工具链已 up"

loaded="$(printf '%s' "$rules_json" | python3 -c "import sys,json;d=json.load(sys.stdin);g=d['data']['groups'];print(sum(len(x['rules']) for x in g))")"
log "Prometheus 已加载规则总数：$loaded"

idlefish_rules="$(printf '%s' "$rules_json" | python3 -c "import sys,json,re;d=json.load(sys.stdin);s=json.dumps(d);print('\n'.join(sorted(set(re.findall(r'Idlefish[A-Za-z0-9]*', s)))))")"
if [ -z "$idlefish_rules" ]; then
  die "未检测到任何 Idlefish* 自定义告警规则，请检查 deploy/observability/alert.rules.yml 是否正确挂载"
fi
log "已加载的自定义告警规则："
printf '%s\n' "$idlefish_rules" | while read -r r; do log "  - $r"; done

log "== 校验应用暴露关键资金安全指标 =="
metrics_json="$(curl -fsS --max-time 5 "$APP_URL/actuator/metrics" 2>/dev/null)" \
  || die "无法访问应用指标端点 ($APP_URL/actuator/metrics)，请确认应用已启动"
for c in fund.flow.duplicate pay.callback.timeout pay.notify.v3.failure; do
  if printf '%s' "$metrics_json" | grep -q "\"name\":\"$c\""; then
    log "OK   指标存在：$c"
  else
    log "WARN 指标未在快照中发现：$c（可能尚未产生样本，属正常）"
  fi
done

log "告警自校验完成 ✓"
