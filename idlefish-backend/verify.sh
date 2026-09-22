#!/bin/zsh
set -u
cd /Users/mezo/Documents/WorkBuddy/2026-09-20-23-01-57/idlefish-backend
MVN=/Users/mezo/Documents/localRepository/devRepository/maven/bin/mvn
REPO=/Users/mezo/Documents/WorkBuddy/2026-09-20-23-01-57/.m2/repository
PORT=8080
BASE=http://127.0.0.1:$PORT
RUNLOG=/Users/mezo/Documents/WorkBuddy/2026-09-20-23-01-57/idlefish-backend/verify_run.log

# 绕过沙箱代理，直接访问本地
export no_proxy="127.0.0.1,localhost"
export NO_PROXY="127.0.0.1,localhost"

api() {
  curl -s --noproxy '*' -H 'Content-Type: application/json' -w '\n[HTTP %{http_code}]\n' "$@"
}
jqv() {
  python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)" 2>/dev/null
}

echo ">>> 启动应用 (port=$PORT) ..."
$MVN -Dmaven.repo.local=$REPO -Dserver.port=$PORT -Dspring-boot.run.jvmArguments="-Xmx512m" spring-boot:run > $RUNLOG 2>&1 &
APP_PID=$!
echo "    maven pid=$APP_PID"

READY=0
for i in $(seq 1 90); do
  if grep -q "Started TradeApplication" $RUNLOG 2>/dev/null; then READY=1; break; fi
  if ! kill -0 $APP_PID 2>/dev/null; then echo "!!! 应用进程退出，日志末尾:"; tail -30 $RUNLOG; exit 1; fi
  sleep 1
done
if [ $READY -ne 1 ]; then echo "!!! 启动超时，日志末尾:"; tail -30 $RUNLOG; kill $APP_PID 2>/dev/null; exit 1; fi
echo ">>> 应用已就绪"

echo "===== 1) 类目树 ====="
TREE=$(api $BASE/api/category/tree)
echo "$TREE" | head -c 500; echo
CATID=$(echo "$TREE" | jqv "['data'][0]['id']")
echo "    catId=$CATID"

echo "===== 2) 微信登录(Mock) ====="
LOGIN=$(api -X POST -d '{"code":"test_openid_001"}' $BASE/api/auth/login)
echo "$LOGIN" | head -c 500; echo
TOKEN=$(echo "$LOGIN" | jqv "['data']['token']")
echo "    token=${TOKEN:0:20}..."
AUTH="Authorization: Bearer $TOKEN"

echo "===== 3) 创建收货地址 ====="
ADDR=$(api -H "$AUTH" -X POST -d '{"receiverName":"张三","phone":"13800138000","province":"广东","city":"深圳","district":"南山区","detail":"科技园1栋","isDefault":1}' $BASE/api/address)
echo "$ADDR" | head -c 400; echo
ADDRID=$(echo "$ADDR" | jqv "['data']")
echo "    addrId=$ADDRID"

echo "===== 4) 发布商品(草稿) ====="
PUB=$(api -H "$AUTH" -X POST -d "{\"categoryId\":$CATID,\"title\":\"九成新iPhone二手手机\",\"description\":\"自用闲置，成色很新\",\"price\":299900,\"originalPrice\":599900,\"images\":[\"https://oss.idlefish.local/img1.jpg\"],\"conditionLevel\":4,\"stock\":1,\"province\":\"广东\",\"city\":\"深圳\",\"freight\":0}" $BASE/api/item/publish)
echo "$PUB" | head -c 400; echo
ITEMID=$(echo "$PUB" | jqv "['data']")
echo "    itemId=$ITEMID"

echo "===== 5) 提交上架(触发审核) ====="
SUB=$(api -H "$AUTH" -X POST $BASE/api/item/$ITEMID/submit)
echo "$SUB" | head -c 200; echo

echo "===== 6) 搜索在售商品 ====="
SEARCH=$(api "$BASE/api/search/query?keyword=%E4%BA%8C%E6%89%8B&page=1&size=10")
echo "$SEARCH" | head -c 500; echo

echo "===== 7) 创建订单 ====="
ORD=$(api -H "$AUTH" -X POST -d "{\"itemId\":$ITEMID,\"quantity\":1,\"addressId\":$ADDRID,\"remark\":\"尽快发货\"}" $BASE/api/orders/create)
echo "$ORD" | head -c 500; echo
ORDERNO=$(echo "$ORD" | jqv "['data']")
echo "    orderNo=$ORDERNO"

echo "===== 8) 支付结果通知(Mock) ====="
DET=$(api -H "$AUTH" "$BASE/api/orders/$ORDERNO")
PAYNO=$(echo "$DET" | jqv "['data']['payNo']")
echo "    payNo=$PAYNO"
NOTIFY=$(api -X POST "$BASE/api/pay/notify?payNo=$PAYNO&transactionId=MOCK_TXN_001")
echo "$NOTIFY" | head -c 200; echo

echo "===== 9) 卖家发货 ====="
SHIP=$(api -H "$AUTH" -X POST "$BASE/api/orders/$ORDERNO/ship?logisticsNo=SF1234567890")
echo "$SHIP" | head -c 200; echo

echo "===== 10) 买家确认收货(触发结算) ====="
CONF=$(api -H "$AUTH" -X POST "$BASE/api/orders/$ORDERNO/confirm")
echo "$CONF" | head -c 200; echo

echo "===== 11) 订单详情校验终态 ====="
DET2=$(api -H "$AUTH" "$BASE/api/orders/$ORDERNO")
echo "$DET2" | head -c 800; echo

echo ">>> 关闭应用 (pid=$APP_PID) ..."
kill $APP_PID 2>/dev/null
pkill -f "TradeApplication" 2>/dev/null
sleep 2
echo ">>> DONE"
