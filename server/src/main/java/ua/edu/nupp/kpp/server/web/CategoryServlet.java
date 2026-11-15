package ua.edu.nupp.kpp.server.web;
import ua.edu.nupp.kpp.server.model.Category;
import javax.servlet.annotation.WebServlet;

@WebServlet("/api/categories/*")
public final class CategoryServlet extends CrudServlet<Category> {
    public CategoryServlet() { super(Services.CATEGORY_SERVICE,Category.class); }
}
