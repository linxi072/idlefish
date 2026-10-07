#!/usr/bin/env bash
# 启用 Prometheus 指标导出：以 -Pmicrometer profile 重新构建后端（暴露 /actuator/prometheus），
# 并重启应用容器使 Prometheus 能够抓取。默认零依赖内核不启用 Micrometer，此脚本用于切换到生产化指标管线。
# 用法：./scripts/observability/enable-prometheus.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

command -v docker >/dev/null 2>&1 || die "未检测到 docker，请在宿主机安装 Docker 后重试"

log "以 -Pmicrometer 重新打包后端（离线）..."
mvn_offline -Pmicrometer clean package -DskipTests \
  && log "Micrometer 构建产物已生成" \
  || die "Micrometer 构建失败，请查看 Maven 输出"

cd "$PROJECT_ROOT"
log "重启应用容器以加载新镜像..."
docker compose -f "$COMPOSE_FILE" up -d --build app

log "完成。应用现暴露 /actuator/prometheus，Prometheus 将按 prometheus.yml 每 15s 抓取一次。"
log "可访问 $PROM_URL/targets 确认 idlefish-app 目标为 UP。"
