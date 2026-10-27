package com.idlefish.trade.common.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ObservabilityController#health} 探针语义单测（T07 容器化）。
 * <p>独立 MockMvc，无 Spring 上下文；以 Mockito 固定各依赖探活结果，验证 HTTP 状态码与 status 字段：
 * <ul>
 *   <li>全部正常 → 200 / UP；</li>
 *   <li>核心依赖 MySQL DOWN → 503 / DOWN（触发 k8s 摘流）；</li>
 *   <li>次要依赖降级 DEGRADED → 200 / DEGRADED（不误摘流）。</li>
 * </ul>
 */
class ObservabilityControllerTest {

    private MockMvc mockMvc;
    private final DependencyProbes probes = Mockito.mock(DependencyProbes.class);

    @BeforeEach
    void setUp() {
        ObservabilityController controller = new ObservabilityController(new MetricsRegistry(), probes);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 固定全部依赖为 UP。 */
    private void allUp() {
        when(probes.probeMysql()).thenReturn(DependencyProbes.ProbeResult.up("ok"));
        when(probes.probeRedis()).thenReturn(DependencyProbes.ProbeResult.up("ok"));
        when(probes.probeElasticsearch()).thenReturn(DependencyProbes.ProbeResult.up("ok"));
        when(probes.probeRocketMq()).thenReturn(DependencyProbes.ProbeResult.up("ok"));
        when(probes.probeOss()).thenReturn(DependencyProbes.ProbeResult.up("ok"));
        when(probes.probeSms()).thenReturn(DependencyProbes.ProbeResult.up("ok"));
    }

    @Test
    void health_allUp_returns200AndUp() throws Exception {
        allUp();
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void health_mysqlDown_returns503AndDown() throws Exception {
        allUp();
        when(probes.probeMysql()).thenReturn(DependencyProbes.ProbeResult.down("connection refused"));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())   // 503
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void health_secondaryDegraded_returns200AndDegraded() throws Exception {
        allUp();
        when(probes.probeElasticsearch()).thenReturn(DependencyProbes.ProbeResult.degraded("unreachable"));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"));
    }
}
