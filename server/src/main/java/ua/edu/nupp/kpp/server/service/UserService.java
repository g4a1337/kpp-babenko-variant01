package ua.edu.nupp.kpp.server.service;

import ua.edu.nupp.kpp.server.dao.*;
import ua.edu.nupp.kpp.server.model.User;
import java.util.Locale;

public final class UserService extends CrudService<User> {
    private final TicketRepository tickets;
    public UserService(Repository<User> users, TicketRepository tickets) { super(users); this.tickets=tickets; }
    protected User normalize(User v, Long id) {
        String name=Validation.required(v.fullName(),"ПІБ",120);
        String email=Validation.required(v.email(),"Email",190).toLowerCase(Locale.ROOT);
        if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw ApiException.badRequest("Некоректна адреса email");
        String department=Validation.required(v.department(),"Підрозділ",120);
        if(id!=null && !v.technician() && tickets.hasAssignedTickets(id))
            throw new ApiException(409,"Користувач є виконавцем заявок; спочатку змініть виконавця цих заявок");
        return new User(id,name,email,department,v.technician());
    }
}
