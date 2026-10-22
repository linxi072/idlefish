package com.idlefish.trade.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ITER-T03 契约扫描共享加载器（离线：纯文件 + Jackson，无 Spring 上下文、无 MySQL）。
 * <p>
 * 职责（对应 PRD-P1-T03）：
 * <ul>
 *   <li>REQ-01 自动扫描 {@code classpath:contracts/*.json}，新增契约文件无需手动登记；</li>
 *   <li>REQ-02 覆盖率自校验：目录内每个 JSON 文件要么被校验、要么以<b>显式已知原因</b>排除，
 *       且递归发现子目录中的 JSON（漏网即失败）；</li>
 *   <li>REQ-08 契约 JSON 非法时失败并定位到文件与行列号；</li>
 *   <li>结构校验（method/path/auth/expectStatus/expectFields/deprecated）与全局端点唯一性；</li>
 *   <li>提供端点快照（{@link EndpointSpec}）供一致性断言与兼容性比对共用。</li>
 * </ul>
 * 排除先例（与 F-15.4 一致）：{@code admin.json} 敏感审批文件、{@code observability.json}
 * 基础设施探针端点（跨 profile 双实现）不做严格漂移断言；点号开头文件为基线/豁免等元数据。
 */
final class ContractLoader {

    private ContractLoader() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** classpath 契约目录名。 */
    static final String RESOURCE_DIR = "contracts";
    /** 契约基线快照（点号开头，不被契约扫描纳入；由 ContractBaselineRegenerateTest 生成）。 */
    static final String BASELINE_FILE = ".contract-baseline.json";
    /** 豁免配置（点号开头；REQ-10：原因 + 过期时间 + 责任人）。 */
    static final String EXEMPTIONS_FILE = ".contract-exemptions.json";

    static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
    /**
     * 允许的鉴权口径（ITER-T04 新增 service）：none=免登录、user=用户令牌、admin=后台令牌、
     * service=服务间令牌（搜推独立服务面 /sr/api/**，X-Service-Token，REQ-09）。
     */
    static final Set<String> ALLOWED_AUTH = Set.of("none", "user", "admin", "service");

    /** 排除原因（仅这三类 + 隐藏文件，其它跳过一律视为覆盖漏洞，REQ-02）。 */
    static final String SKIP_ADMIN = "admin.json（敏感审批文件，按既有先例排除）";
    static final String SKIP_OBSERVABILITY = "observability.json（基础设施探针端点，跨 profile 双实现，按先例排除）";
    static final String SKIP_HIDDEN = "隐藏点号文件（基线/豁免等元数据，非业务契约）";

    /**
     * 单个契约端点的结构化快照。{@code expectFields} 为消费者驱动契约的期望字段清单，
     * 元素支持可选类型注解（如 {@code "amount:integer"}），用于类型变更判定（REQ-04）。
     */
    record EndpointSpec(String method, String path, String auth, Integer expectStatus,
                        List<String> expectFields, boolean deprecated, String file) {
        public String key() {
            return method + " " + path;
        }

        /** 排序后的字段集合（基线比对用，避免字段重排造成伪差异）。 */
        public Set<String> sortedFields() {
            return new java.util.TreeSet<>(expectFields);
        }
    }

    /** 目录内一个 JSON 文件的扫描结果；{@code validated=false} 时 skipReason 必非空。 */
    record ScannedFile(String name, boolean validated, String skipReason, List<EndpointSpec> endpoints) {
    }

    /** 全目录扫描结果。 */
    record ScanResult(List<ScannedFile> files, int totalJsonFiles, boolean directoryFound,
                      List<String> nestedJsonFiles) {

        public List<EndpointSpec> validatedEndpoints() {
            List<EndpointSpec> out = new ArrayList<>();
            for (ScannedFile f : files) {
                if (f.validated()) {
                    out.addAll(f.endpoints());
                }
            }
            return out;
        }

        public int validatedFileCount() {
            return (int) files.stream().filter(ScannedFile::validated).count();
        }

        public int skippedFileCount() {
            return (int) files.stream().filter(f -> !f.validated()).count();
        }

        /** 排除原因 -> 文件数，用于报告。 */
        public Map<String, Long> skipReasonCounts() {
            Map<String, Long> m = new TreeMap<>();
            for (ScannedFile f : files) {
                if (!f.validated()) {
                    m.merge(f.skipReason(), 1L, Long::sum);
                }
            }
            return m;
        }
    }

    // ===== 扫描 =====

    /**
     * 扫描 classpath 下全部 contracts 根（Maven 测试态为展开目录，file 协议）。
     *
     * @throws Exception 目录缺失 / JSON 非法 / 结构非法 / 覆盖漏洞（均以 JUnit 断言失败呈现，REQ-02/08）
     */
    static ScanResult scan() throws Exception {
        ClassLoader cl = ContractLoader.class.getClassLoader();
        Enumeration<URL> roots = cl.getResources(RESOURCE_DIR);

        List<ScannedFile> files = new ArrayList<>();
        Set<String> loaded = new LinkedHashSet<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        int total = 0;
        boolean found = false;
        List<String> nested = new ArrayList<>();

        while (roots.hasMoreElements()) {
            URL url = roots.nextElement();
            if (!"file".equals(url.getProtocol())) {
                continue; // 仅支持展开目录（Maven 测试态）
            }
            found = true;
            Path dir = Paths.get(url.toURI());

            // REQ-02：递归发现子目录 JSON，防「新增文件放在子目录而未被顶层 glob 覆盖」
            try (var walk = Files.walk(dir, 3)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .filter(p -> p.getParent() != null && !p.getParent().equals(dir))
                        .forEach(p -> nested.add(dir.relativize(p).toString()));
            }
            if (!nested.isEmpty()) {
                fail("契约子目录中存在未被扫描的 JSON（REQ-02 覆盖率自校验失败）: " + nested
                        + "\n  请将其移至 contracts 根目录，或显式登记排除原因。");
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    total++;
                    String reason = skipReason(name);
                    if (reason != null) {
                        files.add(new ScannedFile(name, false, reason, List.of()));
                        continue;
                    }
                    if (!loaded.add(name)) {
                        continue; // 多 classpath 根同名去重（保留首个）
                    }
                    JsonNode root = readContract(name, p);
                    List<EndpointSpec> specs = parseEndpoints(name, root);
                    for (EndpointSpec spec : specs) {
                        if (!seenKeys.add(spec.key())) {
                            fail("契约内重复端点（跨文件全局唯一）: " + spec.key() + " @ " + name);
                        }
                    }
                    files.add(new ScannedFile(name, true, null, specs));
                }
            }
        }
        return new ScanResult(files, total, found, nested);
    }

    /** 显式排除原因；返回 null 表示应纳入校验。 */
    static String skipReason(String fileName) {
        if ("admin.json".equals(fileName)) {
            return SKIP_ADMIN;
        }
        if ("observability.json".equals(fileName)) {
            return SKIP_OBSERVABILITY;
        }
        if (fileName.startsWith(".")) {
            return SKIP_HIDDEN;
        }
        return null;
    }

    private static JsonNode readContract(String name, Path p) {
        try (InputStream in = Files.newInputStream(p)) {
            return MAPPER.readTree(in);
        } catch (JsonProcessingException e) {
            // REQ-08：定位到文件与行列号
            fail("契约文件 JSON 解析失败（REQ-08）: " + name
                    + " @ 行 " + e.getLocation().getLineNr()
                    + " 列 " + e.getLocation().getColumnNr()
                    + " -> " + e.getOriginalMessage());
            return null;
        } catch (IOException e) {
            fail("契约文件读取失败: " + name + " -> " + e.getMessage());
            return null;
        }
    }

    private static List<EndpointSpec> parseEndpoints(String file, JsonNode root) {
        assertTrue(root != null && root.has("endpoints") && root.get("endpoints").isArray(),
                "契约文件须含 endpoints 数组: " + file);
        List<EndpointSpec> specs = new ArrayList<>();
        for (JsonNode e : root.get("endpoints")) {
            specs.add(parseEndpoint(file, e));
        }
        assertFalse(specs.isEmpty(), "契约文件 endpoints 为空: " + file);
        return specs;
    }

    private static EndpointSpec parseEndpoint(String file, JsonNode e) {
        String method = str(e, "method", true);
        String path = str(e, "path", true);
        String where = " @ " + file + " " + method + " " + path;
        assertTrue(ALLOWED_METHODS.contains(method), "非法 method: " + method + where);
        assertTrue(path.startsWith("/"), "path 必须以 / 开头: " + path + where);
        String auth = null;
        if (e.has("auth")) {
            auth = str(e, "auth", false);
            assertTrue(ALLOWED_AUTH.contains(auth), "非法 auth: " + auth + where);
        }
        Integer status = null;
        if (e.has("expectStatus")) {
            status = e.get("expectStatus").asInt();
            assertTrue(status >= 200 && status < 600, "expectStatus 越界: " + status + where);
        }
        List<String> fields = new ArrayList<>();
        if (e.has("expectFields")) {
            assertTrue(e.get("expectFields").isArray(), "expectFields 须为数组" + where);
            for (JsonNode f : e.get("expectFields")) {
                assertTrue(f.isTextual() && !f.asText().isBlank(), "expectFields 元素须为非空字符串" + where);
                fields.add(f.asText());
            }
        }
        boolean deprecated = e.path("deprecated").asBoolean(false);
        return new EndpointSpec(method, path, auth, status, List.copyOf(fields), deprecated, file);
    }

    private static String str(JsonNode e, String field, boolean required) {
        JsonNode n = e.get(field);
        assertTrue(n != null && n.isTextual() && !n.asText().isBlank(),
                "契约端点缺少非空字段: " + field);
        return n.asText();
    }

    // ===== 基线 / 豁免加载 =====

    /** 从 classpath 读取点号元数据文件；不存在返回 null。 */
    static JsonNode readMeta(String fileName) throws Exception {
        URL url = ContractLoader.class.getClassLoader().getResource(RESOURCE_DIR + "/" + fileName);
        if (url == null) {
            return null;
        }
        try (InputStream in = url.openStream()) {
            return MAPPER.readTree(in);
        }
    }

    /** 基线端点条目（与 {@link EndpointSpec} 对齐的持久化结构）。 */
    record BaselineEndpoint(String auth, Integer expectStatus, List<String> fields,
                            boolean deprecated, String file) {
    }

    /**
     * 基线快照：key = "METHOD PATH"。
     * 生成方式见 {@code ContractBaselineRegenerateTest}（-Dcontract.baseline.regenerate=true）。
     */
    record Baseline(String generatedAt, int endpointCount, Map<String, BaselineEndpoint> endpoints) {

        static final int VERSION = 1;

        static Baseline from(List<EndpointSpec> specs) {
            Map<String, BaselineEndpoint> map = new TreeMap<>();
            for (EndpointSpec s : specs) {
                map.put(s.key(), new BaselineEndpoint(
                        s.auth(), s.expectStatus(),
                        new ArrayList<>(s.sortedFields()), s.deprecated(), s.file()));
            }
            return new Baseline(java.time.LocalDate.now().toString(), specs.size(), map);
        }

        static Baseline load() throws Exception {
            JsonNode node = readMeta(BASELINE_FILE);
            if (node == null) {
                fail("未找到契约基线 " + BASELINE_FILE
                        + "：请先执行 mvn -o test -Dtest=ContractBaselineRegenerateTest -Dcontract.baseline.regenerate=true"
                        + " 生成基线并提交，再运行兼容性测试。");
                return null;
            }
            Map<String, BaselineEndpoint> map = new LinkedHashMap<>();
            JsonNode eps = node.get("endpoints");
            assertTrue(eps != null && eps.isObject(), "基线文件缺少 endpoints 对象: " + BASELINE_FILE);
            eps.fields().forEachRemaining(en -> {
                JsonNode v = en.getValue();
                List<String> fields = new ArrayList<>();
                JsonNode f = v.get("fields");
                if (f != null && f.isArray()) {
                    f.forEach(x -> fields.add(x.asText()));
                }
                map.put(en.getKey(), new BaselineEndpoint(
                        v.has("auth") && !v.get("auth").isNull() ? v.get("auth").asText() : null,
                        v.has("expectStatus") && !v.get("expectStatus").isNull() ? v.get("expectStatus").asInt() : null,
                        fields, v.path("deprecated").asBoolean(false),
                        v.has("file") ? v.get("file").asText() : null));
            });
            return new Baseline(node.has("generatedAt") ? node.get("generatedAt").asText() : "unknown",
                    map.size(), map);
        }
    }

    /** 豁免条目（REQ-10）：必须带 reason 与 expiresAt，过期即失败。 */
    record Exemption(String key, String field, String reason, String owner, String expiresAt) {
    }

    record Exemptions(List<Exemption> items) {

        static Exemptions load() throws Exception {
            JsonNode node = readMeta(EXEMPTIONS_FILE);
            if (node == null) {
                return new Exemptions(List.of());
            }
            JsonNode arr = node.get("exemptions");
            if (arr == null || !arr.isArray()) {
                return new Exemptions(List.of());
            }
            List<Exemption> items = new ArrayList<>();
            for (JsonNode n : arr) {
                items.add(new Exemption(
                        n.path("key").asText(null),
                        n.has("field") && !n.get("field").isNull() ? n.get("field").asText() : null,
                        n.path("reason").asText(""),
                        n.path("owner").asText(""),
                        n.path("expiresAt").asText(null)));
            }
            return new Exemptions(items);
        }
    }
}
