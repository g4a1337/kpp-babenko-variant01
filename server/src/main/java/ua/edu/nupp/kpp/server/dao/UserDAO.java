package ua.edu.nupp.kpp.server.dao;

import ua.edu.nupp.kpp.server.model.User;
import java.sql.*;

public final class UserDAO extends JdbcRepository<User> {
    public UserDAO() { super("app_user"); }
    protected User map(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"), rs.getString("department"), rs.getBoolean("technician"));
    }
    protected String insertSql() { return "INSERT INTO app_user(full_name,email,department,technician) VALUES(?,?,?,?)"; }
    protected String updateSql() { return "UPDATE app_user SET full_name=?,email=?,department=?,technician=? WHERE id=?"; }
    protected int fieldCount() { return 4; }
    protected void bind(PreparedStatement s, User v) throws SQLException {
        s.setString(1,v.fullName()); s.setString(2,v.email()); s.setString(3,v.department()); s.setBoolean(4,v.technician());
    }
}
