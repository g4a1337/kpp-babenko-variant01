package ua.edu.nupp.kpp.server.web;
import ua.edu.nupp.kpp.server.model.Equipment;
import javax.servlet.annotation.WebServlet;

@WebServlet("/api/equipment/*")
public final class EquipmentServlet extends CrudServlet<Equipment> {
    public EquipmentServlet() { super(Services.EQUIPMENT_SERVICE,Equipment.class); }
}
