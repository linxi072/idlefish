package com.idlefish.trade.search.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.vo.ItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 检索实现（真实 Elasticsearch 实现，默认启用）。
 * 零 SDK 依赖，直接调用 ES REST：
 * - 启动时建索引（PUT /{index}）；
 * - 发布/上架时写入文档（PUT /{index}/_doc/{id}）；
 * - 检索/联想/兜底走 _search（multi_match + term + range + sort）。
 * 关键生产属性：ES 不可用时优雅降级（记录日志、返回空），不阻断主流程。
 */
@Service
public class EsSearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(EsSearchServiceImpl.class);

    private final IdlefishProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EsSearchServiceImpl(IdlefishProperties props, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private String index() {
        return props.getSearch().getIndex();
    }

    private String baseUrl() {
        List<String> hosts = props.getSearch().getHosts();
        String host = (hosts == null || hosts.isEmpty()) ? "http://127.0.0.1:9200" : hosts.get(0);
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    @PostConstruct
    public void ensureIndex() {
        try {
            String url = baseUrl() + "/" + index();
            ObjectNode mapping = objectMapper.createObjectNode();
            ObjectNode propsNode = mapping.putObject("mappings").putObject("properties");
            propsNode.putObject("title").put("type", "text").put("analyzer", "ik_max_word").put("search_analyzer", "ik_smart");
            propsNode.putObject("description").put("type", "text").put("analyzer", "ik_max_word");
            propsNode.putObject("categoryId").put("type", "long");
            propsNode.putObject("sellerId").put("type", "long");
            propsNode.putObject("price").put("type", "long");
            propsNode.putObject("conditionLevel").put("type", "integer");
            propsNode.putObject("status").put("type", "keyword");
            propsNode.putObject("auditStatus").put("type", "keyword");
            propsNode.putObject("city").put("type", "keyword");
            propsNode.putObject("createdAt").put("type", "date");
            restTemplate.put(url, new HttpEntity<>(mapping.toString(), jsonHeaders()), String.class);
        } catch (Exception e) {
            log.warn("ES 建索引失败（可能已存在或 ES 不可用），忽略: {}", e.getMessage());
        }
    }

    @Override
    public IPage<ItemVO> search(ItemQueryDTO q) {
        if (q == null) {
            q = new ItemQueryDTO();
        }
        if (q.getKeyword() == null || q.getKeyword().isBlank()) {
            return fallback(q);
        }
        try {
            ObjectNode bool = buildBool(q);

            ObjectNode sortNode = objectMapper.createObjectNode();
            switch (q.getSort() == null ? "" : q.getSort()) {
                case "price_asc": sortNode.put("price", "asc"); break;
                case "price_desc": sortNode.put("price", "desc"); break;
                case "hot": sortNode.put("viewCount", "desc"); break;
                default: sortNode.put("createdAt", "desc");
            }
            ArrayNode sort = objectMapper.createArrayNode().add(sortNode);

            ObjectNode body = objectMapper.createObjectNode();
            body.set("query", objectMapper.createObjectNode().set("bool", bool));
            body.set("sort", sort);
            body.put("from", (Math.max(q.getPage(), 1) - 1) * Math.max(q.getSize(), 1));
            body.put("size", Math.max(q.getSize(), 1));

            JsonNode root = postSearch(body);
            return parseHits(root, q);
        } catch (Exception e) {
            log.warn("ES 检索失败，降级为空结果: {}", e.getMessage());
            return new Page<>(Math.max(q.getPage(), 1), Math.max(q.getSize(), 1));
        }
    }

    /**
     * 构建检索/聚合共用的 bool 查询（multi_match + 过滤项，F-14.1 新增同城/省份过滤）。
     * 纯构造、无副作用，离线可单测（见 EsSearchQueryTest）。
     */
    ObjectNode buildBool(ItemQueryDTO q) {
        ObjectNode bool = objectMapper.createObjectNode().putObject("bool");
        ArrayNode must = bool.putArray("must");
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            ObjectNode multi = must.addObject().putObject("multi_match");
            multi.put("query", q.getKeyword());
            ArrayNode fields = multi.putArray("fields");
            fields.add("title").add("description");
        }
        ArrayNode filter = bool.putArray("filter");
        term(filter, "status", "on_sale");
        term(filter, "auditStatus", "pass");
        if (q.getCategoryId() != null) {
            term(filter, "categoryId", q.getCategoryId());
        }
        if (q.getConditionLevel() != null) {
            term(filter, "conditionLevel", q.getConditionLevel());
        }
        if (q.getCity() != null && !q.getCity().isBlank()) {
            term(filter, "city", q.getCity());
        }
        if (q.getProvince() != null && !q.getProvince().isBlank()) {
            term(filter, "province", q.getProvince());
        }
        if (q.getMinPrice() != null || q.getMaxPrice() != null) {
            ObjectNode range = filter.addObject().putObject("range").putObject("price");
            if (q.getMinPrice() != null) range.put("gte", q.getMinPrice());
            if (q.getMaxPrice() != null) range.put("lte", q.getMaxPrice());
        }
        return bool;
    }

    private void term(ArrayNode filter, String field, Object value) {
        ObjectNode t = filter.addObject().putObject("term");
        if (value instanceof Number) {
            t.putObject(field).put("value", ((Number) value).longValue());
        } else {
            t.putObject(field).put("value", value.toString());
        }
    }

    @Override
    public Map<String, Map<String, Long>> facets(ItemQueryDTO q) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        if (q == null) {
            q = new ItemQueryDTO();
        }
        try {
            ObjectNode bool = buildBool(q);
            ObjectNode body = objectMapper.createObjectNode();
            body.set("query", objectMapper.createObjectNode().set("bool", bool));
            // 聚合维度：类目 / 成色 / 城市
            ObjectNode aggs = body.putObject("aggs");
            for (String dim : new String[]{"categoryId", "conditionLevel", "city"}) {
                ObjectNode terms = aggs.putObject(dim).putObject("terms");
                terms.put("field", dim);
                terms.put("size", 100);
            }
            body.put("size", 0); // 聚合查询不需要返回文档

            JsonNode root = postSearch(body);
            JsonNode aggsNode = root.path("aggregations");
            for (String dim : new String[]{"categoryId", "conditionLevel", "city"}) {
                Map<String, Long> entries = new LinkedHashMap<>();
                JsonNode buckets = aggsNode.path(dim).path("buckets");
                if (buckets.isArray()) {
                    for (JsonNode b : buckets) {
                        String key = b.path("key").asText();
                        long count = b.path("doc_count").asLong(0L);
                        entries.put(key, count);
                    }
                }
                result.put(dim, entries);
            }
            return result;
        } catch (Exception e) {
            log.warn("ES 聚合失败，降级为空: {}", e.getMessage());
            result.put("categoryId", new LinkedHashMap<>());
            result.put("conditionLevel", new LinkedHashMap<>());
            result.put("city", new LinkedHashMap<>());
            return result;
        }
    }

    @Override
    public List<String> suggest(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode match = body.putObject("query").putObject("match_phrase");
            match.put("title", keyword);
            body.put("size", 10);
            body.put("_source", objectMapper.createArrayNode().add("title"));
            JsonNode root = postSearch(body);
            List<String> res = new ArrayList<>();
            JsonNode hits = root.path("hits").path("hits");
            if (hits.isArray()) {
                for (JsonNode h : hits) {
                    String title = h.path("_source").path("title").asText(null);
                    if (title != null) res.add(title);
                }
            }
            return res;
        } catch (Exception e) {
            log.warn("ES 联想失败，降级为空: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public IPage<ItemVO> fallback(ItemQueryDTO q) {
        if (q == null) {
            q = new ItemQueryDTO();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode bool = body.putObject("query").putObject("bool");
            ArrayNode filter = bool.putArray("filter");
            ObjectNode s = filter.addObject().putObject("term");
            s.putObject("status").put("value", "on_sale");
            ObjectNode a = filter.addObject().putObject("term");
            a.putObject("auditStatus").put("value", "pass");
            ObjectNode sortNode = objectMapper.createObjectNode().put("createdAt", "desc");
            body.set("sort", objectMapper.createArrayNode().add(sortNode));
            body.put("from", (Math.max(q.getPage(), 1) - 1) * Math.max(q.getSize(), 1));
            body.put("size", Math.max(q.getSize(), 1));
            JsonNode root = postSearch(body);
            return parseHits(root, q);
        } catch (Exception e) {
            log.warn("ES 兜底检索失败，降级为空: {}", e.getMessage());
            return new Page<>(Math.max(q.getPage(), 1), Math.max(q.getSize(), 1));
        }
    }

    @Override
    public void indexItem(Item item) {
        try {
            ObjectNode doc = objectMapper.createObjectNode();
            doc.put("id", item.getId());
            doc.put("sellerId", item.getSellerId());
            doc.put("categoryId", item.getCategoryId());
            doc.put("title", item.getTitle());
            doc.put("description", item.getDescription());
            doc.put("price", item.getPrice());
            doc.put("originalPrice", item.getOriginalPrice());
            doc.put("images", item.getImages());
            doc.put("conditionLevel", item.getConditionLevel());
            doc.put("status", item.getStatus());
            doc.put("auditStatus", item.getAuditStatus());
            doc.put("city", item.getCity());
            doc.put("province", item.getProvince());
            doc.put("createdAt", item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
            String url = baseUrl() + "/" + index() + "/_doc/" + item.getId();
            restTemplate.put(url, new HttpEntity<>(doc.toString(), jsonHeaders()), String.class);
        } catch (Exception e) {
            log.warn("ES 索引写入失败 itemId={}: {}", item.getId(), e.getMessage());
        }
    }

    @Override
    public void removeItem(Long itemId) {
        try {
            String url = baseUrl() + "/" + index() + "/_doc/" + itemId;
            restTemplate.delete(url);
        } catch (Exception e) {
            log.warn("ES 索引删除失败 itemId={}: {}", itemId, e.getMessage());
        }
    }

    private JsonNode postSearch(ObjectNode body) {
        String url = baseUrl() + "/" + index() + "/_search";
        String resp = restTemplate.postForObject(url, new HttpEntity<>(body.toString(), jsonHeaders()), String.class);
        try {
            return objectMapper.readTree(resp);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private IPage<ItemVO> parseHits(JsonNode root, ItemQueryDTO q) {
        long total = root.path("hits").path("total").path("value").asLong(0L);
        List<ItemVO> list = new ArrayList<>();
        JsonNode hits = root.path("hits").path("hits");
        if (hits.isArray()) {
            for (JsonNode h : hits) {
                JsonNode src = h.path("_source");
                ItemVO vo = new ItemVO();
                vo.setId(src.path("id").asLong());
                vo.setSellerId(src.path("sellerId").asLong());
                vo.setCategoryId(src.path("categoryId").asLong());
                vo.setTitle(src.path("title").asText());
                vo.setCover(firstImage(src.path("images").asText()));
                vo.setPrice(src.path("price").asLong());
                vo.setOriginalPrice(src.path("originalPrice").asLong());
                vo.setConditionLevel(src.path("conditionLevel").asInt());
                vo.setStatus(src.path("status").asText());
                vo.setAuditStatus(src.path("auditStatus").asText());
                vo.setCity(src.path("city").asText());
                list.add(vo);
            }
        }
        Page<ItemVO> page = new Page<>(Math.max(q.getPage(), 1), Math.max(q.getSize(), 1));
        page.setRecords(list);
        page.setTotal(total);
        return page;
    }

    private String firstImage(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return null;
        try {
            JsonNode arr = objectMapper.readTree(imagesJson);
            if (arr.isArray() && arr.size() > 0) return arr.get(0).asText();
        } catch (Exception ignored) {
        }
        return null;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
