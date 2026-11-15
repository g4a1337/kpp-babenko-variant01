package ua.edu.nupp.kpp.server.service;

import ua.edu.nupp.kpp.server.dao.Repository;
import ua.edu.nupp.kpp.server.model.Equipment;

public final class EquipmentService extends CrudService<Equipment> {
    public EquipmentService(Repository<Equipment> repository) { super(repository); }
    protected Equipment normalize(Equipment v, Long id) {
        return new Equipment(id,Validation.required(v.inventoryNumber(),"Інвентарний номер",80),
                Validation.required(v.name(),"Назва обладнання",200),Validation.required(v.location(),"Розташування",200),
                Validation.score(v.criticality(),"Критичність обладнання"));
    }
}
