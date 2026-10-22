package com.idlefish.trade.search.serving.dto;

import com.idlefish.trade.item.vo.ItemVO;

import java.util.List;

/**
 * 分页传输对象（ITER-T04）：远程模式回传结构与 MyBatis-Plus {@code Page} JSON 对齐
 * （records / total / size / current / pages），客户端再组装为 {@code Page<ItemVO>}，
 * 避免跨进程传递框架分页类型。
 */
public class ItemPage {

    private List<ItemVO> records;
    private long total;
    private long size;
    private long current;
    private long pages;

    public List<ItemVO> getRecords() {
        return records;
    }

    public void setRecords(List<ItemVO> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }

    /** 转为 MyBatis-Plus 分页对象（保持既有控制器响应结构不变）。 */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<ItemVO> toPage() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ItemVO> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        Math.max(current, 1), Math.max(size, 1), total);
        page.setRecords(records == null ? List.of() : records);
        return page;
    }
}
