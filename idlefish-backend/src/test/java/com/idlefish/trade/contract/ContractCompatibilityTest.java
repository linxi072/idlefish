package com.idlefish.trade.contract;

import com.idlefish.trade.contract.ContractLoader.Baseline;
import com.idlefish.trade.contract.ContractLoader.BaselineEndpoint;
import com.idlefish.trade.contract.ContractLoader.EndpointSpec;
import com.idlefish.trade.contract.ContractLoader.Exemption;
import com.idlefish.trade.contract.ContractLoader.Exemptions;
import com.idlefish.trade.contract.ContractLoader.ScanResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ITER-T03 契约兼容性判定（离线，纯文件 + Jackson）。
 * <p>
 * 消费者驱动契约的轻量实现：以提交入库的 {@code .contract-baseline.json} 为「上次达成共识的契约」，
 * 每次运行与当前契约逐端点比对：
 * <ul>
 *   <li><b>不兼容（阻断，REQ-04）</b>：端点删除、字段删除、字段类型注解变更、auth 变更、expectStatus 变更；</li>
 *   <li><b>兼容（放行并列入报告，REQ-05）</b>：新增端点、新增可选字段、端点标记 deprecated；</li>
 *   <li><b>豁免（REQ-10）</b>：{@code .contract-exemptions.json} 显式登记（key + 可选 field + reason +
 *       owner + expiresAt），过期即失败，报告末尾可见；</li>
 *   <li><b>迁移指引（REQ-09）</b>：不兼容项输出受影响的前端调用位置清单（扫描 pc-admin / miniprogram 源码）。</li>
 * </ul>
 * 基线重新生成：{@code mvn -o test -Dtest=ContractBaselineRegenerateTest -Dcontract.baseline.regenerate=true}。
 */
class ContractCompatibilityTest {

    /** 消费者源码根（可用 -Dcontract.consumer.roots=a,b 覆盖）。 */
    private static final List<String> DEFAULT_CONSUMER_ROOTS = List.of("../pc-admin/src/api", "../miniprogram");
    private static final Set<String> CONSUMER_EXTENSIONS = Set.of(".js", ".vue", ".wxml", ".json");
    private static final int MAX_HITS_PER_CHANGE = 5;

    /** 一项契约变更。 */
    private record Change(String type, String key, String detail, String message) {
    }

    @Test
    void noIncompatibleContractChanges() throws Exception {
        ScanResult scan = ContractLoader.scan();
        Map<String, EndpointSpec> current = new LinkedHashMap<>();
        for (EndpointSpec s : scan.validatedEndpoints()) {
            current.put(s.key(), s);
        }

        Baseline baseline = Baseline.load();
        Exemptions exemptions = Exemptions.load();

        List<Change> incompatible = new ArrayList<>();
        List<Change> compatible = new ArrayList<>();

        compare(current, baseline, incompatible, compatible);

        List<Exemption> expired = validateExemptions(exemptions);
        assertTrue(expired.isEmpty(), "豁免配置已过期（REQ-10 到期必须清理）: " + renderExpired(expired));

        List<Change> blocking = applyExemptions(incompatible, exemptions);

        StringBuilder report = new StringBuilder(4096);
        report.append("========== ITER-T03 契约兼容性报告 ==========\n");
        report.append("[概要] 当前契约端点 ").append(current.size())
                .append(" 个（基线 ").append(baseline.endpointCount()).append(" 个，生成于 ")
                .append(baseline.generatedAt()).append("）；兼容变更 ").append(compatible.size())
                .append(" 项（放行）；不兼容变更 ").append(incompatible.size())
                .append(" 项（阻断 ").append(blocking.size()).append(" 项，豁免 ")
                .append(incompatible.size() - blocking.size()).append(" 项）；生效豁免 ")
                .append(exemptions.items().size()).append(" 条\n");

        appendSection(report, "[兼容变更-放行]（REQ-05：新增端点/新增可选字段/deprecated 过渡）", compatible);
        appendSection(report, "[不兼容变更-阻断]（REQ-04：删端点/删字段/改类型/改 auth/改 expectStatus）", blocking);

        if (!exemptions.items().isEmpty()) {
            report.append("[生效豁免]（REQ-10：均带原因与过期时间，报告可见）\n");
            for (Exemption ex : exemptions.items()) {
                report.append("  - ").append(ex.key())
                        .append(ex.field() == null ? "" : " field=" + ex.field())
                        .append(" | 原因: ").append(ex.reason())
                        .append(" | 责任人: ").append(ex.owner())
                        .append(" | 过期: ").append(ex.expiresAt()).append('\n');
            }
        }
        List<String> stale = staleExemptions(exemptions, incompatible);
        if (!stale.isEmpty()) {
            report.append("[豁免候选清理]（登记项当前无不兼容变更，建议移除）\n");
            stale.forEach(s -> report.append("  - ").append(s).append('\n'));
        }

        System.out.println(report);

        assertTrue(blocking.isEmpty(), renderBlocking(blocking) + "\n" + remediation());
    }

