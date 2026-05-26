package com.aiops.alert.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.BizException;
import com.aiops.alert.dto.AiCallLogQuery;
import com.aiops.alert.dto.AiCallLogResponse;
import com.aiops.alert.dto.AiStatsOverviewResponse;
import com.aiops.alert.dto.AiStatsOverviewResponse.SceneStat;
import com.aiops.alert.dto.AiStatsOverviewResponse.TrendItem;
import com.aiops.alert.dto.PageResult;
import com.aiops.alert.entity.AiCallLog;
import com.aiops.alert.entity.LlmModelConfig;
import com.aiops.alert.mapper.AiCallLogMapper;
import com.aiops.alert.mapper.LlmModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * AI 调用统计聚合服务。
 *
 * 实现风格：照搬 DashboardService 的"全表 selectList → Java 端 groupingBy"模式，避开 H2/MySQL 方言差异。
 *
 * 列表接口（page / slowTop）映射 DTO 时不返回 requestPayload / responsePayload / reasoningContent，
 * 详情接口（get）才返回，以满足 Property 12 契约。
 */
@Service
public class AiCallStatsService {

    private static final Set<String> KNOWN_SCENES = Set.of(
            "NL2RULE", "EVENT_SUMMARY", "CHAT", "THRESHOLD"
    );

    private final AiCallLogMapper logMapper;
    private final LlmModelConfigMapper configMapper;

    public AiCallStatsService(AiCallLogMapper logMapper, LlmModelConfigMapper configMapper) {
        this.logMapper = logMapper;
        this.configMapper = configMapper;
    }

    // ---------------- Overview（hero + 场景分布 + 7 日趋势 + 成本卡） ----------------

