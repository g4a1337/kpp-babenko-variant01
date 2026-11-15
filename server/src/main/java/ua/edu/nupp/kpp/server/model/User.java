package ua.edu.nupp.kpp.server.model;

public record User(Long id, String fullName, String email, String department, boolean technician) {}
