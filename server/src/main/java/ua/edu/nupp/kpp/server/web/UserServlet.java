package ua.edu.nupp.kpp.server.web;
import ua.edu.nupp.kpp.server.model.User;
import javax.servlet.annotation.WebServlet;

@WebServlet("/api/users/*")
public final class UserServlet extends CrudServlet<User> {
    public UserServlet() { super(Services.USER_SERVICE,User.class); }
}
