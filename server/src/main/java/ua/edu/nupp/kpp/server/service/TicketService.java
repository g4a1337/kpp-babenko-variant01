package ua.edu.nupp.kpp.server.service;

import ua.edu.nupp.kpp.server.dao.*;
import ua.edu.nupp.kpp.server.model.*;

public final class TicketService extends CrudService<Ticket> {
    private final TicketRepository tickets;
    private final Repository<User> users;
    private final Repository<Equipment> equipment;
    private final Repository<Category> categories;
    public TicketService(TicketRepository tickets, Repository<User> users, Repository<Equipment> equipment, Repository<Category> categories) {
        super(tickets); this.tickets=tickets; this.users=users; this.equipment=equipment; this.categories=categories;
    }
    protected Ticket normalize(Ticket v, Long id) {
        String title=Validation.required(v.title(),"Тема заявки",200);
        String description=Validation.required(v.description(),"Опис заявки",4000);
        long requesterId=Validation.id(v.requesterId(),"Заявник"), equipmentId=Validation.id(v.equipmentId(),"Обладнання"), categoryId=Validation.id(v.categoryId(),"Категорія");
        if(users.findById(requesterId).isEmpty()) throw ApiException.badRequest("Заявника не знайдено");
        Equipment item=equipment.findById(equipmentId).orElseThrow(()->ApiException.badRequest("Обладнання не знайдено"));
        if(categories.findById(categoryId).isEmpty()) throw ApiException.badRequest("Категорію не знайдено");
        validateAssignee(v.assigneeId());
        Ticket old=id==null?null:get(id);
        String state=old==null?TicketState.NEW.name():old.state();
        if(v.state()!=null && !v.state().equals(state)) throw ApiException.badRequest("Для зміни стану використовуйте окрему операцію переходу");
        if(!state.equals("NEW") && v.assigneeId()==null) throw ApiException.badRequest("Для призначеної заявки потрібен виконавець");
        double urgency=Validation.score(v.urgency(),"Терміновість");
        double impact=Validation.score(v.impact(),"Вплив на роботу");
        double priority=PriorityCalculator.calculate(urgency,impact,item.criticality());
        // F3 is a snapshot: equipment edits do not silently rewrite existing ticket scores.
        return new Ticket(id,title,description,requesterId,v.assigneeId(),equipmentId,categoryId,state,
                urgency,impact,item.criticality(),priority,old==null?null:old.createdAt());
    }
    private void validateAssignee(Long id) {
        if(id!=null) {
            Validation.id(id,"Виконавець");
            User assignee=users.findById(id).orElseThrow(()->ApiException.badRequest("Виконавця не знайдено"));
            if(!assignee.technician()) throw ApiException.badRequest("Виконавцем може бути лише фахівець ІТ-підтримки");
        }
    }
    public Ticket transition(long id, String next) {
        Ticket ticket=get(id); TicketState current=TicketState.valueOf(ticket.state());
        TicketState target;
        try { target=TicketState.valueOf(next==null?"":next); }
        catch(IllegalArgumentException ex) { throw ApiException.badRequest("Невідомий стан заявки"); }
        if(!current.canTransitionTo(target)) throw ApiException.badRequest("Недозволений перехід: "+current+" → "+target);
        if(ticket.assigneeId()==null) throw ApiException.badRequest("Спочатку оберіть і збережіть виконавця заявки");
        validateAssignee(ticket.assigneeId());
        if(!tickets.transition(id,current.name(),target.name())) throw new ApiException(409,"Заявку вже змінено. Оновіть дані та повторіть дію");
        log.info("Transition ticket="+id+" "+current+" -> "+target);
        return get(id);
    }
}
