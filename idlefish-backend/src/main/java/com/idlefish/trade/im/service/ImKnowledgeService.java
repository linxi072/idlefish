package com.idlefish.trade.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.im.assistant.ImIntent;
import com.idlefish.trade.im.assistant.KnowledgeMatcher;
import com.idlefish.trade.im.entity.ImKnowledge;
import com.idlefish.trade.im.mapper.ImKnowledgeMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 知识库话术服务（ITER-F06 / REQ-11 品类话术优先）。
 * <p>运营后台维护条目（品类/意图/触发词/答案模板/优先级/上下架）；
 * 匹配侧将启用中的条目装配为纯函数 {@link KnowledgeMatcher.Candidate}，品类专属优先、通用回落。</p>
 */
@Service
public class ImKnowledgeService {

    private final ImKnowledgeMapper knowledgeMapper;

    public ImKnowledgeService(ImKnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    /** 运营后台列表（可按意图/状态过滤，按品类与优先级排序）。 */
    public List<ImKnowledge> adminList(String intent, String status) {
        return knowledgeMapper.selectList(new LambdaQueryWrapper<ImKnowledge>()
                .eq(intent != null && !intent.isBlank(), ImKnowledge::getIntent, intent)
                .eq(status != null && !status.isBlank(), ImKnowledge::getStatus, status)
                .orderByAsc(ImKnowledge::getCategoryId)
                .orderByDesc(ImKnowledge::getPriority));
    }

    /** 新增 / 更新（按 id 是否存在 upsert）。 */
    public Long save(ImKnowledge knowledge) {
        validate(knowledge);
        if (knowledge.getId() == null) {
            if (knowledge.getStatus() == null || knowledge.getStatus().isBlank()) {
                knowledge.setStatus(ImKnowledge.STATUS_ON);
            }
            if (knowledge.getPriority() == null) {
                knowledge.setPriority(0);
            }
            if (knowledge.getCategoryId() == null) {
                knowledge.setCategoryId(ImKnowledge.CATEGORY_GENERAL);
            }
            knowledgeMapper.insert(knowledge);
        } else {
            knowledgeMapper.updateById(knowledge);
        }
        return knowledge.getId();
    }

    /** 删除条目。 */
    public void delete(Long id) {
        if (knowledgeMapper.deleteById(id) == 0) {
            throw new BizException(Code.IM_KNOWLEDGE_NOT_FOUND);
        }
    }

    /** 上下架。 */
    public void updateStatus(Long id, String status) {
        ImKnowledge exist = knowledgeMapper.selectById(id);
        if (exist == null) {
            throw new BizException(Code.IM_KNOWLEDGE_NOT_FOUND);
        }
        if (!ImKnowledge.STATUS_ON.equals(status) && !ImKnowledge.STATUS_OFF.equals(status)) {
            throw new BizException(Code.PARAM_INVALID, "状态仅支持 ON/OFF");
        }
        ImKnowledge upd = new ImKnowledge();
        upd.setId(id);
        upd.setStatus(status);
        knowledgeMapper.updateById(upd);
    }

    /** 启用中的全部条目 → 匹配候选视图（按品类与优先级排序，供 {@link KnowledgeMatcher} 使用）。 */
    public List<KnowledgeMatcher.Candidate> enabledCandidates() {
        List<ImKnowledge> list = knowledgeMapper.selectList(new LambdaQueryWrapper<ImKnowledge>()
                .eq(ImKnowledge::getStatus, ImKnowledge.STATUS_ON)
                .orderByAsc(ImKnowledge::getCategoryId)
                .orderByDesc(ImKnowledge::getPriority));
        return list.stream().map(ImKnowledgeService::toCandidate).collect(Collectors.toList());
    }

    /** 按 ID 取条目（运营核对）。 */
    public ImKnowledge get(Long id) {
        ImKnowledge k = knowledgeMapper.selectById(id);
        if (k == null) {
            throw new BizException(Code.IM_KNOWLEDGE_NOT_FOUND);
        }
        return k;
    }

    private static KnowledgeMatcher.Candidate toCandidate(ImKnowledge k) {
        List<String> keywords = parseKeywords(k.getKeywords());
        return new KnowledgeMatcher.Candidate(k.getId(), k.getCategoryId(), parseIntent(k.getIntent()),
                keywords, k.getAnswerTemplate(), k.getPriority() == null ? 0 : k.getPriority());
    }

    private static ImIntent parseIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return ImIntent.UNKNOWN;
        }
        try {
            return ImIntent.valueOf(intent.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ImIntent.UNKNOWN;
        }
    }

    private static List<String> parseKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static void validate(ImKnowledge k) {
        if (k == null) {
            throw new BizException(Code.PARAM_MISSING, "知识库条目不能为空");
        }
        if (k.getIntent() == null || k.getIntent().isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "意图不能为空");
        }
        if (parseIntent(k.getIntent()) == ImIntent.UNKNOWN || parseIntent(k.getIntent()) == ImIntent.RULING) {
            // REQ-04：裁决类不配置话术（命中即转人工）
            throw new BizException(Code.PARAM_INVALID, "意图不支持或为裁决类（裁决类一律转人工，不配置话术）");
        }
        if (k.getKeywords() == null || k.getKeywords().isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "触发词不能为空");
        }
        if (k.getAnswerTemplate() == null || k.getAnswerTemplate().isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "答案模板不能为空");
        }
    }
}
