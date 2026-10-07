#!/usr/bin/env bash
# 停止并移除可观测性工具链容器（保留卷数据，不会删除 Prometheus/Grafana 持久化目录）。
# 用法：./scripts/observability/down.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

command -v docker >/dev/null 2>&1 || die "未检测到 docker，请在宿主机安装 Docker 后重试"

cd "$PROJECT_ROOT"
log "停止可观测性工具链..."
docker compose -f "$COMPOSE_FILE" down
log "已停止。如需彻底清理卷，请手动执行：docker compose -f $COMPOSE_FILE down -v"
