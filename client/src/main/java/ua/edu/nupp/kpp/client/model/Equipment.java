package ua.edu.nupp.kpp.client.model;

public record Equipment(Long id, String inventoryNumber, String name, String location, double criticality) {
    @Override public String toString() { return inventoryNumber + " · " + name; }
}
