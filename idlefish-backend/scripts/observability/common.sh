#!/usr/bin/env bash
# 可观测性工具链脚本通用环境（被 up/down/smoke-test/build-check/alert-selfcheck/enable-prometheus 引用）。
# 解析项目根目录与组件路径，集中定义可覆盖的环境变量。
set -euo pipefail

# 脚本所在目录（scripts/observability）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 项目根目录（WorkBuddy 会话目录）
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# 后端模块目录
APP_DIR="$PROJECT_ROOT/idlefish-backend"
# 可观测性部署配置目录
DEPLOY_DIR="$APP_DIR/deploy/observability"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"

# ---- 可覆盖的环境变量（默认值见下方）----
export APP_URL="${APP_URL:-http://localhost:8080}"
export PROM_URL="${PROM_URL:-http://localhost:9090}"
export GRAF_URL="${GRAF_URL:-http://localhost:3000}"
export MVN_SETTINGS="${MVN_SETTINGS:-$PROJECT_ROOT/.mvn-settings-offline.xml}"
export MVN_REPO="${MVN_REPO:-/Users/mezo/Documents/localRepository/mvnRepository}"

log()  { echo "[observability] $*"; }
die()  { echo "[observability][ERROR] $*" >&2; exit 1; }

# 离线 Maven 调用封装：默认 -o 离线 + -gs 覆盖 settings + 指定本地仓库
mvn_offline() {
  cd "$APP_DIR"
  mvn -o -gs "$MVN_SETTINGS" -Dmaven.repo.local="$MVN_REPO" "$@"
}
