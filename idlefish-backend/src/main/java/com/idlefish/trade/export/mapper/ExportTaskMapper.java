package com.idlefish.trade.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.export.ExportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 导出任务持久化（ITER-T06）。
 * <p>归属 {@code export.mapper} 子包以被 {@code @MapperScan("com.idlefish.trade.export.mapper")} 扫描
 * （T06 交付时位于包根导致 Spring 上下文装配失败，事件模块全量回归中发现并修复）。</p>
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<com.idlefish.trade.export.ExportTask> {

    /** 按导出单号查询任务（导出单号唯一）。 */
    @Select("SELECT * FROM t_export_task WHERE export_no = #{exportNo}")
    com.idlefish.trade.export.ExportTask selectByExportNo(@Param("exportNo") String exportNo);
}
