package com.idlefish.trade.export;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导出任务（ITER-T06 / REQ-07）：每次导出落一条任务记录，便于审计追溯与后续下载。
 * <ul>
 *   <li>{@code orgIdsSnapshot}：导出时的生效机构集合（JSON 数组）；{@code null}/空 表示不受限（全量）；</li>
 *   <li>{@code status}：DONE 已完成 / FAILED 失败；</li>
 *   <li>{@code fileKey}：存储键（由 {@link ExportFileStore} 解析）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_export_task")
public class ExportTask extends BaseEntity {

    /** 导出单号（EXP 前缀，来自 IdGenerator.exportNo）。 */
    private String exportNo;

    /** 导出类型 code（见 ExportType）。 */
    private String type;

    /** 发起导出的管理员 ID（t_admin_user.id）。 */
    private Long adminId;

    /** 导出时的生效机构集合快照（JSON 数组，如 [1,2,3]）；为空表示不受限（全量）。 */
    private String orgIdsSnapshot;

    /** 实际导出数据行数。 */
    private Integer rowCount;

    /** 导出格式（目前仅 CSV）。 */
    private String format;

    /** 存储键（文件名，如 EXPxxx.csv）。 */
    private String fileKey;

    /** 下载文件名（含扩展名）。 */
    private String fileName;

    /** 任务状态：DONE 已完成 / FAILED 失败。 */
    private String status;
}
