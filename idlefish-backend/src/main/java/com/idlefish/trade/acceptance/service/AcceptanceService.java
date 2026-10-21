package com.idlefish.trade.acceptance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.acceptance.closure.AcceptanceClosureCalculator;
import com.idlefish.trade.acceptance.closure.ClosureResult;
import com.idlefish.trade.acceptance.dto.AcceptanceDefectCreateDTO;
import com.idlefish.trade.acceptance.dto.AcceptanceResultDTO;
import com.idlefish.trade.acceptance.entity.AcceptanceCase;
import com.idlefish.trade.acceptance.entity.AcceptanceDefect;
import com.idlefish.trade.acceptance.mapper.AcceptanceCaseMapper;
import com.idlefish.trade.acceptance.mapper.AcceptanceDefectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 真机验收闭环管理（ITER-F01 / F-11.5）。
 * <p>
 * 负责验收用例的登记/结果记录、缺陷的登记(REQ-09 回链 PRD)/修复/闭环，以及验收闭环状态的
 * 纯函数计算。本服务不重复实现支付/出款/验签等已落地能力（REQ-01~REQ-08 由对应领域服务保证），
 * 仅提供「追踪 + 回链 + 闭环编排」。
 */
@Service
public class AcceptanceService {

    /** 默认回链 PRD 标识（与 docs/PRD-P0-F01-真机验收闭环.md 对应）。 */
    public static final String DEFAULT_PRD = "PRD-P0-F01-真机验收闭环";

    private final AcceptanceCaseMapper caseMapper;
    private final AcceptanceDefectMapper defectMapper;

    public AcceptanceService(AcceptanceCaseMapper caseMapper, AcceptanceDefectMapper defectMapper) {
        this.caseMapper = caseMapper;
        this.defectMapper = defectMapper;
    }

