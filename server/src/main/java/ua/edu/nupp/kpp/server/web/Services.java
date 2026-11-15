package ua.edu.nupp.kpp.server.web;

import ua.edu.nupp.kpp.server.dao.*;
import ua.edu.nupp.kpp.server.service.*;

final class Services {
    private static final UserDAO USERS = new UserDAO();
    private static final CategoryDAO CATEGORIES = new CategoryDAO();
    private static final EquipmentDAO EQUIPMENT = new EquipmentDAO();
    private static final TicketDAO TICKETS = new TicketDAO();
    static final UserService USER_SERVICE = new UserService(USERS,TICKETS);
    static final CategoryService CATEGORY_SERVICE = new CategoryService(CATEGORIES);
    static final EquipmentService EQUIPMENT_SERVICE = new EquipmentService(EQUIPMENT);
    static final TicketService TICKET_SERVICE = new TicketService(TICKETS,USERS,EQUIPMENT,CATEGORIES);
    private Services() {}
}
