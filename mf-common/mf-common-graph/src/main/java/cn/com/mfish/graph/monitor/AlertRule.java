package cn.com.mfish.graph.monitor;

import lombok.Data;

/**
 * 告警规则
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class AlertRule {
    private String ruleId;
    private String metricName;
    private double threshold;
    private AlertLevel level;
    private long windowMs;
    private String businessLine;
    private boolean enabled;

    public static AlertRule of(String ruleId, String metricName, double threshold, AlertLevel level) {
        AlertRule rule = new AlertRule();
        rule.setRuleId(ruleId);
        rule.setMetricName(metricName);
        rule.setThreshold(threshold);
        rule.setLevel(level);
        rule.setEnabled(true);
        return rule;
    }
}