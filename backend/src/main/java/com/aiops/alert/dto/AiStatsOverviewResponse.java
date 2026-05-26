package com.aiops.alert.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * AiStatsView 顶部 hero + 双图表 + 成本卡 一次性返回的聚合视图。
 */
@Data
@Builder
public class AiStatsOverviewResponse {

    // ---- Hero（今日 vs 昨日） ----
    private long todayCallTotal;
    private long todayTokenTotal;
    private double todaySuccessRate;
    private long yesterdayCallTotal;
    private long yesterdayTokenTotal;
    private double yesterdaySuccessRate;

    // ---- 场景分布（今日） ----
    private List<SceneStat> sceneDistribution;

    // ---- 7 日趋势 ----
    private List<TrendItem> sevenDayTrend;

    // ---- 成本估算 ----
    private BigDecimal todayCost;
    private BigDecimal monthCost;
    /** 货币代号，固定 "CNY" */
    private String costCurrency;

    @Data
    @Builder
    public static class SceneStat {
        /** 归一化后的场景名：NL2RULE / EVENT_SUMMARY / CHAT / THRESHOLD / OTHER */
        private String scene;
        private long callCount;
        private long tokenTotal;
        /** tokenTotal / 全部 todayTokenTotal 的百分比，0-100 */
        private double tokenPercent;
    }

    @Data
    @Builder
    public static class TrendItem {
        /** yyyy-MM-dd */
        private String date;
        private long callCount;
    }
}
