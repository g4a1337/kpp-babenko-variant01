package ua.edu.nupp.kpp.server.service;

import org.junit.jupiter.api.*;
import ua.edu.nupp.kpp.server.dao.*;
import ua.edu.nupp.kpp.server.model.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Unit tests use in-memory repositories and never need a database or graphical desktop. */
class CourseworkServiceTest {
    private MemoryRepository<User> users;
    private MemoryRepository<Category> categories;
    private MemoryRepository<Equipment> equipment;
    private MemoryTickets tickets;
    private TicketService service;
    private EquipmentService equipmentService;
    private UserService userService;
    private CategoryService categoryService;

    @BeforeEach void setup() {
        users=new MemoryRepository<>(); categories=new MemoryRepository<>(); equipment=new MemoryRepository<>(); tickets=new MemoryTickets();
        users.insert(new User(null,"Заявник","requester@example.edu","Лабораторія",false));
        users.insert(new User(null,"Фахівець","technician@example.edu","ІТ",true));
        categories.insert(new Category(null,"ПЗ","Програмне забезпечення"));
        equipment.insert(new Equipment(null,"PC-001","Робоча станція","Аудиторія 201",80));
        service=new TicketService(tickets,users,equipment,categories);
        equipmentService=new EquipmentService(equipment); userService=new UserService(users,tickets); categoryService=new CategoryService(categories);
    }
    private Ticket input() { return ticket(null,null,50,75); }
    private Ticket ticket(Long id,String state,double urgency,double impact) {
        return new Ticket(id,"Не працює IDE","Опис несправності",1L,null,1L,1L,state,urgency,impact,999,999,"fake date");
    }
    private Ticket copy(Ticket t,Long assignee,String state) {
        return new Ticket(t.id(),t.title(),t.description(),t.requesterId(),assignee,t.equipmentId(),t.categoryId(),state,t.urgency(),t.impact(),t.criticality(),t.priority(),t.createdAt());
    }
    private Ticket assign(Ticket created) { return service.update(created.id(),copy(created,2L,created.state())); }

