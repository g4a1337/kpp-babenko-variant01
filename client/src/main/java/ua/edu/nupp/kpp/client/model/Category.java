package ua.edu.nupp.kpp.client.model;

public record Category(Long id, String name, String description) {
    @Override public String toString() { return name; }
}
