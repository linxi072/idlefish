#!/usr/bin/env bash
# 启动可观测性工具链（Prometheus + Alertmanager + Grafana + 应用指标导出）。
# 前置：宿主机已安装 Docker 与 docker compose 插件。
# 用法：./scripts/observability/up.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

command -v docker >/dev/null 2>&1 || die "未检测到 docker，请在宿主机安装 Docker 后重试"

cd "$PROJECT_ROOT"
log "使用编排文件：$COMPOSE_FILE"
docker compose -f "$COMPOSE_FILE" up -d

log "启动完成。访问入口："
log "  Grafana    : $GRAF_URL  (admin / admin，首次登录请修改密码)"
log "  Prometheus : $PROM_URL"
log "  应用指标   : $APP_URL/actuator/metrics  (非 micrometer 模式)"
log "  应用健康   : $APP_URL/actuator/health"
