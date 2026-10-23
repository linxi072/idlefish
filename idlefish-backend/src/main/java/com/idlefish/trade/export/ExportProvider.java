package com.idlefish.trade.export;

import java.util.List;

/**
 * 导出数据提供者（ITER-T06 策略模式）：每种可导出实体对应一个实现，
 * 负责声明表头、并在已校验的数据范围（{@link ExportContext}）内取数。
 * <p>
 * 范围约束由 {@link ExportService} 在调用前完成（越权拒绝 + 审计），本层仅按
 * {@link ExportContext#getOrgIds()} 过滤：{@code null} 表示不受限导出全量。
 */
public interface ExportProvider {

    /** 本提供者对应的导出类型。 */
    ExportType type();

    /** 该实体是否带 org_id 列（决定是否受数据范围约束）。 */
    boolean hasOrgScope();

    /** 表头（CSV 首行）。 */
    List<String> headers();

    /**
     * 取数并转换为 CSV 行（每行与 {@link #headers()} 等长）。
     *
     * @param ctx 已校验的生效范围与行数上限
     */
    List<List<String>> rows(ExportContext ctx);
}
