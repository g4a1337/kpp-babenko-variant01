package ua.edu.nupp.kpp.server.web;
import ua.edu.nupp.kpp.server.model.Ticket;
import ua.edu.nupp.kpp.server.service.ApiException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/api/tickets/*")
public final class TicketServlet extends CrudServlet<Ticket> {
    public TicketServlet() { super(Services.TICKET_SERVICE,Ticket.class); }
    public record TransitionRequest(String state) {}
    @Override protected void route(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path=req.getPathInfo();
        if(path!=null && path.endsWith("/transition")) {
            if(!req.getMethod().equals("POST")) throw new ApiException(405,"Для переходу стану використовуйте POST");
            long id=parseId(path.substring(0,path.length()-"/transition".length()));
            TransitionRequest body=read(req,TransitionRequest.class);
            if(body==null) throw ApiException.badRequest("Порожній запит");
            write(res,200,Services.TICKET_SERVICE.transition(id,body.state()));
        } else super.route(req,res);
    }
}
