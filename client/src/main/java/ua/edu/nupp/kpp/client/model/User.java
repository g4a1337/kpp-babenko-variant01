package ua.edu.nupp.kpp.client.model;

public record User(Long id, String fullName, String email, String department, boolean technician) {
    @Override public String toString() { return fullName + (technician ? " · ІТ-фахівець" : ""); }
}
