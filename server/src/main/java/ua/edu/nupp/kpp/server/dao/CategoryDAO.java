package ua.edu.nupp.kpp.server.dao;

import ua.edu.nupp.kpp.server.model.Category;
import java.sql.*;

public final class CategoryDAO extends JdbcRepository<Category> {
    public CategoryDAO() { super("category"); }
    protected Category map(ResultSet rs) throws SQLException { return new Category(rs.getLong("id"),rs.getString("name"),rs.getString("description")); }
    protected String insertSql() { return "INSERT INTO category(name,description) VALUES(?,?)"; }
    protected String updateSql() { return "UPDATE category SET name=?,description=? WHERE id=?"; }
    protected int fieldCount() { return 2; }
    protected void bind(PreparedStatement s, Category v) throws SQLException { s.setString(1,v.name()); s.setString(2,v.description()); }
}
