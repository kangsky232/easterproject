package com.smoke.service;

import com.smoke.entity.AlertRecord;
import com.smoke.entity.SmokeData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryAlertEvaluatorTest {

    private final TelemetryAlertEvaluator evaluator = new TelemetryAlertEvaluator();

    @Test
    void evaluatesEveryDangerThresholdFromSingleReading() {
        SmokeData current = reading("301", "61", "19", "16", "91", "101",
                LocalDateTime.of(2026, 8, 27, 16, 0));

        List<TelemetryAlertEvaluator.AlertSignal> signals = evaluator.evaluate(current, null, 100);

        assertEquals(List.of(1, 3, 4, 5, 6, 7),
                signals.stream().map(TelemetryAlertEvaluator.AlertSignal::alertType).toList());
        assertTrue(signals.stream().allMatch(
                signal -> AlertRecord.SEVERITY_DANGER.equals(signal.severity())));
    }

    @Test
    void usesInclusiveWarningBoundariesAndStrictDangerBoundaries() {
        SmokeData current = reading("300", "46", "40", "11", "71", "50",
                LocalDateTime.of(2026, 8, 27, 16, 0));

        List<TelemetryAlertEvaluator.AlertSignal> signals = evaluator.evaluate(current, null, 100);

        assertEquals(List.of(1, 3, 5, 6, 7),
                signals.stream().map(TelemetryAlertEvaluator.AlertSignal::alertType).toList());
        assertTrue(signals.stream().allMatch(
                signal -> AlertRecord.SEVERITY_WARNING.equals(signal.severity())));
    }

    @Test
    void detectsRapidChangesInsideFiveMinuteWindow() {
        SmokeData previous = reading("20", "25", "60", "2", "30", "5",
                LocalDateTime.of(2026, 8, 27, 16, 0));
        SmokeData current = reading("20", "35", "40", "7", "30", "5",
                LocalDateTime.of(2026, 8, 27, 16, 4));

        List<TelemetryAlertEvaluator.AlertSignal> signals = evaluator.evaluate(current, previous, 100);

        assertEquals(List.of(3, 4, 5),
                signals.stream().map(TelemetryAlertEvaluator.AlertSignal::alertType).toList());
    }

    @Test
    void ignoresTrendChangesOutsideFiveMinuteWindow() {
        SmokeData previous = reading("20", "25", "60", "2", "30", "5",
                LocalDateTime.of(2026, 8, 27, 16, 0));
        SmokeData current = reading("20", "35", "40", "7", "30", "5",
                LocalDateTime.of(2026, 8, 27, 16, 6));

        assertTrue(evaluator.evaluate(current, previous, 100).isEmpty());
    }

    private SmokeData reading(
            String smoke,
            String temperature,
            String humidity,
            String current,
            String wireTemperature,
            String co,
            LocalDateTime timestamp) {
        SmokeData reading = new SmokeData();
        reading.setConcentration(new BigDecimal(smoke));
        reading.setTemperature(new BigDecimal(temperature));
        reading.setHumidity(new BigDecimal(humidity));
        reading.setCurrentValue(new BigDecimal(current));
        reading.setWireTemperature(new BigDecimal(wireTemperature));
        reading.setCoValue(new BigDecimal(co));
        reading.setTimestamp(timestamp);
        return reading;
    }
}
