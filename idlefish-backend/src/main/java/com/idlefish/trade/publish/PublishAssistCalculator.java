package com.idlefish.trade.publish;

/**
 * 发布辅助计算（U01）纯函数层：成色系数、估价区间、草稿模板。无副作用，便于离线单测。
 * 所有估价结果为「建议区间」，明确为参考值（REQ-09 仅供参考，最终定价权归卖家）。
 */
public final class PublishAssistCalculator {

    private PublishAssistCalculator() {
    }

    /** 成色 → 文案（1 全新 ~ 5 功能完好）。 */
    public static String conditionLabel(Integer level) {
        if (level == null) {
            return "未知成色";
        }
        return switch (level) {
            case 1 -> "全新";
            case 2 -> "95新";
            case 3 -> "9成新";
            case 4 -> "8成新";
            case 5 -> "功能完好";
            default -> "其他成色";
        };
    }

    /** 成色 → 折损系数（估价用）。 */
    public static double conditionCoefficient(int level) {
        return switch (level) {
            case 1 -> 1.0;
            case 2 -> 0.85;
            case 3 -> 0.70;
            case 4 -> 0.55;
            case 5 -> 0.40;
            default -> 0.5;
        };
    }

    /**
     * 规则兜底估价区间（分）：baseFen × 成色系数，上下浮动 10%。
     * 仅当 baseFen > 0 时有效；无基准价（冷门类目）返回 null，由调用方降级（REQ-05/边界2）。
     *
     * @return {minFen, maxFen} 或 null
     */
    public static long[] suggestPriceRange(long baseFen, int level) {
        if (baseFen <= 0) {
            return null;
        }
        double coef = conditionCoefficient(level);
        long center = Math.round(baseFen * coef);
        long min = Math.round(center * 0.9);
        long max = Math.round(center * 1.1);
        return new long[]{min, max};
    }

    /** REQ-02 规则草稿标题模板（AI 不可用时兜底）。 */
    public static String draftTitle(String categoryName, Integer conditionLevel) {
        String c = conditionLabel(conditionLevel);
        String cat = categoryName == null ? "闲置" : categoryName;
        return "【" + c + "】" + cat + " 低价转让";
    }

    /** REQ-02 规则草稿描述模板（AI 不可用时兜底）。 */
    public static String draftDescription(String categoryName, Integer conditionLevel, String title) {
        String c = conditionLabel(conditionLevel);
        String cat = categoryName == null ? "本商品" : categoryName;
        StringBuilder sb = new StringBuilder();
        sb.append("【商品描述】\n");
        sb.append("品类：").append(cat).append("\n");
        sb.append("成色：").append(c).append("\n");
        if (title != null && !title.isBlank()) {
            sb.append("标题：").append(title).append("\n");
        }
        sb.append("商品功能正常，具体瑕疵请查看图片；支持平台验货，放心交易。\n");
        sb.append("（本描述为系统辅助生成，请以实物为准）");
        return sb.toString();
    }
}