    public AiStatsOverviewResponse loadOverview() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime weekAgoStart = today.minusDays(6).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        // 拉取最近 7 天 + 本月起的 union 区间一次性取完，再按窗口过滤
        LocalDateTime fetchStart = monthStart.isBefore(weekAgoStart) ? monthStart : weekAgoStart;
        // 兜底再多取 1 天，应对昨日跨日边界
        if (fetchStart.isAfter(yesterdayStart)) {
            fetchStart = yesterdayStart;
        }
        List<AiCallLog> logs = logMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreatedAt, fetchStart));

        Map<Long, LlmModelConfig> priceIndex = loadPriceIndex(logs);

        List<AiCallLog> todayLogs = logs.stream()
                .filter(l -> notNullAfter(l.getCreatedAt(), todayStart) && before(l.getCreatedAt(), tomorrowStart))
                .toList();
        List<AiCallLog> yesterdayLogs = logs.stream()
                .filter(l -> notNullAfter(l.getCreatedAt(), yesterdayStart) && before(l.getCreatedAt(), todayStart))
                .toList();
        List<AiCallLog> weekLogs = logs.stream()
                .filter(l -> notNullAfter(l.getCreatedAt(), weekAgoStart))
                .toList();
        List<AiCallLog> monthLogs = logs.stream()
                .filter(l -> notNullAfter(l.getCreatedAt(), monthStart))
                .toList();

        long todayCalls = todayLogs.size();
        long todayTokens = todayLogs.stream()
                .mapToLong(l -> nz(l.getPromptTokens()) + nz(l.getCompletionTokens()))
                .sum();
        double todaySuccessRate = successRate(todayLogs);

        long yesterdayCalls = yesterdayLogs.size();
        long yesterdayTokens = yesterdayLogs.stream()
                .mapToLong(l -> nz(l.getPromptTokens()) + nz(l.getCompletionTokens()))
                .sum();
        double yesterdaySuccessRate = successRate(yesterdayLogs);

        // 场景分布（基于今日数据）
        Map<String, List<AiCallLog>> sceneGroups = todayLogs.stream()
                .collect(Collectors.groupingBy(l -> normalizeScene(l.getScene())));
        long todayTokenTotalForPercent = todayTokens == 0 ? 1 : todayTokens;
        List<SceneStat> sceneDist = sceneGroups.entrySet().stream()
                .map(e -> {
                    long sceneTokens = e.getValue().stream()
                            .mapToLong(l -> nz(l.getPromptTokens()) + nz(l.getCompletionTokens()))
                            .sum();
                    return SceneStat.builder()
                            .scene(e.getKey())
                            .callCount(e.getValue().size())
                            .tokenTotal(sceneTokens)
                            .tokenPercent(roundPct(sceneTokens * 100.0 / todayTokenTotalForPercent))
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getCallCount(), a.getCallCount()))
                .toList();

        // 7 日趋势
        Map<LocalDate, Long> byDate = weekLogs.stream()
                .filter(l -> l.getCreatedAt() != null)
                .collect(Collectors.groupingBy(l -> l.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<TrendItem> trend = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(6 - i);
            trend.add(TrendItem.builder()
                    .date(d.toString())
                    .callCount(byDate.getOrDefault(d, 0L))
                    .build());
        }

        // 成本估算
        BigDecimal todayCost = todayLogs.stream()
                .map(l -> calcCost(l, priceIndex))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthCost = monthLogs.stream()
                .map(l -> calcCost(l, priceIndex))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AiStatsOverviewResponse.builder()
                .todayCallTotal(todayCalls)
                .todayTokenTotal(todayTokens)
                .todaySuccessRate(todaySuccessRate)
                .yesterdayCallTotal(yesterdayCalls)
                .yesterdayTokenTotal(yesterdayTokens)
                .yesterdaySuccessRate(yesterdaySuccessRate)
                .sceneDistribution(sceneDist)
                .sevenDayTrend(trend)
                .todayCost(todayCost.setScale(2, RoundingMode.HALF_UP))
                .monthCost(monthCost.setScale(2, RoundingMode.HALF_UP))
                .costCurrency("CNY")
                .build();
    }

    // ---------------- 慢调用 Top N ----------------

    public List<AiCallLogResponse> slowTop(int days, int limit) {
        int safeDays = Math.max(1, Math.min(days, 30));
        int safeLimit = Math.max(1, Math.min(limit, 50));
        LocalDateTime since = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();

        List<AiCallLog> logs = logMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreatedAt, since)
                .isNotNull(AiCallLog::getDurationMs)
                .orderByDesc(AiCallLog::getDurationMs)
                .last("limit " + safeLimit));
        Map<Long, LlmModelConfig> priceIndex = loadPriceIndex(logs);
        return logs.stream().map(l -> toResponse(l, priceIndex, false)).toList();
    }

    // ---------------- 流水分页 ----------------

    public PageResult<AiCallLogResponse> page(AiCallLogQuery q) {
        int page = q.getPage() == null ? 1 : Math.max(1, q.getPage());
        int size = q.getSize() == null ? 20 : Math.max(1, Math.min(100, q.getSize()));

        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<AiCallLog>()
                .eq(StrUtil.isNotBlank(q.getScene()), AiCallLog::getScene, q.getScene())
                .eq(StrUtil.isNotBlank(q.getModelName()), AiCallLog::getModelName, q.getModelName())
                .eq(StrUtil.isNotBlank(q.getStatus()), AiCallLog::getStatus, q.getStatus())
                .orderByDesc(AiCallLog::getCreatedAt);

        Page<AiCallLog> p = new Page<>(page, size);
        Page<AiCallLog> result = logMapper.selectPage(p, wrapper);
        Map<Long, LlmModelConfig> priceIndex = loadPriceIndex(result.getRecords());
        List<AiCallLogResponse> records = result.getRecords().stream()
                .map(l -> toResponse(l, priceIndex, false))
                .toList();
        return PageResult.of(result.getTotal(), page, size, records);
    }

    // ---------------- 单条详情（含完整 payload） ----------------

    public AiCallLogResponse get(Long id) {
        AiCallLog log = logMapper.selectById(id);
        if (log == null) {
            throw new BizException("AI 调用流水不存在");
        }
        Map<Long, LlmModelConfig> priceIndex = loadPriceIndex(List.of(log));
        return toResponse(log, priceIndex, true);
    }

    // ---------------- 私有工具 ----------------

    /** 把 AiCallLog 转 DTO。verbose=true 时附带 prompt/response/reasoning 完整内容。 */
    private AiCallLogResponse toResponse(AiCallLog log, Map<Long, LlmModelConfig> priceIndex, boolean verbose) {
        int total = (int) (nz(log.getPromptTokens()) + nz(log.getCompletionTokens()));
        BigDecimal cost = calcCost(log, priceIndex).setScale(4, RoundingMode.HALF_UP);
        return AiCallLogResponse.builder()
                .id(log.getId())
                .scene(log.getScene())
                .modelConfigId(log.getModelConfigId())
                .modelName(log.getModelName())
                .promptTokens(log.getPromptTokens())
                .completionTokens(log.getCompletionTokens())
                .totalTokens(total)
                .durationMs(log.getDurationMs())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .reasoningContent(verbose ? log.getReasoningContent() : null)
                .requestPayload(verbose ? log.getRequestPayload() : null)
                .responsePayload(verbose ? log.getResponsePayload() : null)
                .estimatedCost(cost)
                .createdAt(log.getCreatedAt())
                .build();
    }

    /** 一次性把所涉及 modelConfigId 的 LlmModelConfig 读出来，避免每条记录单独 selectById。 */
    private Map<Long, LlmModelConfig> loadPriceIndex(Collection<AiCallLog> logs) {
        Set<Long> ids = new HashSet<>();
        for (AiCallLog l : logs) {
            if (l.getModelConfigId() != null) ids.add(l.getModelConfigId());
        }
        if (ids.isEmpty()) return Map.of();
        Map<Long, LlmModelConfig> map = new HashMap<>();
        for (LlmModelConfig c : configMapper.selectBatchIds(ids)) {
            map.put(c.getId(), c);
        }
        return map;
    }

    /** 估算成本：(prompt_tokens / 1000) * prompt_price + (completion_tokens / 1000) * completion_price */
    private BigDecimal calcCost(AiCallLog log, Map<Long, LlmModelConfig> priceIndex) {
        if (log == null || log.getModelConfigId() == null) return BigDecimal.ZERO;
        LlmModelConfig cfg = priceIndex.get(log.getModelConfigId());
        if (cfg == null) return BigDecimal.ZERO;
        BigDecimal pp = nzBd(cfg.getPromptPricePer1k());
        BigDecimal cp = nzBd(cfg.getCompletionPricePer1k());
        if (pp.signum() == 0 && cp.signum() == 0) return BigDecimal.ZERO;
        BigDecimal pt = BigDecimal.valueOf(nz(log.getPromptTokens()));
        BigDecimal ct = BigDecimal.valueOf(nz(log.getCompletionTokens()));
        return pt.multiply(pp).add(ct.multiply(cp))
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
    }

    private String normalizeScene(String s) {
        if (s == null) return "OTHER";
        return KNOWN_SCENES.contains(s) ? s : "OTHER";
    }

    private double successRate(List<AiCallLog> logs) {
        if (logs.isEmpty()) return 0.0;
        long ok = logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
        return roundPct(ok * 100.0 / logs.size());
    }

    private double roundPct(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private long nz(Integer v) { return v == null ? 0L : v.longValue(); }
    private BigDecimal nzBd(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private boolean notNullAfter(LocalDateTime ts, LocalDateTime threshold) {
        return ts != null && !ts.isBefore(threshold);
    }

    private boolean before(LocalDateTime ts, LocalDateTime threshold) {
        return ts != null && ts.isBefore(threshold);
    }
}
