package ua.edu.nupp.kpp.server.dao;

import ua.edu.nupp.kpp.server.model.Equipment;
import java.sql.*;

public final class EquipmentDAO extends JdbcRepository<Equipment> {
    public EquipmentDAO() { super("equipment"); }
    protected Equipment map(ResultSet rs) throws SQLException {
        return new Equipment(rs.getLong("id"),rs.getString("inventory_number"),rs.getString("name"),rs.getString("location"),rs.getDouble("criticality"));
    }
    protected String insertSql() { return "INSERT INTO equipment(inventory_number,name,location,criticality) VALUES(?,?,?,?)"; }
    protected String updateSql() { return "UPDATE equipment SET inventory_number=?,name=?,location=?,criticality=? WHERE id=?"; }
    protected int fieldCount() { return 4; }
    protected void bind(PreparedStatement s, Equipment v) throws SQLException {
        s.setString(1,v.inventoryNumber()); s.setString(2,v.name()); s.setString(3,v.location()); s.setDouble(4,v.criticality());
    }
}
