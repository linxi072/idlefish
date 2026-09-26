package com.idlefish.trade.risk.service;

/**
 * 异常评分卡纯函数（F-15.1）：聚合多维风险信号，输出 low/mid/high 三档。
 * 纯函数无 IO，阈值随信号透传，保证确定性可测。
 */
public final class RiskScorer {

    private static final int W_BLACKLIST = 60;     // 设备黑名单为确定性高危信号，直达 high 档（原 R6 直接冻结）
    private static final int W_NEW_DEVICE_HIGH = 35;
    private static final int W_MULTI_ACCOUNT = 25;
    private static final int W_OPEN_RISK = 15;       // 每个未处置事件（封顶 3 个）
    private static final int OPEN_RISK_CAP = 3;
    private static final int BAND_HIGH = 60;
    private static final int BAND_MID = 30;

    private RiskScorer() {
    }

    /**
     * 聚合信号评分。
     *
     * @return 评分结果（band=low/mid/high, score=累计分）
     */
    public static RiskBand score(RiskSignals s) {
        int sc = 0;
        if (s.isBlacklisted()) {
            sc += W_BLACKLIST;
        }
        if (s.isNewDevice() && s.getAmountFen() >= s.getHighAmountThresholdFen()) {
            sc += W_NEW_DEVICE_HIGH;
        }
        if (s.getBindUsers() >= s.getDeviceAccountThreshold()) {
            sc += W_MULTI_ACCOUNT;
        }
        sc += Math.min(s.getOpenRiskCount(), OPEN_RISK_CAP) * W_OPEN_RISK;

        String band = sc >= BAND_HIGH ? "high" : (sc >= BAND_MID ? "mid" : "low");
        return new RiskBand(band, sc);
    }

    /** 评分输入信号（含阈值，便于纯函数复用配置）。 */
    public static class RiskSignals {
        private final int openRiskCount;
        private final boolean newDevice;
        private final int bindUsers;
        private final boolean blacklisted;
        private final long amountFen;
        private final long highAmountThresholdFen;
        private final int deviceAccountThreshold;

        public RiskSignals(int openRiskCount, boolean newDevice, int bindUsers, boolean blacklisted,
                          long amountFen, long highAmountThresholdFen, int deviceAccountThreshold) {
            this.openRiskCount = openRiskCount;
            this.newDevice = newDevice;
            this.bindUsers = bindUsers;
            this.blacklisted = blacklisted;
            this.amountFen = amountFen;
            this.highAmountThresholdFen = highAmountThresholdFen;
            this.deviceAccountThreshold = deviceAccountThreshold;
        }

        public int getOpenRiskCount() {
            return openRiskCount;
        }

        public boolean isNewDevice() {
            return newDevice;
        }

        public int getBindUsers() {
            return bindUsers;
        }

        public boolean isBlacklisted() {
            return blacklisted;
        }

        public long getAmountFen() {
            return amountFen;
        }

        public long getHighAmountThresholdFen() {
            return highAmountThresholdFen;
        }

        public int getDeviceAccountThreshold() {
            return deviceAccountThreshold;
        }
    }

    /** 评分结果。 */
    public static class RiskBand {
        private final String band;
        private final int score;

        public RiskBand(String band, int score) {
            this.band = band;
            this.score = score;
        }

        public String getBand() {
            return band;
        }

        public int getScore() {
            return score;
        }
    }
}
