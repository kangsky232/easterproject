package com.smoke.service;

import com.smoke.entity.AlertRecord;
import com.smoke.entity.SmokeData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class TelemetryAlertEvaluator {

    static final int DEFAULT_SMOKE_WARNING_PPM = 100;
    static final int SMOKE_DANGER_PPM = 300;
    static final int TEMPERATURE_WARNING_C = 45;
    static final int TEMPERATURE_DANGER_C = 60;
    static final int HUMIDITY_DANGER_PERCENT = 20;
    static final int CURRENT_WARNING_A = 10;
    static final int CURRENT_DANGER_A = 15;
    static final int WIRE_TEMPERATURE_WARNING_C = 70;
    static final int WIRE_TEMPERATURE_DANGER_C = 90;
    static final int CO_WARNING_PPM = 50;
    static final int CO_DANGER_PPM = 100;

    private static final BigDecimal TEMPERATURE_RISE_WARNING = BigDecimal.valueOf(10);
    private static final BigDecimal HUMIDITY_DROP_WARNING = BigDecimal.valueOf(20);
    private static final BigDecimal CURRENT_FLUCTUATION_WARNING = BigDecimal.valueOf(5);
    private static final Duration TREND_WINDOW = Duration.ofMinutes(5);

    public List<AlertSignal> evaluate(SmokeData current, SmokeData previous, Integer smokeWarningThreshold) {
        int smokeWarning = smokeWarningThreshold == null || smokeWarningThreshold < 1
                ? DEFAULT_SMOKE_WARNING_PPM
                : smokeWarningThreshold;
        List<AlertSignal> signals = new ArrayList<>();

        addSmoke(signals, current.getConcentration(), smokeWarning);
        addTemperature(signals, current, previous);
        addHumidity(signals, current, previous);
        addCurrent(signals, current, previous);
        addWireTemperature(signals, current.getWireTemperature());
        addCarbonMonoxide(signals, current.getCoValue());
        return List.copyOf(signals);
    }

    private void addSmoke(List<AlertSignal> signals, BigDecimal value, int warningThreshold) {
        if (greaterThan(value, SMOKE_DANGER_PPM)) {
            signals.add(signal(AlertRecord.TYPE_SMOKE, AlertRecord.SEVERITY_DANGER, value,
                    SMOKE_DANGER_PPM, "烟雾浓度 > 300 ppm"));
        } else if (atLeast(value, warningThreshold)) {
            signals.add(signal(AlertRecord.TYPE_SMOKE, AlertRecord.SEVERITY_WARNING, value,
                    warningThreshold, "烟雾浓度达到预警阈值（建议范围 100–300 ppm）"));
        }
    }

    private void addTemperature(List<AlertSignal> signals, SmokeData current, SmokeData previous) {
        BigDecimal value = current.getTemperature();
        if (greaterThan(value, TEMPERATURE_DANGER_C)) {
            signals.add(signal(AlertRecord.TYPE_TEMPERATURE, AlertRecord.SEVERITY_DANGER, value,
                    TEMPERATURE_DANGER_C, "环境温度 > 60℃"));
        } else if (greaterThan(value, TEMPERATURE_WARNING_C)) {
            signals.add(signal(AlertRecord.TYPE_TEMPERATURE, AlertRecord.SEVERITY_WARNING, value,
                    TEMPERATURE_WARNING_C, "环境温度 > 45℃"));
        } else if (withinTrendWindow(current, previous)
                && increase(value, previous.getTemperature()).compareTo(TEMPERATURE_RISE_WARNING) >= 0) {
            signals.add(signal(AlertRecord.TYPE_TEMPERATURE, AlertRecord.SEVERITY_WARNING, value,
                    TEMPERATURE_RISE_WARNING.intValue(), "5分钟内温度上升 ≥ 10℃"));
        }
    }

    private void addHumidity(List<AlertSignal> signals, SmokeData current, SmokeData previous) {
        BigDecimal value = current.getHumidity();
        if (lessThan(value, HUMIDITY_DANGER_PERCENT)) {
            signals.add(signal(AlertRecord.TYPE_HUMIDITY, AlertRecord.SEVERITY_DANGER, value,
                    HUMIDITY_DANGER_PERCENT, "环境湿度 < 20%"));
        } else if (withinTrendWindow(current, previous)
                && decrease(previous.getHumidity(), value).compareTo(HUMIDITY_DROP_WARNING) >= 0) {
            signals.add(signal(AlertRecord.TYPE_HUMIDITY, AlertRecord.SEVERITY_WARNING, value,
                    HUMIDITY_DROP_WARNING.intValue(), "5分钟内湿度下降 ≥ 20 个百分点"));
        }
    }

    private void addCurrent(List<AlertSignal> signals, SmokeData current, SmokeData previous) {
        BigDecimal value = current.getCurrentValue();
        if (greaterThan(value, CURRENT_DANGER_A)) {
            signals.add(signal(AlertRecord.TYPE_CURRENT, AlertRecord.SEVERITY_DANGER, value,
                    CURRENT_DANGER_A, "电流 > 15A"));
        } else if (greaterThan(value, CURRENT_WARNING_A)) {
            signals.add(signal(AlertRecord.TYPE_CURRENT, AlertRecord.SEVERITY_WARNING, value,
                    CURRENT_WARNING_A, "电流 > 10A"));
        } else if (withinTrendWindow(current, previous)
                && absoluteChange(value, previous.getCurrentValue()).compareTo(CURRENT_FLUCTUATION_WARNING) >= 0) {
            signals.add(signal(AlertRecord.TYPE_CURRENT, AlertRecord.SEVERITY_WARNING, value,
                    CURRENT_FLUCTUATION_WARNING.intValue(), "5分钟内电流波动 ≥ 5A"));
        }
    }

    private void addWireTemperature(List<AlertSignal> signals, BigDecimal value) {
        if (greaterThan(value, WIRE_TEMPERATURE_DANGER_C)) {
            signals.add(signal(AlertRecord.TYPE_WIRE_TEMPERATURE, AlertRecord.SEVERITY_DANGER, value,
                    WIRE_TEMPERATURE_DANGER_C, "线缆温度 > 90℃"));
        } else if (greaterThan(value, WIRE_TEMPERATURE_WARNING_C)) {
            signals.add(signal(AlertRecord.TYPE_WIRE_TEMPERATURE, AlertRecord.SEVERITY_WARNING, value,
                    WIRE_TEMPERATURE_WARNING_C, "线缆温度 > 70℃"));
        }
    }

    private void addCarbonMonoxide(List<AlertSignal> signals, BigDecimal value) {
        if (greaterThan(value, CO_DANGER_PPM)) {
            signals.add(signal(AlertRecord.TYPE_CO, AlertRecord.SEVERITY_DANGER, value,
                    CO_DANGER_PPM, "一氧化碳浓度 > 100 ppm"));
        } else if (atLeast(value, CO_WARNING_PPM)) {
            signals.add(signal(AlertRecord.TYPE_CO, AlertRecord.SEVERITY_WARNING, value,
                    CO_WARNING_PPM, "一氧化碳浓度达到 50–100 ppm 预警范围"));
        }
    }

    private AlertSignal signal(int type, String severity, BigDecimal value, int threshold, String rule) {
        return new AlertSignal(type, severity, value, threshold, rule);
    }

    private boolean withinTrendWindow(SmokeData current, SmokeData previous) {
        if (previous == null || current.getTimestamp() == null || previous.getTimestamp() == null) {
            return false;
        }
        Duration gap = Duration.between(previous.getTimestamp(), current.getTimestamp());
        return !gap.isNegative() && !gap.isZero() && gap.compareTo(TREND_WINDOW) <= 0;
    }

    private boolean greaterThan(BigDecimal value, int threshold) {
        return value != null && value.compareTo(BigDecimal.valueOf(threshold)) > 0;
    }

    private boolean atLeast(BigDecimal value, int threshold) {
        return value != null && value.compareTo(BigDecimal.valueOf(threshold)) >= 0;
    }

    private boolean lessThan(BigDecimal value, int threshold) {
        return value != null && value.compareTo(BigDecimal.valueOf(threshold)) < 0;
    }

    private BigDecimal increase(BigDecimal current, BigDecimal previous) {
        return current == null || previous == null ? BigDecimal.ZERO : current.subtract(previous);
    }

    private BigDecimal decrease(BigDecimal previous, BigDecimal current) {
        return current == null || previous == null ? BigDecimal.ZERO : previous.subtract(current);
    }

    private BigDecimal absoluteChange(BigDecimal current, BigDecimal previous) {
        return current == null || previous == null ? BigDecimal.ZERO : current.subtract(previous).abs();
    }

    public record AlertSignal(
            int alertType,
            String severity,
            BigDecimal measuredValue,
            Integer threshold,
            String ruleDescription) {
    }
}
