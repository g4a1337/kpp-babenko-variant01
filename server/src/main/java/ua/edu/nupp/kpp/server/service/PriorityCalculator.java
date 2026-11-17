package ua.edu.nupp.kpp.server.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriorityCalculator {
    private static final BigDecimal URGENCY_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal IMPACT_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal CRITICALITY_WEIGHT = new BigDecimal("0.25");
    private PriorityCalculator() {}
    public static double calculate(double urgency, double impact, double criticality) {
        urgency = Validation.score(urgency, "Терміновість");
        impact = Validation.score(impact, "Вплив на роботу");
        criticality = Validation.score(criticality, "Критичність обладнання");
        return BigDecimal.valueOf(urgency).multiply(URGENCY_WEIGHT)
                .add(BigDecimal.valueOf(impact).multiply(IMPACT_WEIGHT))
                .add(BigDecimal.valueOf(criticality).multiply(CRITICALITY_WEIGHT))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