    /**
     * 从 PRD「第十节 验收」幂等构造 REQ-01~REQ-09 九个验收用例。
     * 已存在用例时直接返回，避免重复 seed。
     */
    public List<AcceptanceCase> seedFromPrd() {
        Long count = caseMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return listCases();
        }
        List<AcceptanceCase> seeds = List.of(
                newCase("REQ-01", "支付回调 v3 验签恒拒绝伪造", "SIGN"),
                newCase("REQ-02", "支付金额与订单不符拒绝入账", "PAY"),
                newCase("REQ-03", "重复回调仅落一条资金流水", "PAY"),
                newCase("REQ-04", "分账失败落库可重试", "PAY"),
                newCase("REQ-05", "无出款凭据硬网关不出款", "CONFIG"),
                newCase("REQ-06", "已支付触发双向通知", "PAY"),
                newCase("REQ-07", "IM 指数退避重连", "IM"),
                newCase("REQ-08", "--v3-sign 真实签名闭环", "SIGN"),
                newCase("REQ-09", "真机缺陷登记回链 PRD", "DEFECT")
        );
        seeds.forEach(caseMapper::insert);
        return seeds;
    }

    private AcceptanceCase newCase(String reqCode, String title, String category) {
        AcceptanceCase c = new AcceptanceCase();
        c.setReqCode(reqCode);
        c.setTitle(title);
        c.setCategory(category);
        c.setStatus("NOT_RUN");
        c.setPrdLink(DEFAULT_PRD);
        c.setExecutedAt(null);
        return c;
    }

    /** 列出全部验收用例（按 id 升序）。 */
    public List<AcceptanceCase> listCases() {
        return caseMapper.selectList(
                new LambdaQueryWrapper<AcceptanceCase>().orderByAsc(AcceptanceCase::getId));
    }

    /** 获取单个验收用例，不存在抛 {@link Code#ACCEPTANCE_NOT_FOUND}。 */
    public AcceptanceCase getCase(Long id) {
        AcceptanceCase c = caseMapper.selectById(id);
        if (c == null) {
            throw new BizException(Code.ACCEPTANCE_NOT_FOUND);
        }
        return c;
    }

    /**
     * 回填验收用例结果。result 须为 PASSED/FAILED/SKIPPED（DTO 已 @Pattern 校验，此处兜底）。
     */
    public AcceptanceCase recordResult(Long id, AcceptanceResultDTO dto) {
        AcceptanceCase c = getCase(id);
        String result = dto.getResult();
        if (!List.of("PASSED", "FAILED", "SKIPPED").contains(result)) {
            throw new BizException(Code.PARAM_INVALID, "验收结论须为 PASSED/FAILED/SKIPPED");
        }
        c.setStatus(result);
        c.setExecutor(dto.getExecutor());
        c.setResultNote(dto.getResultNote());
        c.setExecutedAt(dto.getExecutedAt() != null ? dto.getExecutedAt() : LocalDateTime.now());
        caseMapper.updateById(c);
        return c;
    }

    /** 列出全部缺陷（按 id 升序）。 */
    public List<AcceptanceDefect> listDefects() {
        return defectMapper.selectList(
                new LambdaQueryWrapper<AcceptanceDefect>().orderByAsc(AcceptanceDefect::getId));
    }

    /** 获取单个缺陷，不存在抛 {@link Code#ACCEPTANCE_NOT_FOUND}。 */
    public AcceptanceDefect getDefect(Long id) {
        AcceptanceDefect d = defectMapper.selectById(id);
        if (d == null) {
            throw new BizException(Code.ACCEPTANCE_NOT_FOUND);
        }
        return d;
    }

    /**
     * 登记真机验收缺陷（REQ-09 回链 PRD）。初始状态 OPEN；linkedPrd 缺省为 {@link #DEFAULT_PRD}。
     */
    public AcceptanceDefect registerDefect(AcceptanceDefectCreateDTO dto) {
        AcceptanceDefect d = new AcceptanceDefect();
        d.setCaseId(dto.getCaseId());
        d.setReqCode(dto.getReqCode());
        d.setTitle(dto.getTitle());
        d.setSeverity(dto.getSeverity());
        d.setStatus("OPEN");
        d.setLinkedPrd(dto.getLinkedPrd() != null && !dto.getLinkedPrd().isBlank()
                ? dto.getLinkedPrd() : DEFAULT_PRD);
        d.setDescription(dto.getDescription());
        defectMapper.insert(d);
        return d;
    }

    /** 修复缺陷：OPEN → FIXED（非法状态转移抛 {@link Code#STATE_NOT_ALLOWED}）。 */
    public AcceptanceDefect fixDefect(Long id) {
        AcceptanceDefect d = getDefect(id);
        if (!"OPEN".equals(d.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅 OPEN 状态缺陷可标记为已修复");
        }
        d.setStatus("FIXED");
        d.setFixedAt(LocalDateTime.now());
        defectMapper.updateById(d);
        return d;
    }

    /** 闭环缺陷：FIXED → CLOSED（非法状态转移抛 {@link Code#STATE_NOT_ALLOWED}）。 */
    public AcceptanceDefect closeDefect(Long id) {
        AcceptanceDefect d = getDefect(id);
        if (!"FIXED".equals(d.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅 FIXED 状态缺陷可闭环");
        }
        d.setStatus("CLOSED");
        d.setClosedAt(LocalDateTime.now());
        defectMapper.updateById(d);
        return d;
    }

    /** 计算当前验收闭环状态（纯函数，基于全部用例与缺陷）。 */
    public ClosureResult evaluateClosure() {
        List<AcceptanceCase> cases = listCases();
        List<AcceptanceDefect> defects = listDefects();
        return AcceptanceClosureCalculator.evaluate(cases, defects);
    }

    /**
     * 关单：重算闭环状态，仅当 {@code CLOSED} 方视为成功并回传快照；否则抛
     * {@link Code#ACCEPTANCE_NOT_CLOSED}（含当前状态说明）。
     */
    public ClosureResult closeAcceptance() {
        ClosureResult r = evaluateClosure();
        if (!r.closable()) {
            throw new BizException(Code.ACCEPTANCE_NOT_CLOSED,
                    "验收未闭环：" + r.status().getLabel()
                            + "（未通过用例 " + r.openCases() + "，未关闭缺陷 " + r.openDefects() + "）");
        }
        return r;
    }
}
