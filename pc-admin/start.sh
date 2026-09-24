#!/usr/bin/env bash
# 启动 PC 运营后台静态服务（免构建，依赖 CDN：Vue/Element Plus/axios/ECharts）
# 用法：./start.sh [端口]   默认 8787
cd "$(dirname "$0")" || exit 1
PORT="${1:-8787}"
echo "闲置集 PC 运营后台已启动： http://localhost:${PORT}   （Ctrl+C 停止）"
exec python3 -m http.server "${PORT}"
