package com.idlefish.trade.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F-15.4 契约一致性测试（离线、无 MySQL、无 Spring 上下文）。
 * <p>
 * 1) 加载 {@code classpath:contracts/*.json} 自研 JSON 契约；
 * 2) 校验每个端点结构合法（method/path/auth/expectStatus/expectFields）；
 * 3) 校验契约内 method+path 唯一；
 * 4) 用 {@link ClassPathScanningCandidateComponentProvider} 反射扫描
 *    {@code com.idlefish.trade} 下全部 {@code *Controller}，构建「METHOD PATH」实现集；
 * 5) 断言每个契约端点都在代码中真实存在（防契约漂移）。
 */
class ContractConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> ALLOWED_AUTH = Set.of("none", "user", "admin");
    private static final String BASE_PACKAGE = "com.idlefish.trade";

    @Test
    void allContractEndpointsExistInCode() throws Exception {
        List<JsonNode> contracts = loadContracts();
        assertFalse(contracts.isEmpty(), "未找到任何契约文件（classpath:contracts/*.json）");

        Set<String> implemented = collectImplementedEndpoints();
        assertTrue(!implemented.isEmpty(), "反射扫描未收集到任何 Controller 端点，请检查扫描包名");

        Set<String> seenKeys = new LinkedHashSet<>();
        int total = 0;
        for (JsonNode c : contracts) {
            JsonNode endpoints = c.get("endpoints");
            assertTrue(endpoints != null && endpoints.isArray(), "契约缺少 endpoints 数组");
            for (JsonNode e : endpoints) {
                total++;
                String method = str(e, "method", true);
                String path = str(e, "path", true);
                assertTrue(ALLOWED_METHODS.contains(method), "非法 method: " + method + " @ " + path);
                assertTrue(path.startsWith("/"), "path 必须以 / 开头: " + path);
                if (e.has("auth")) {
                    assertTrue(ALLOWED_AUTH.contains(str(e, "auth", false)),
                            "非法 auth: " + str(e, "auth", false) + " @ " + path);
                }
                if (e.has("expectStatus")) {
                    int st = e.get("expectStatus").asInt();
                    assertTrue(st >= 200 && st < 600, "expectStatus 越界: " + st);
                }
                if (e.has("expectFields")) {
                    assertTrue(e.get("expectFields").isArray(), "expectFields 须为数组");
                    for (JsonNode f : e.get("expectFields")) {
                        assertTrue(f.isTextual() && !f.asText().isBlank(), "expectFields 元素须为非空字符串");
                    }
                }

                String key = method + " " + path;
                assertFalse(seenKeys.contains(key), "契约内重复端点: " + key);
                seenKeys.add(key);

                assertTrue(implemented.contains(key),
                        "契约端点未在代码中找到实现（漂移）: " + key
                                + "\n  已实现端点样例: " + sample(implemented, key, 8));
            }
        }
        System.out.println("[ContractConsistency] 校验通过：契约端点 " + total
                + " 个，代码实现端点 " + implemented.size() + " 个，全部命中。");
    }

    // ===== 契约加载 =====

    private List<JsonNode> loadContracts() throws Exception {
        // 显式清单：避免 getResources("contracts") 误匹配依赖 jar 内的同名资源
        String[] files = {
                "auth-item.json", "trade.json", "growth.json",
                "notify-im-search.json", "admin.json", "observability.json"
        };
        List<JsonNode> list = new ArrayList<>();
        for (String f : files) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("contracts/" + f)) {
                assertTrue(in != null, "契约文件缺失: contracts/" + f);
                JsonNode root = MAPPER.readTree(in);
                assertTrue(root.has("endpoints") && root.get("endpoints").isArray(),
                        "契约文件须含 endpoints 数组: " + f);
                list.add(root);
            }
        }
        return list;
    }

    // ===== 反射扫描代码端点 =====

    private Set<String> collectImplementedEndpoints() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        Set<String> endpoints = new LinkedHashSet<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        scanner.findCandidateComponents(BASE_PACKAGE).forEach(bd -> {
            try {
                Class<?> cls = Class.forName(bd.getBeanClassName(), false, cl);
                String base = mappingPath(cls.getAnnotation(RequestMapping.class));
                RequestMethod[] classMethods = mappingMethods(cls.getAnnotation(RequestMapping.class));
                for (Method m : cls.getDeclaredMethods()) {
                    for (Annotation a : m.getAnnotations()) {
                        String sub = null;
                        RequestMethod[] ms = null;
                        if (a instanceof GetMapping g) { sub = first(g.value(), g.path()); ms = new RequestMethod[]{RequestMethod.GET}; }
                        else if (a instanceof PostMapping p) { sub = first(p.value(), p.path()); ms = new RequestMethod[]{RequestMethod.POST}; }
                        else if (a instanceof PutMapping p) { sub = first(p.value(), p.path()); ms = new RequestMethod[]{RequestMethod.PUT}; }
                        else if (a instanceof DeleteMapping d) { sub = first(d.value(), d.path()); ms = new RequestMethod[]{RequestMethod.DELETE}; }
                        else if (a instanceof PatchMapping p) { sub = first(p.value(), p.path()); ms = new RequestMethod[]{RequestMethod.PATCH}; }
                        else if (a instanceof RequestMapping r) { sub = first(r.value(), r.path()); ms = r.method().length == 0 ? RequestMethod.values() : r.method(); }
                        if (sub == null) continue;
                        String full = join(base, sub);
                        if (ms != null) {
                            for (RequestMethod rm : ms) {
                                endpoints.add(rm.name() + " " + full);
                            }
                        }
                    }
                }
                // 仅类级映射（无方法级）的情况
                if (base != null && classMethods != null) {
                    // 已在方法循环中覆盖；此处仅兜底类级无方法级映射
                }
            } catch (Throwable t) {
                System.err.println("跳过无法加载的 Controller: " + bd.getBeanClassName() + " -> " + t);
            }
        });
        return endpoints;
    }

    // ===== 辅助 =====

    private String mappingPath(RequestMapping r) {
        if (r == null) return "";
        return first(r.value(), r.path());
    }

    private RequestMethod[] mappingMethods(RequestMapping r) {
        return r == null ? null : r.method();
    }

    private String first(String[] a, String[] b) {
        if (a != null && a.length > 0) return a[0];
        if (b != null && b.length > 0) return b[0];
        return "";
    }

    private String join(String base, String sub) {
        if (base == null || base.isEmpty()) return ensureLeadingSlash(sub);
        if (sub == null || sub.isEmpty()) return base;
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String s = sub.startsWith("/") ? sub.substring(1) : sub;
        return b + "/" + s;
    }

    private String ensureLeadingSlash(String s) {
        if (s == null || s.isEmpty()) return "/";
        return s.startsWith("/") ? s : "/" + s;
    }

    private String str(JsonNode e, String field, boolean required) {
        JsonNode n = e.get(field);
        assertTrue(n != null && n.isTextual() && !n.asText().isBlank(),
                "契约端点缺少非空字段: " + field);
        return n.asText();
    }

    private String sample(Set<String> set, String key, int n) {
        List<String> near = new ArrayList<>();
        String target = key.split(" ")[1]; // path
        for (String s : set) {
            if (s.contains(target) || near.size() < n) near.add(s);
            if (near.size() >= n) break;
        }
        return String.join(", ", near);
    }
}