    @Test void priorityWeightedExample() { assertEquals(66.25,PriorityCalculator.calculate(50,75,80),0.0001); }
    @Test void priorityZeroBoundary() { assertEquals(0,PriorityCalculator.calculate(0,0,0)); }
    @Test void priorityMaximumBoundary() { assertEquals(100,PriorityCalculator.calculate(100,100,100)); }
    @Test void priorityUsesDeclaredWeights() {
        assertEquals(40,PriorityCalculator.calculate(100,0,0));
        assertEquals(35,PriorityCalculator.calculate(0,100,0));
        assertEquals(25,PriorityCalculator.calculate(0,0,100));
    }
    @Test void priorityRoundsToTwoDecimalPlaces() { assertEquals(33.33,PriorityCalculator.calculate(33.333,33.333,33.333)); }
    @Test void priorityRoundsHalfCentUpUsingDecimalWeights() {
        assertEquals(0.15,PriorityCalculator.calculate(0,0,0.58));
        assertEquals(0.04,PriorityCalculator.calculate(0,0.1,0));
        assertEquals(0.06,PriorityCalculator.calculate(0,0.05,0.15));
    }
    @Test void scoreRoundsDecimalHalvesUp() {
        assertEquals(1.01,Validation.score(1.005,"Фактор"));
        assertEquals(10.08,Validation.score(10.075,"Фактор"));
        assertEquals(0.15,Validation.score(0.145,"Фактор"));
        assertEquals(100.00,Validation.score(99.995,"Фактор"));
    }
    @Test void priorityUsesNormalizedFactorsBeforeWeighting() {
        assertEquals(1.01,PriorityCalculator.calculate(1.005,1.005,1.005));
        assertEquals(0.01,PriorityCalculator.calculate(0.005,0.005,0));
    }
    @Test void rejectsNegativeFactor() { assertThrows(ApiException.class,()->PriorityCalculator.calculate(-0.01,0,0)); }
    @Test void rejectsFactorAboveHundred() { assertThrows(ApiException.class,()->PriorityCalculator.calculate(0,100.01,0)); }
    @Test void rejectsNaNFactor() { assertThrows(ApiException.class,()->PriorityCalculator.calculate(0,0,Double.NaN)); }
    @Test void rejectsInfiniteFactor() { assertThrows(ApiException.class,()->PriorityCalculator.calculate(Double.POSITIVE_INFINITY,0,0)); }
    @Test void newTicketHasInitialStateAndServerCalculatedScore() {
        Ticket created=service.create(input());
        assertEquals("NEW",created.state()); assertEquals(80,created.criticality()); assertEquals(66.25,created.priority());
        assertNotNull(created.id()); assertNotEquals("fake date",created.createdAt());
    }
    @Test void cannotCreateTicketInLaterState() { assertThrows(ApiException.class,()->service.create(ticket(null,"CLOSED",1,1))); }
    @Test void fullLifecycleWorksInOrder() {
        Ticket t=assign(service.create(input()));
        assertEquals("ASSIGNED",service.transition(t.id(),"ASSIGNED").state());
        assertEquals("IN_PROGRESS",service.transition(t.id(),"IN_PROGRESS").state());
        assertEquals("CLOSED",service.transition(t.id(),"CLOSED").state());
    }
    @Test void skippingStateIsRejected() {
        Ticket t=assign(service.create(input()));
        assertThrows(ApiException.class,()->service.transition(t.id(),"CLOSED"));
        assertEquals("NEW",service.get(t.id()).state());
    }
    @Test void reverseTransitionIsRejected() {
        Ticket t=assign(service.create(input())); service.transition(t.id(),"ASSIGNED");
        assertThrows(ApiException.class,()->service.transition(t.id(),"NEW"));
    }
    @Test void repeatingSameStateIsRejected() {
        Ticket t=assign(service.create(input())); service.transition(t.id(),"ASSIGNED");
        assertThrows(ApiException.class,()->service.transition(t.id(),"ASSIGNED"));
    }
    @Test void closedStateHasNoOutgoingTransition() {
        assertFalse(TicketState.CLOSED.canTransitionTo(TicketState.NEW));
        assertFalse(TicketState.CLOSED.canTransitionTo(null));
    }
    @Test void assigningRequiresSavedTechnician() {
        Ticket t=service.create(input());
        assertThrows(ApiException.class,()->service.transition(t.id(),"ASSIGNED"));
        assertThrows(ApiException.class,()->service.update(t.id(),copy(t,1L,"NEW")));
    }
    @Test void unknownAssigneeIsRejected() {
        Ticket t=service.create(input());
        assertThrows(ApiException.class,()->service.update(t.id(),copy(t,999L,"NEW")));
    }
    @Test void stateCannotBeChangedViaCrudUpdate() {
        Ticket t=assign(service.create(input()));
        assertThrows(ApiException.class,()->service.update(t.id(),copy(t,2L,"IN_PROGRESS")));
        assertEquals("NEW",service.get(t.id()).state());
    }
    @Test void cannotRemoveAssigneeAfterAssignment() {
        Ticket t=assign(service.create(input())); t=service.transition(t.id(),"ASSIGNED");
        final Ticket assigned=t;
        assertThrows(ApiException.class,()->service.update(assigned.id(),copy(assigned,null,"ASSIGNED")));
    }
    @Test void unknownStateIsRejected() {
        Ticket t=assign(service.create(input()));
        assertThrows(ApiException.class,()->service.transition(t.id(),"INVALID"));
        assertThrows(ApiException.class,()->service.transition(t.id(),null));
    }
    @Test void blankTicketTitleIsRejected() {
        Ticket v=new Ticket(null,"  ","Опис",1L,null,1L,1L,null,50,50,0,0,null);
        assertThrows(ApiException.class,()->service.create(v));
    }
    @Test void oversizedDescriptionIsRejected() {
        Ticket v=new Ticket(null,"Тема","x".repeat(4001),1L,null,1L,1L,null,50,50,0,0,null);
        assertThrows(ApiException.class,()->service.create(v));
    }
    @Test void missingEquipmentIsRejected() {
        Ticket v=new Ticket(null,"Тема","Опис",1L,null,999L,1L,null,50,50,0,0,null);
        assertThrows(ApiException.class,()->service.create(v));
    }
    @Test void missingRequesterIsRejected() {
        Ticket v=new Ticket(null,"Тема","Опис",999L,null,1L,1L,null,50,50,0,0,null);
        assertThrows(ApiException.class,()->service.create(v));
    }
    @Test void missingCategoryIsRejected() {
        Ticket v=new Ticket(null,"Тема","Опис",1L,null,1L,999L,null,50,50,0,0,null);
        assertThrows(ApiException.class,()->service.create(v));
    }
    @Test void equipmentChangeDoesNotRewriteScoreUntilTicketEdit() {
        Ticket t=service.create(input());
        equipmentService.update(1,new Equipment(1L,"PC-001","Станція","201",20));
        assertEquals(80,service.get(t.id()).criticality());
        Ticket edited=service.update(t.id(),t);
        assertEquals(20,edited.criticality()); assertEquals(51.25,edited.priority());
    }
    @Test void equipmentFieldsAreTrimmedAndStored() {
        Equipment v=equipmentService.create(new Equipment(null,"  PC-002  "," Назва "," 202 ",25));
        assertEquals("PC-002",v.inventoryNumber()); assertEquals("Назва",v.name()); assertEquals("202",v.location());
    }
    @Test void equipmentRejectsInvalidCriticality() {
        assertThrows(ApiException.class,()->equipmentService.create(new Equipment(null,"PC-002","Назва","202",101)));
    }
    @Test void equipmentRequiresInventoryNumber() {
        assertThrows(ApiException.class,()->equipmentService.create(new Equipment(null," ","Назва","202",50)));
    }
    @Test void equipmentRejectsTooLongLocation() {
        assertThrows(ApiException.class,()->equipmentService.create(new Equipment(null,"PC-002","Назва","x".repeat(201),50)));
    }
    @Test void userEmailIsNormalized() {
        User user=userService.create(new User(null," Тест "," TEST@EXAMPLE.EDU "," ІТ ",false));
        assertEquals("test@example.edu",user.email()); assertEquals("Тест",user.fullName());
    }
    @Test void invalidEmailIsRejected() {
        assertThrows(ApiException.class,()->userService.create(new User(null,"Тест","broken-email","ІТ",false)));
    }
    @Test void assignedTechnicianRoleCannotBeRemoved() {
        assign(service.create(input()));
        ApiException ex=assertThrows(ApiException.class,()->userService.update(2,new User(2L,"Фахівець","tech@example.edu","ІТ",false)));
        assertEquals(409,ex.status());
    }
    @Test void blankCategoryIsRejected() { assertThrows(ApiException.class,()->categoryService.create(new Category(null," ","Опис"))); }
    @Test void categoryDescriptionIsOptional() { assertEquals("",categoryService.create(new Category(null,"Мережа",null)).description()); }
    @Test void deleteRemovesTicket() {
        Ticket t=service.create(input()); service.delete(t.id());
        assertEquals(404,assertThrows(ApiException.class,()->service.get(t.id())).status());
    }
    @Test void updatingMissingRecordReturnsNotFound() { assertEquals(404,assertThrows(ApiException.class,()->service.update(999,input())).status()); }
    @Test void invalidIdIsRejected() { assertEquals(400,assertThrows(ApiException.class,()->service.get(-1)).status()); }
    @Test void nullCreateBodyIsRejected() { assertThrows(ApiException.class,()->service.create(null)); }
    @Test void concurrentStateChangeReturnsConflict() {
        Ticket t=assign(service.create(input())); tickets.failTransition=true;
        assertEquals(409,assertThrows(ApiException.class,()->service.transition(t.id(),"ASSIGNED")).status());
    }

