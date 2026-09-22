#!/usr/bin/env bash
# 单进程启动「闲置集」后端（锁定 8080），规避沙箱跨进程 localhost 隔离。
# 用途（RK-5 单进程联调方案）：在本机/连接器执行；请勿在沙箱 Bash 内跨进程 curl localhost。
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$SCRIPT_DIR/.."
BACKEND_DIR="$WORKSPACE/idlefish-backend"
if [ -d "$WORKSPACE/.m2/repository" ]; then REPO="$WORKSPACE/.m2/repository"; else REPO="$HOME/.m2/repository"; fi
cd "$BACKEND_DIR"
echo "[run-backend] 启动后端，端口 8080，日志 /tmp/idlefish-backend.log"
nohup mvn -Dmaven.repo.local="$REPO" -Dserver.port=8080 -Dspring-boot.run.fork=false \
  spring-boot:run > /tmp/idlefish-backend.log 2>&1 &
echo "[run-backend] 已启动，PID $!"
