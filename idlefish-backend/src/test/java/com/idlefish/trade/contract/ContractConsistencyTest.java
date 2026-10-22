package com.idlefish.trade.contract;

import com.idlefish.trade.contract.ContractLoader.EndpointSpec;
import com.idlefish.trade.contract.ContractLoader.ScanResult;
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-15.4 契约一致性测试 + ITER-T03 覆盖率自校验（离线、无 MySQL、无 Spring 上下文）。
 * <p>
 * 1) 经 {@link ContractLoader} 自动扫描 {@code classpath:contracts/*.json}（REQ-01）；
 * 2) 覆盖率自校验：目录内每个 JSON 要么被校验、要么以显式已知原因排除（REQ-02）；
 * 3) 结构合法性与全局端点唯一性由 {@link ContractLoader} 统一执行（REQ-08 定位到文件行列）；
 * 4) 用 {@link ClassPathScanningCandidateComponentProvider} 反射扫描
 *    {@code com.idlefish.trade} 下全部 {@code *Controller}，构建「METHOD PATH」实现集；
 * 5) 断言每个（未标记 deprecated 的）契约端点都在代码中真实存在（防契约漂移）。
 * <p>
 * 兼容性判定（删除字段/改类型阻断、新增放行）见 {@link ContractCompatibilityTest}。
 */
class ContractConsistencyTest {

    private static final String BASE_PACKAGE = "com.idlefish.trade";

    @Test
    void allContractEndpointsExistInCode() throws Exception {
        ScanResult scan = ContractLoader.scan();
        assertTrue(scan.directoryFound(), "classpath 未定位到 contracts 目录（资源未拷贝到 test-classes）");
        assertFalse(scan.validatedEndpoints().isEmpty(),
                "未扫描到任何契约文件（已排除 admin.json / observability.json / 点号元数据）");

        // REQ-02 覆盖率自校验：目录文件数 = 已校验 + 显式排除，不等即失败
        int total = scan.totalJsonFiles();
        int validated = scan.validatedFileCount();
        int skipped = scan.skippedFileCount();
        assertEquals(total, validated + skipped,
                "契约目录覆盖率自校验失败（REQ-02）：目录 JSON " + total + " 个 != 已校验 " + validated + " + 排除 " + skipped);
        System.out.println("[ContractConsistency] 覆盖率自校验通过：目录内 JSON " + total
                + " 个 = 已校验 " + validated + " + 排除 " + skipped + " " + scan.skipReasonCounts());

        Set<String> implemented = collectImplementedEndpoints();
        assertTrue(!implemented.isEmpty(), "反射扫描未收集到任何 Controller 端点，请检查扫描包名");

        int checked = 0;
        int deprecated = 0;
        for (EndpointSpec spec : scan.validatedEndpoints()) {
            if (spec.deprecated()) {
                deprecated++;
                System.out.println("  [deprecated] " + spec.key() + " @ " + spec.file() + " 跳过漂移断言");
                continue;
            }
            checked++;
            assertTrue(implemented.contains(spec.key()),
                    "契约端点未在代码中找到实现（漂移）: " + spec.key() + " @ " + spec.file()
                            + "\n  已实现端点样例: " + sample(implemented, spec.key(), 8));
        }
        System.out.println("[ContractConsistency] 校验通过：契约端点 " + checked
                + " 个（deprecated " + deprecated + " 个跳过），代码实现端点 " + implemented.size() + " 个，全部命中。");
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
