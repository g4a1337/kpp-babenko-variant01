package ua.edu.nupp.kpp.server.service;

import ua.edu.nupp.kpp.server.dao.Repository;
import ua.edu.nupp.kpp.server.model.Category;

public final class CategoryService extends CrudService<Category> {
    public CategoryService(Repository<Category> repository) { super(repository); }
    protected Category normalize(Category v, Long id) {
        return new Category(id,Validation.required(v.name(),"Назва категорії",100),Validation.optional(v.description(),"Опис",1000));
    }
}
