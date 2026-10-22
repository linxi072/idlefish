package com.idlefish.trade.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idlefish.trade.contract.ContractLoader.EndpointSpec;
import com.idlefish.trade.contract.ContractLoader.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ITER-T03 契约基线再生工具（REQ-04/05 的「共识快照」生成入口）。
 * <p>
 * 仅在显式开启时运行，避免污染常规测试：
 * <pre>
 * mvn -o test -Dtest=ContractBaselineRegenerateTest -Dcontract.baseline.regenerate=true
 * </pre>
 * 输出位置默认 {@code src/test/resources/contracts/.contract-baseline.json}，
 * 可用 {@code -Dcontract.baseline.output=<路径>} 覆盖。
 * <p>
 * 注意：Maven 资源拷贝发生在 test-compile 之后，<b>同一次</b> mvn 调用内兼容性测试读到的仍是旧基线；
 * 生成后须重新执行 {@link ContractCompatibilityTest} 验证，并 review 基线 diff 后提交。
 */
@EnabledIfSystemProperty(named = "contract.baseline.regenerate", matches = "true")
class ContractBaselineRegenerateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void regenerateBaseline() throws Exception {
        ScanResult scan = ContractLoader.scan();
        List<EndpointSpec> specs = scan.validatedEndpoints();
        assertFalse(specs.isEmpty(), "未扫描到任何契约端点，拒绝生成空基线");

        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 1);
        root.put("generatedAt", java.time.LocalDate.now().toString());
        root.put("endpointCount", specs.size());
        root.put("generatedBy", "ContractBaselineRegenerateTest（ITER-T03）；不兼容变更请走评审后再生，禁止为绕过门禁随意再生");

        ObjectNode endpoints = root.putObject("endpoints");
        TreeMap<String, EndpointSpec> sorted = new TreeMap<>();
        specs.forEach(s -> sorted.put(s.key(), s));
        for (var en : sorted.entrySet()) {
            EndpointSpec s = en.getValue();
            ObjectNode e = endpoints.putObject(en.getKey());
            if (s.auth() != null) {
                e.put("auth", s.auth());
            }
            if (s.expectStatus() != null) {
                e.put("expectStatus", s.expectStatus());
            }
            ArrayNode fields = e.putArray("fields");
            s.sortedFields().forEach(fields::add);
            if (s.deprecated()) {
                e.put("deprecated", true);
            }
            e.put("file", s.file());
        }

        Path out = Paths.get(System.getProperty("contract.baseline.output",
                "src/test/resources/contracts/.contract-baseline.json"));
        Files.createDirectories(out.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);

        assertTrue(Files.exists(out) && Files.size(out) > 0, "基线写出失败: " + out);
        System.out.println("[ContractBaseline] 已生成基线：" + out.toAbsolutePath()
                + "（端点 " + specs.size() + " 个，来源文件 " + scan.validatedFileCount() + " 个）");
        System.out.println("[ContractBaseline] 请 git diff review 基线变更后提交，并重跑 "
                + "ContractCompatibilityTest 验证门禁通过。");
    }
}
