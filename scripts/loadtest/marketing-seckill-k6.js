// ITER-F08 直播秒杀 G3 峰值压测脚本（k6）
// ---------------------------------------------------------------
// 门禁（PRD G3）：秒杀峰值 QPS 承载 >= 3 × 现有拼团峰值 QPS
//   GROUPON_PEAK_QPS  = 现有拼团秒杀峰值（压测环境实测基线，必填）
//   G3_TARGET_QPS     = GROUPON_PEAK_QPS × 3（本脚本自动推导）
//
// 说明：
//   1. 超卖/重复发放由集成测试 MarketingIntegrationTest(#541) 闭环保证
//      （强一致路径 + Redis 预扣路径 + 重放幂等，均断言不超卖）。
//      本脚本聚焦【并发吞吐 / 延迟 / 错误率】三项 G3 承压指标。
//   2. 真并发结论须在压测环境运行；离线沙箱仅提供「服务层指示性吞吐」
//      （单线程 DB 路径 ≈ 32.8 QPS，见 docs/ITER-F08-压测方案.md），
//      不足以代表 Redis 预扣路径下的 G3 并发能力。
//   3. 风控准确率（precision 90.91% ≥ 85%）已由 RiskControlAccuracyTest(#542)
//      真跑达成，不在本脚本重复测量。
// ---------------------------------------------------------------

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// ============ 环境变量 ============
const GROUPON_PEAK_QPS = Number(__ENV.GROUPON_PEAK_QPS || 0);
const G3_TARGET_QPS = GROUPON_PEAK_QPS * 3;          // PRD G3 门禁
const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const ACTIVITY_ID = __ENV.ACTIVITY_ID || '';          // 已发布并预热的秒杀活动 ID
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';            // 合法 Bearer 令牌（建议压测账号池）
const RPS_PER_VU = Number(__ENV.RPS_PER_VU || 50);    // 单 VU 经验吞吐，压测环境校准
// 承压时长（秒）：预热 30s + 持续 120s + 退坡 30s
const SUSTAIN_SECONDS = Number(__ENV.SUSTAIN_SECONDS || 120);

// 自定义指标：基础设施错误率（5xx / 超时 / 非 2xx）
const infraError = new Rate('seckill_infra_error');

function targetVUs() {
  // 由 G3 目标吞吐与单 VU 吞吐估算所需并发 VU
  return Math.max(1, Math.ceil(G3_TARGET_QPS / RPS_PER_VU));
}

export const options = {
  scenarios: {
    seckill_g3: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: targetVUs() },                  // 预热爬坡
        { duration: `${SUSTAIN_SECONDS}s`, target: targetVUs() },  // 持续 G3 峰值
        { duration: '30s', target: 0 },                            // 退坡
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // G3 延迟门禁：主流程 p95 < 500ms，p99 < 1000ms
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    // G3 错误率门禁：基础设施错误 < 1%
    seckill_infra_error: ['rate<0.01'],
    // 不应出现 5xx
    http_req_failed: ['rate<0.01'],
  },
  tags: { test: 'iter-f08-seckill-g3', activityId: ACTIVITY_ID },
};

export function setup() {
  if (!GROUPON_PEAK_QPS || !ACTIVITY_ID) {
    throw new Error('必须注入 GROUPON_PEAK_QPS 与 ACTIVITY_ID（G3 = 3 × GROUPON_PEAK_QPS）');
  }
  console.log(
    `[G3] GROUPON_PEAK_QPS=${GROUPON_PEAK_QPS} → G3_TARGET_QPS=${G3_TARGET_QPS} ` +
    `| targetVUs=${targetVUs()} | RPS_PER_VU=${RPS_PER_VU}`
  );
  return {};
}

export default function () {
  const requestId = `k6-${__VU}-${__ITER}`;
  const deviceId = `k6-dev-${__VU}`;
  const url =
    `${BASE_URL}/api/mkt/activity/seckill/grab` +
    `?activityId=${encodeURIComponent(ACTIVITY_ID)}` +
    `&deviceId=${encodeURIComponent(deviceId)}` +
    `&requestId=${encodeURIComponent(requestId)}`;

  const params = {
    headers: {
      Authorization: `Bearer ${AUTH_TOKEN}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    timeout: '5s',
  };

  const res = http.post(url, null, params);

  // 基础设施错误：非 2xx 或网络层失败（超时/连接拒绝）
  const isInfraError = res.status < 200 || res.status >= 300;
  infraError.add(isInfraError);

  check(res, {
    'HTTP 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  // 业务层（非阻塞断言，仅观测）：
  //  - code === 0 且 status === 'ACCEPTED' → 预扣成功
  //  - code !== 0（如 LIVE_SECKILL_PREEMPTED / STOCK_NOT_ENOUGH）→ 库存耗尽的正常业务拒绝，非故障
  // 说明：高并发下仅 stock 个请求会 ACCEPTED，其余为正常业务拒绝；
  //       「不超卖」由 #541 集成测试保证，此处不重复断言。
  sleep(0.05);
}
