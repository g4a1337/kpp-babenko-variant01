package ua.edu.nupp.kpp.server.service;

import ua.edu.nupp.kpp.server.dao.Repository;
import java.util.List;
import java.util.logging.Logger;

public abstract class CrudService<T> {
    protected final Repository<T> repository;
    protected final Logger log = Logger.getLogger(getClass().getName());
    protected CrudService(Repository<T> repository) { this.repository=repository; }
    protected abstract T normalize(T value, Long id);
    public List<T> list() { return repository.findAll(); }
    public T get(long id) { Validation.id(id,"ID"); return repository.findById(id).orElseThrow(ApiException::notFound); }
    public T create(T value) {
        if(value==null) throw ApiException.badRequest("Порожній запит");
        T valid=normalize(value,null); long id=repository.insert(valid); log.info("Created id="+id); return get(id);
    }
    public T update(long id, T value) {
        get(id);
        if(value==null) throw ApiException.badRequest("Порожній запит");
        T valid=normalize(value,id);
        if(!repository.update(id,valid)) throw ApiException.notFound();
        log.info("Updated id="+id); return get(id);
    }
    public void delete(long id) {
        Validation.id(id,"ID");
        if(!repository.delete(id)) throw ApiException.notFound();
        log.info("Deleted id="+id);
    }
}
