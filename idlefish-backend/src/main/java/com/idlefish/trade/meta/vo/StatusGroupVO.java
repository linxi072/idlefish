package com.idlefish.trade.meta.vo;

import java.util.List;

/**
 * 一组状态字典（按业务域聚合）。
 *
 * @param domain 业务域标识（order / item / pay / refund / dispute / audit）
 * @param label  业务域中文名
 * @param items  该域下的状态列表
 */
public record StatusGroupVO(String domain, String label, List<StatusMetaVO> items) {
}
