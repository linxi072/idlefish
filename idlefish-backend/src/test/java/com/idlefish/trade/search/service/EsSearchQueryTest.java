package com.idlefish.trade.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ES 检索查询构造单测（F-14.1，离线可跑，无需 ES 实例）：验证 buildBool 过滤项构造正确。
 */
class EsSearchQueryTest {

    private final EsSearchServiceImpl svc = new EsSearchServiceImpl(new IdlefishProperties(), null, new ObjectMapper());

    @Test
    @DisplayName("buildBool：关键词 multi_match + 状态/类目/成色/同城/省份/价格区间过滤齐全")
    void buildBoolWithAllFilters() {
        ItemQueryDTO q = new ItemQueryDTO();
        q.setKeyword("手机");
        q.setCategoryId(5L);
        q.setConditionLevel(2);
        q.setCity("杭州");
        q.setProvince("浙江");
        q.setMinPrice(1000L);
        q.setMaxPrice(5000L);

        ObjectNode bool = svc.buildBool(q);
        // must[0].multi_match.query == 手机
        ArrayNode must = bool.path("must").isMissingNode() ? new ObjectMapper().createArrayNode() : (ArrayNode) bool.path("must");
        assertEquals("手机", must.get(0).path("multi_match").path("query").asText());

        Map<String, String> terms = extractTerms(bool.path("filter"));
        assertEquals("on_sale", terms.get("status"));
        assertEquals("pass", terms.get("auditStatus"));
        assertEquals("5", terms.get("categoryId"));
        assertEquals("2", terms.get("conditionLevel"));
        assertEquals("杭州", terms.get("city"));
        assertEquals("浙江", terms.get("province"));

        JsonNode range = findRange(bool.path("filter")).path("price");
        assertEquals(1000L, range.path("gte").asLong());
        assertEquals(5000L, range.path("lte").asLong());
    }

    @Test
    @DisplayName("buildBool：无关键词时 must 为空，仅保留状态过滤")
    void buildBoolNoKeyword() {
        ItemQueryDTO q = new ItemQueryDTO();
        q.setCategoryId(7L);
        ObjectNode bool = svc.buildBool(q);
        assertTrue(bool.path("must").isMissingNode() || bool.path("must").size() == 0);
        Map<String, String> terms = extractTerms(bool.path("filter"));
        assertEquals("on_sale", terms.get("status"));
        assertEquals("7", terms.get("categoryId"));
    }

    @Test
    @DisplayName("buildBool：仅价格上限时只生成 lte")
    void buildBoolMaxPriceOnly() {
        ItemQueryDTO q = new ItemQueryDTO();
        q.setMaxPrice(999L);
        ObjectNode bool = svc.buildBool(q);
        JsonNode range = findRange(bool.path("filter")).path("price");
        assertTrue(range.path("gte").isMissingNode());
        assertEquals(999L, range.path("lte").asLong());
    }

    /** 从 filter 数组中提取 term 过滤项（field → value）。 */
    private Map<String, String> extractTerms(JsonNode filter) {
        Map<String, String> m = new HashMap<>();
        if (filter.isArray()) {
            for (JsonNode node : filter) {
                JsonNode term = node.path("term");
                if (!term.isMissingNode()) {
                    term.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().path("value").asText()));
                }
            }
        }
        return m;
    }

    /** 从 filter 数组中找到 range 节点（含 price.gte/lte）。 */
    private JsonNode findRange(JsonNode filter) {
        if (filter.isArray()) {
            for (JsonNode node : filter) {
                if (!node.path("range").isMissingNode()) {
                    return node.path("range");
                }
            }
        }
        return new ObjectMapper().createObjectNode();
    }
}
