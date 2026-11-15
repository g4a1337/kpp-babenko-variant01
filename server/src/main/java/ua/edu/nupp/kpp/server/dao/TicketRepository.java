package ua.edu.nupp.kpp.server.dao;

import ua.edu.nupp.kpp.server.model.Ticket;

public interface TicketRepository extends Repository<Ticket> {
    boolean transition(long id, String expectedState, String nextState);
    boolean hasAssignedTickets(long userId);
}
