package com.idlefish.trade.analytics.narrative;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 维度贡献度分析（ITER-U06）：纯函数，无 DB / 无 Spring 依赖，离线可单测。
 * <p>支持两类归因：①占比分析（某维度各取值对总量的贡献份额与累计份额，标记 TopN）；
 * ②负向归因（当前 vs 上期，找出拖累总量的 TopN 维度取值）。</p>
 */
public final class ContributionAnalyzer {

    private ContributionAnalyzer() {
    }

    /** 单个维度取值（label + value）。 */
    public record Item(String label, double value) {
    }

    /** 占比分析结果。 */
    public record Contribution(String label, double value, double share, double cumulativeShare, boolean top) {
    }

    /** 负向归因结果（用于异常下钻）。 */
    public record Attribution(String label, double current, double previous, double delta) {
    }

    /** 占比分析：按 value 降序计算份额与累计份额，前 topN 标记为 top。 */
    public static List<Contribution> analyze(List<Item> items, int topN) {
        double total = items.stream().mapToDouble(Item::value).sum();
        if (total <= 0) {
            return List.of();
        }
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingDouble(Item::value).reversed());

        List<Contribution> out = new ArrayList<>();
        double cum = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Item it = sorted.get(i);
            double share = it.value() / total;
            cum += share;
            out.add(new Contribution(it.label(), it.value(), share, cum, i < topN));
        }
        return out;
    }

    /** 负向归因：当前 vs 上期，按 delta 升序（最负优先）返回前 topN 拖累项。 */
    public static List<Attribution> negativeContributors(List<Item> current, List<Item> previous, int topN) {
        Map<String, Double> prevMap = new LinkedHashMap<>();
        for (Item it : previous) {
            prevMap.put(it.label(), it.value());
        }
        List<Attribution> out = new ArrayList<>();
        for (Item it : current) {
            double prev = prevMap.getOrDefault(it.label(), 0d);
            out.add(new Attribution(it.label(), it.value(), prev, it.value() - prev));
        }
        out.sort(Comparator.comparingDouble(Attribution::delta));
        return out.stream().limit(topN).toList();
    }
}
