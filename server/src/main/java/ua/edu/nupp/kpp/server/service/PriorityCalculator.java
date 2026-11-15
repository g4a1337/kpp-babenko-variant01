package ua.edu.nupp.kpp.server.service;

public final class PriorityCalculator {
    private PriorityCalculator() {}
    public static double calculate(double urgency, double impact, double criticality) {
        urgency = Validation.score(urgency, "Терміновість");
        impact = Validation.score(impact, "Вплив на роботу");
        criticality = Validation.score(criticality, "Критичність обладнання");
        return Math.round((0.40 * urgency + 0.35 * impact + 0.25 * criticality) * 100.0) / 100.0;
    }
}
