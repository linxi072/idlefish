package com.idlefish.trade.export;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 导出任务持久化（ITER-T06）。
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {

    /** 按导出单号查询任务（导出单号唯一）。 */
    @Select("SELECT * FROM t_export_task WHERE export_no = #{exportNo}")
    ExportTask selectByExportNo(@Param("exportNo") String exportNo);
}
