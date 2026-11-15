package ua.edu.nupp.kpp.client.model;

public record Ticket(Long id, String title, String description, Long requesterId, Long assigneeId,
                     Long equipmentId, Long categoryId, String state, double urgency, double impact,
                     double criticality, double priority, String createdAt) { }
