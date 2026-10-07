#!/usr/bin/env bash
# 冒烟测试：校验应用、Prometheus、Grafana 三个端点可达且关键指标暴露正常。
# 用法：./scripts/observability/smoke-test.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

fail=0

check() {
  local name="$1"; local url="$2"
  if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
    log "OK   $name ($url)"
  else
    log "FAIL $name ($url) — 不可达或返回非 2xx"
    fail=1
  fi
}

log "== 应用层 =="
check "应用健康"        "$APP_URL/actuator/health"
check "应用指标快照"     "$APP_URL/actuator/metrics"

log "== Prometheus =="
check "Prometheus 就绪"  "$PROM_URL/-/ready"
check "Prometheus 存活"  "$PROM_URL/-/healthy"

log "== Grafana =="
check "Grafana 健康"     "$GRAF_URL/api/health"

if [ "$fail" -ne 0 ]; then
  die "冒烟测试存在失败项，请检查各组件状态"
fi
log "冒烟测试全部通过 ✓"