    private static class MemoryRepository<T> implements Repository<T> {
        final Map<Long,T> values=new LinkedHashMap<>();
        long sequence;
        public List<T> findAll() { return new ArrayList<>(values.values()); }
        public Optional<T> findById(long id) { return Optional.ofNullable(values.get(id)); }
        public long insert(T value) { long id=++sequence; values.put(id,withId(value,id)); return id; }
        public boolean update(long id,T value) { if(!values.containsKey(id)) return false; values.put(id,withId(value,id)); return true; }
        public boolean delete(long id) { return values.remove(id)!=null; }
        @SuppressWarnings("unchecked") private T withId(T v,long id) {
            return (T)switch(v) {
                case User u -> new User(id,u.fullName(),u.email(),u.department(),u.technician());
                case Category c -> new Category(id,c.name(),c.description());
                case Equipment e -> new Equipment(id,e.inventoryNumber(),e.name(),e.location(),e.criticality());
                case Ticket t -> new Ticket(id,t.title(),t.description(),t.requesterId(),t.assigneeId(),t.equipmentId(),t.categoryId(),t.state(),t.urgency(),t.impact(),t.criticality(),t.priority(),"2026-11-15T10:00:00");
                default -> throw new IllegalArgumentException();
            };
        }
    }
    private static class MemoryTickets extends MemoryRepository<Ticket> implements TicketRepository {
        boolean failTransition;
        public boolean hasAssignedTickets(long id) { return values.values().stream().anyMatch(t->Objects.equals(t.assigneeId(),id)); }
        public boolean transition(long id,String expected,String next) {
            Ticket t=values.get(id);
            if(failTransition || t==null || !t.state().equals(expected)) return false;
            values.put(id,new Ticket(id,t.title(),t.description(),t.requesterId(),t.assigneeId(),t.equipmentId(),t.categoryId(),next,t.urgency(),t.impact(),t.criticality(),t.priority(),t.createdAt()));
            return true;
        }
    }
}