    // ===== 比对 =====

    private void compare(Map<String, EndpointSpec> current, Baseline baseline,
                         List<Change> incompatible, List<Change> compatible) {
        for (Map.Entry<String, BaselineEndpoint> en : baseline.endpoints().entrySet()) {
            String key = en.getKey();
            BaselineEndpoint base = en.getValue();
            EndpointSpec cur = current.get(key);
            if (cur == null) {
                if (base.deprecated()) {
                    compatible.add(new Change("deprecated 端点移除", key, null,
                            "基线已标记 deprecated，移除视为兼容（过渡完成）"));
                } else {
                    incompatible.add(new Change("端点删除", key, null,
                            "基线（" + base.file() + "）存在、当前契约缺失"));
                }
                continue;
            }
            if (!Objects.equals(base.auth(), cur.auth())) {
                incompatible.add(new Change("auth 变更", key, base.auth() + " -> " + cur.auth(),
                        "鉴权语义变化（收紧/放开均需评审）"));
            }
            if (!Objects.equals(base.expectStatus(), cur.expectStatus())) {
                incompatible.add(new Change("expectStatus 变更", key,
                        String.valueOf(base.expectStatus()) + " -> " + cur.expectStatus(),
                        "响应状态契约变化"));
            }
            if (!base.deprecated() && cur.deprecated()) {
                compatible.add(new Change("标记 deprecated", key, "true",
                        "过渡信号：后续移除需重新生成基线或登记豁免"));
            }
            Set<String> baseFields = new java.util.TreeSet<>(base.fields());
            Set<String> curFields = cur.sortedFields();
            for (String f : baseFields) {
                if (!curFields.contains(f)) {
                    incompatible.add(new Change("字段删除/类型变更", key, f,
                            "基线期望 " + f + "，当前契约缺失（含类型注解变更，如 amount:integer -> amount:string）"));
                }
            }
            for (String f : curFields) {
                if (!baseFields.contains(f)) {
                    compatible.add(new Change("字段新增", key, f, "新增可选字段，向后兼容"));
                }
            }
        }
        for (String key : current.keySet()) {
            if (!baseline.endpoints().containsKey(key)) {
                compatible.add(new Change("端点新增", key, null, "新端点，向后兼容"));
            }
        }
        compatible.sort(Comparator.comparing(Change::type).thenComparing(Change::key));
        incompatible.sort(Comparator.comparing(Change::type).thenComparing(Change::key));
    }

    // ===== 豁免 =====

