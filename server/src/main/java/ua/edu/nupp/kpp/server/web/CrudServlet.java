package ua.edu.nupp.kpp.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import ua.edu.nupp.kpp.server.dao.DataAccessException;
import ua.edu.nupp.kpp.server.service.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import java.util.logging.*;

public abstract class CrudServlet<T> extends HttpServlet {
    protected static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Logger LOG = Logger.getLogger(CrudServlet.class.getName());
    private final CrudService<T> service;
    private final Class<T> type;
    protected CrudServlet(CrudService<T> service, Class<T> type) { this.service=service; this.type=type; }
    @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        response.setHeader("Cache-Control","no-store");
        try { route(request,response); }
        catch(ApiException ex) { error(response,ex.status(),ex.getMessage()); }
        catch(JsonProcessingException ex) { error(response,400,"Некоректний JSON або тип даних"); }
        catch(DataAccessException ex) {
            if(ex.isConflict()) {
                LOG.log(Level.WARNING,"Data constraint prevented operation",ex);
                error(response,409,"Конфлікт даних: значення має бути унікальним, або запис пов’язаний з іншими даними");
            } else {
                LOG.log(Level.SEVERE,"Database operation failed",ex);
                error(response,503,"База даних тимчасово недоступна. Перевірте запуск MySQL та конфігурацію сервера");
            }
        }
        catch(Exception ex) { LOG.log(Level.SEVERE,"Request failed",ex); error(response,500,"Внутрішня помилка сервера"); }
    }
    protected void route(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path=req.getPathInfo(); boolean collection=path==null || path.equals("/");
        Long id=collection?null:parseId(path);
        switch(req.getMethod()) {
            case "GET" -> write(res,200,collection?service.list():service.get(id));
            case "POST" -> {
                if(!collection) throw new ApiException(405,"Створення можливе лише для колекції");
                T value=service.create(read(req,type)); write(res,201,value);
            }
            case "PUT" -> { if(collection) throw ApiException.badRequest("Потрібен ID запису"); write(res,200,service.update(id,read(req,type))); }
            case "DELETE" -> { if(collection) throw ApiException.badRequest("Потрібен ID запису"); service.delete(id); res.setStatus(204); }
            default -> throw new ApiException(405,"Метод не підтримується");
        }
    }
    protected static long parseId(String path) {
        if(!path.matches("/[1-9][0-9]*")) throw new ApiException(404,"Невідома адреса запиту");
        try { return Long.parseLong(path.substring(1)); }
        catch(NumberFormatException ex) { throw ApiException.badRequest("Некоректний ID"); }
    }
    protected static <R> R read(HttpServletRequest req, Class<R> type) throws IOException {
        if(req.getContentType()==null || !req.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
            throw new ApiException(415,"Очікується Content-Type application/json");
        R value=JSON.readValue(req.getInputStream(),type);
        if(value==null) throw ApiException.badRequest("Порожній JSON-запит");
        return value;
    }
    protected static void write(HttpServletResponse res, int status, Object data) throws IOException { res.setStatus(status); JSON.writeValue(res.getOutputStream(),data); }
    private static void error(HttpServletResponse res, int status, String message) throws IOException {
        if(!res.isCommitted()) write(res,status,Map.of("message",message));
    }
}
