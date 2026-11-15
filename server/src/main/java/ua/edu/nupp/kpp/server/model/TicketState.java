package ua.edu.nupp.kpp.server.model;

public enum TicketState {
    NEW, ASSIGNED, IN_PROGRESS, CLOSED;

    public boolean canTransitionTo(TicketState next) {
        return next != null && ordinal() + 1 == next.ordinal();
    }
}
