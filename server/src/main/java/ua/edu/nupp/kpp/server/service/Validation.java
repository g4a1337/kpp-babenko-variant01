package ua.edu.nupp.kpp.server.service;

public final class Validation {
    private Validation() {}
    public static String required(String value, String field, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > max)
            throw ApiException.badRequest(field + ": введіть від 1 до " + max + " символів");
        return clean;
    }
    public static String optional(String value, String field, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > max) throw ApiException.badRequest(field + ": максимум " + max + " символів");
        return clean;
    }
    public static long id(Long id, String field) {
        if (id == null || id <= 0) throw ApiException.badRequest(field + ": оберіть запис");
        return id;
    }
    public static double score(double score, String field) {
        if (!Double.isFinite(score) || score < 0 || score > 100)
            throw ApiException.badRequest(field + ": значення має бути від 0 до 100");
        return Math.round(score * 100.0) / 100.0;
    }
}
