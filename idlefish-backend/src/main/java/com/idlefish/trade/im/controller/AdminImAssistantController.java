package com.idlefish.trade.im.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.im.entity.ImAutoReply;
import com.idlefish.trade.im.entity.ImContentBlock;
import com.idlefish.trade.im.entity.ImDisputeWarning;
import com.idlefish.trade.im.entity.ImKnowledge;
import com.idlefish.trade.im.entity.ImUnanswered;
import com.idlefish.trade.im.service.ImAssistantService;
import com.idlefish.trade.im.service.ImKnowledgeService;
import com.idlefish.trade.im.vo.ImAssistantStatsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;

/**
 * IM 智能助手运营后台（ITER-F06 / PRD §六）：知识库话术维护、未命中问题池、
 * 内容拦截记录、纠纷预警跟进、应答审计与质量统计。
 */
@RestController
@RequestMapping("/api/admin/im/assistant")
public class AdminImAssistantController {

    private final ImKnowledgeService knowledgeService;
    private final ImAssistantService assistantService;

    public AdminImAssistantController(ImKnowledgeService knowledgeService, ImAssistantService assistantService) {
        this.knowledgeService = knowledgeService;
        this.assistantService = assistantService;
    }

    /** 知识库列表（可按意图/状态过滤）。 */
    @GetMapping("/knowledge")
    public Result<List<ImKnowledge>> knowledge(@CurrentAdmin AdminUser admin,
                                               @RequestParam(required = false) String intent,
                                               @RequestParam(required = false) String status) {
        return Result.ok(knowledgeService.adminList(intent, status));
    }

    /** 新增 / 更新知识库条目。 */
    @PostMapping("/knowledge/save")
    public Result<Long> saveKnowledge(@CurrentAdmin AdminUser admin,
                                      @Valid @RequestBody ImKnowledge knowledge) {
        return Result.ok(knowledgeService.save(knowledge));
    }

    /** 删除知识库条目。 */
    @PostMapping("/knowledge/delete")
    public Result<Void> deleteKnowledge(@CurrentAdmin AdminUser admin,
                                        @RequestParam Long id) {
        knowledgeService.delete(id);
        return Result.ok(null);
    }

    /** 知识库上下架。 */
    @PostMapping("/knowledge/status")
    public Result<Void> knowledgeStatus(@CurrentAdmin AdminUser admin,
                                        @RequestParam Long id,
                                        @RequestParam String status) {
        knowledgeService.updateStatus(id, status);
        return Result.ok(null);
    }

    /** 未命中问题池（REQ-14：按热度降序，供补充话术）。 */
    @GetMapping("/unanswered")
    public Result<IPage<ImUnanswered>> unanswered(@CurrentAdmin AdminUser admin,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(assistantService.unansweredPage(status, page, size));
    }

    /** 标记未命中问题已补充话术。 */
    @PostMapping("/unanswered/resolve")
    public Result<Void> resolveUnanswered(@CurrentAdmin AdminUser admin,
                                          @RequestParam Long id) {
        assistantService.resolveUnanswered(id);
        return Result.ok(null);
    }

    /** 内容拦截记录（REQ-08：引流/站外联系方式留痕）。 */
    @GetMapping("/blocks")
    public Result<IPage<ImContentBlock>> blocks(@CurrentAdmin AdminUser admin,
                                                @RequestParam(required = false) String convId,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(assistantService.blocksPage(convId, page, size));
    }

    /** 纠纷预警列表（REQ-06）。 */
    @GetMapping("/warnings")
    public Result<IPage<ImDisputeWarning>> warnings(@CurrentAdmin AdminUser admin,
                                                    @RequestParam(required = false) Boolean unhandledOnly,
                                                    @RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(assistantService.warningsPage(
                unhandledOnly == null || unhandledOnly, page, size));
    }

    /** 标记预警已跟进。 */
    @PostMapping("/warnings/handle")
    public Result<Void> handleWarning(@CurrentAdmin AdminUser admin,
                                      @RequestParam Long id) {
        assistantService.handleWarning(id, admin.getId());
        return Result.ok(null);
    }

    /** 自动应答记录（REQ-10 来源审计，可按来源/意图过滤）。 */
    @GetMapping("/replies")
    public Result<IPage<ImAutoReply>> replies(@CurrentAdmin AdminUser admin,
                                              @RequestParam(required = false) String source,
                                              @RequestParam(required = false) String intent,
                                              @RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(assistantService.repliesPage(source, intent, page, size));
    }

    /** 质量统计（自动解决率 / 转人工率 / 降级率 / 满意度 / 预警与拦截量）。 */
    @GetMapping("/stats")
    public Result<ImAssistantStatsVO> stats(@CurrentAdmin AdminUser admin) {
        return Result.ok(assistantService.stats());
    }
}
