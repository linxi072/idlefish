package com.idlefish.trade.search.enhance.dto;

import java.util.Map;

/**
 * AB 分桶结果（ITER-F04 / REQ-06 稳定分桶、REQ-07 回滚）。
 *
 * @param experimentId      实验 ID
 * @param experimentName    实验名称
 * @param group             分组：CONTROL（对照）/ TREATMENT（实验）
 * @param effective         实验是否对当前请求生效（RUNNING 且处于时间窗内）
 * @param treatmentPercent  实验组流量占比
 * @param params            解析后的策略参数（如 recallBoost / semanticRecall）
 */
public record BucketResult(Long experimentId, String experimentName, String group,
                          boolean effective, int treatmentPercent, Map<String, Object> params) {
}
