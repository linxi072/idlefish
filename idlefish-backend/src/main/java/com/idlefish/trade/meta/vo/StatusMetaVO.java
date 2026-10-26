package com.idlefish.trade.meta.vo;

/**
 * 状态元数据：供多端（小程序 / pc-admin / H5）统一渲染名称与语义色，避免各端重复定义与色值漂移。
 * <p>语义色采用 Element Plus 默认调色板（pc-admin 已落地），小程序 / H5 按同名义映射即可对齐。</p>
 *
 * @param code        状态枚举 code（与后端实体字段一致，如 order.status=pending_pay）
 * @param name        中文展示名
 * @param color       语义色（HEX），由 {@link MetaDictionary} 统一维护
 * @param description 业务语义说明
 * @param terminal    是否终态（不可再流转）
 * @param open        是否未结（流程进行中，如维权/退款）
 */
public record StatusMetaVO(String code, String name, String color, String description,
                          boolean terminal, boolean open) {
}
