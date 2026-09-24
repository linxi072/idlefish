#!/usr/bin/env bash
# 真实后端（MySQL 本地）端到端冒烟（RK-3 真实后端验证门槛）。
# 强制：阶段二联调须在本机/连接器对真实后端跑通，禁止以 Mock 演示替代验收。
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$SCRIPT_DIR/.."
if [ -d "$WORKSPACE/.m2/repository" ]; then REPO="$WORKSPACE/.m2/repository"; else REPO="$HOME/.m2/repository"; fi
cd "$WORKSPACE/idlefish-backend"

echo "== [1] 编译验证（0 ERROR 为通过）=="
mvn -Dmaven.repo.local="$REPO" -o -DskipTests compile || mvn -Dmaven.repo.local="$REPO" -DskipTests compile

echo "== [2] 启动后端 8080 =="
bash "$SCRIPT_DIR/run-backend.sh"

echo "== [3] 等待就绪 =="
for i in $(seq 1 90); do
  if curl -s -o /dev/null http://localhost:8080/ 2>/dev/null; then echo "就绪(${i}s)"; break; fi
  sleep 1
done

echo "== [4] 冒烟：登录 -> 金额单位契约校验 =="
TOKEN=$(curl -s -X POST 'http://localhost:8080/api/auth/login?code=demo' \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data",{}).get("token",""))')
echo "token=${TOKEN:0:16}..."

echo "-- 搜索在售商品（校验 priceYuan 字段，验证金额单位契约分->元）--"
curl -s 'http://localhost:8080/api/search?page=1&size=5' -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);data=d.get("data",{});rows=data.get("rows") or data.get("records") or [];print("返回商品数:",len(rows));[print("  id=%s  price(分)=%s  priceYuan=%s"%(r.get("id"),r.get("price"),r.get("priceYuan"))) for r in rows[:3]]'

echo "-- 后台登录（校验独立鉴权，返回 admin token）--"
ADMIN_TOKEN=$(curl -s -X POST 'http://localhost:8080/api/admin/auth/login?username=admin&password=admin123' \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data",{}).get("token",""))')
echo "admin_token=${ADMIN_TOKEN:0:16}..."

echo "== [4b] 冒烟：收藏 / 图片上传 / Mock 支付（本轮新增模块）=="

echo "-- 收藏：add -> check -> remove 完整链路 --"
ITEM_ID=$(curl -s 'http://localhost:8080/api/search?page=1&size=1' -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);data=d.get("data",{});rows=data.get("rows") or data.get("records") or [];print(rows[0].get("id") if rows else "")')
echo "取在售商品 id=$ITEM_ID"
if [ -n "$ITEM_ID" ]; then
  curl -s -X POST "http://localhost:8080/api/favorite/add?itemId=$ITEM_ID" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;d=json.load(sys.stdin);print("  add -> code=%s msg=%s"%(d.get("code"),d.get("msg")))'
  curl -s "http://localhost:8080/api/favorite/check?itemId=$ITEM_ID" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;d=json.load(sys.stdin);print("  check ->",d.get("data"))'
  curl -s "http://localhost:8080/api/favorite/list?page=1&size=10" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;d=json.load(sys.stdin);print("  list -> total=",(d.get("data") or {}).get("total"))'
  curl -s -X POST "http://localhost:8080/api/favorite/remove?itemId=$ITEM_ID" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;d=json.load(sys.stdin);print("  remove -> code=%s"%(d.get("code")))'
fi

echo "-- 图片上传：上传测试文件并校验可访问 --"
TMP=$(mktemp /tmp/idlefish_up_XXXXXX.bin); head -c 2048 /dev/urandom > "$TMP"
UP=$(curl -s -X POST 'http://localhost:8080/api/file/upload' -H "Authorization: Bearer $TOKEN" -F "file=@$TMP")
echo "  upload -> $UP"
UP_URL=$(echo "$UP" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data") or "")')
if [ -n "$UP_URL" ]; then
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080$UP_URL")
  echo "  访问 $UP_URL -> HTTP $CODE"
fi
rm -f "$TMP"

echo "-- Mock 支付完成端点（Gated：仅 pay.mock=true 可用）--"
curl -s -X POST 'http://localhost:8080/api/pay/mock/NONEXIST_PAYNO' | python3 -c 'import sys,json;d=json.load(sys.stdin);print("  mock-complete(NONEXIST) -> code=%s msg=%s"%(d.get("code"),d.get("msg")))'

echo "== [5] 关闭 =="
pkill -f 'idlefish-backend' 2>/dev/null || true
echo "DONE"