    private List<Exemption> validateExemptions(Exemptions exemptions) {
        List<Exemption> expired = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Exemption ex : exemptions.items()) {
            if (ex.key() == null || ex.key().isBlank()) {
                fail("豁免配置缺少 key: " + ex);
            }
            if (ex.reason() == null || ex.reason().isBlank()) {
                fail("豁免配置缺少 reason（REQ-10 必须带原因）: " + ex.key());
            }
            if (ex.expiresAt() == null || ex.expiresAt().isBlank()) {
                fail("豁免配置缺少 expiresAt（REQ-10 必须带过期时间）: " + ex.key());
            }
            LocalDate exp;
            try {
                exp = LocalDate.parse(ex.expiresAt());
            } catch (DateTimeParseException e) {
                fail("豁免配置 expiresAt 格式非法（应为 yyyy-MM-dd）: " + ex.key() + " -> " + ex.expiresAt());
                return expired;
            }
            if (exp.isBefore(today)) {
                expired.add(ex);
            }
        }
        return expired;
    }

    private List<Change> applyExemptions(List<Change> incompatible, Exemptions exemptions) {
        List<Change> blocking = new ArrayList<>();
        for (Change c : incompatible) {
            boolean exempted = exemptions.items().stream()
                    .anyMatch(ex -> ex.key().equals(c.key())
                            && (ex.field() == null || ex.field().equals(c.detail())));
            if (!exempted) {
                blocking.add(c);
            }
        }
        return blocking;
    }

    private List<String> staleExemptions(Exemptions exemptions, List<Change> incompatible) {
        List<String> stale = new ArrayList<>();
        for (Exemption ex : exemptions.items()) {
            boolean hit = incompatible.stream()
                    .anyMatch(c -> c.key().equals(ex.key())
                            && (ex.field() == null || ex.field().equals(c.detail())));
            if (!hit) {
                stale.add(ex.key() + (ex.field() == null ? "" : " field=" + ex.field()));
            }
        }
        return stale;
    }

    // ===== 报告 =====

    private void appendSection(StringBuilder report, String title, List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        report.append(title).append('\n');
        for (Change c : changes) {
            report.append("  - [").append(c.type()).append("] ").append(c.key());
            if (c.detail() != null) {
                report.append("  ").append(c.detail());
            }
            report.append("  (").append(c.message()).append(")\n");
        }
    }

    private String renderBlocking(List<Change> blocking) {
        StringBuilder sb = new StringBuilder();
        sb.append("不兼容契约变更 ").append(blocking.size()).append(" 项（REQ-04 阻断合流）：\n");
        for (Change c : blocking) {
            sb.append("  - [").append(c.type()).append("] ").append(c.key());
            if (c.detail() != null) {
                sb.append("  ").append(c.detail());
            }
            sb.append("  (").append(c.message()).append(")\n");
        }
        sb.append(consumerImpact(blocking));
        return sb.toString();
    }

    private String renderExpired(List<Exemption> expired) {
        return expired.stream()
                .map(ex -> ex.key() + " field=" + ex.field() + " 过期于 " + ex.expiresAt())
                .collect(Collectors.joining("; "));
    }

    private String remediation() {
        return "处置方式：\n"
                + "  a) 有意变更：同步更新契约后重新生成基线并提交 ——\n"
                + "     mvn -o test -Dtest=ContractBaselineRegenerateTest -Dcontract.baseline.regenerate=true\n"
                + "     然后 review .contract-baseline.json 并提交，重跑本测试；\n"
                + "  b) 过渡期放行：在 src/test/resources/contracts/.contract-exemptions.json 登记豁免"
                + "（key + 可选 field + reason + owner + expiresAt），到期自动失效（REQ-10）。";
    }

    // ===== 消费者影响清单（REQ-09）=====

    /**
     * 扫描前端源码，产出受影响调用位置清单。
     * 消费者根目录不存在时降级为警告（idlefish-backend 未来可能独立成库）。
     */
    private String consumerImpact(List<Change> blocking) {
        List<Path> roots = consumerRoots();
        List<Path> existing = roots.stream().filter(Files::isDirectory).toList();
        if (existing.isEmpty()) {
            return "[受影响前端调用] 消费者源码目录不存在（" + roots + "），跳过影响清单（降级）。"
                    + "可用 -Dcontract.consumer.roots=<目录列表> 指定。\n";
        }
        // 预扫描文件行索引，避免逐变更重复读盘
        Map<Path, List<String>> fileLines = new LinkedHashMap<>();
        for (Path root : existing) {
            try (Stream<Path> walk = Files.walk(root, 8)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> CONSUMER_EXTENSIONS.contains(extension(p)))
                        .forEach(p -> {
                            try {
                                fileLines.put(p, Files.readAllLines(p));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            } catch (IOException | UncheckedIOException e) {
                return "[受影响前端调用] 扫描消费者目录失败: " + e.getMessage() + "（降级跳过）\n";
            }
        }

        StringBuilder sb = new StringBuilder("[受影响前端调用]（REQ-09 迁移指引，扫描 "
                + existing + " 共 " + fileLines.size() + " 个文件）\n");
        for (Change c : blocking) {
            String path = c.key().split(" ")[1];
            List<String> hits = new ArrayList<>();
            for (Map.Entry<Path, List<String>> e : fileLines.entrySet()) {
                for (int i = 0; i < e.getValue().size(); i++) {
                    String line = e.getValue().get(i);
                    if (line.contains(path)) {
                        hits.add(e.getKey() + ":" + (i + 1) + "  " + line.trim());
                        if (hits.size() >= MAX_HITS_PER_CHANGE) {
                            break;
                        }
                    }
                }
                if (hits.size() >= MAX_HITS_PER_CHANGE) {
                    break;
                }
            }
            if (hits.isEmpty()) {
                sb.append("  - ").append(c.key()).append("  未在前端源码中找到直接引用（可能经常量/代理封装）\n");
            } else {
                sb.append("  - ").append(c.key()).append('\n');
                hits.forEach(h -> sb.append("      ").append(h).append('\n'));
            }
        }
        return sb.toString();
    }

    private List<Path> consumerRoots() {
        String prop = System.getProperty("contract.consumer.roots");
        if (prop != null && !prop.isBlank()) {
            return Stream.of(prop.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Paths::get)
                    .toList();
        }
        return DEFAULT_CONSUMER_ROOTS.stream().map(Paths::get).toList();
    }

    private static String extension(Path p) {
        String n = p.getFileName().toString();
        int idx = n.lastIndexOf('.');
        return idx < 0 ? "" : n.substring(idx).toLowerCase(Locale.ROOT);
    }
}
