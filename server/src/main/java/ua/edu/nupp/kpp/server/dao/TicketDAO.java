package ua.edu.nupp.kpp.server.dao;

import ua.edu.nupp.kpp.server.model.Ticket;
import java.sql.*;

public final class TicketDAO extends JdbcRepository<Ticket> implements TicketRepository {
    public TicketDAO() { super("ticket"); }
    protected Ticket map(ResultSet rs) throws SQLException {
        Long assignee = rs.getObject("assignee_id", Long.class);
        return new Ticket(rs.getLong("id"),rs.getString("title"),rs.getString("description"),rs.getLong("requester_id"),assignee,
                rs.getLong("equipment_id"),rs.getLong("category_id"),rs.getString("state"),rs.getDouble("urgency"),rs.getDouble("impact"),
                rs.getDouble("criticality"),rs.getDouble("priority"),rs.getTimestamp("created_at").toLocalDateTime().toString());
    }
    protected String insertSql() {
        return "INSERT INTO ticket(title,description,requester_id,assignee_id,equipment_id,category_id,urgency,impact,criticality,priority) VALUES(?,?,?,?,?,?,?,?,?,?)";
    }
    protected String updateSql() {
        // State and creation time can never be overwritten by a CRUD update.
        return "UPDATE ticket SET title=?,description=?,requester_id=?,assignee_id=?,equipment_id=?,category_id=?,urgency=?,impact=?,criticality=?,priority=? WHERE id=?";
    }
    protected int fieldCount() { return 10; }
    protected void bind(PreparedStatement s, Ticket v) throws SQLException {
        s.setString(1,v.title()); s.setString(2,v.description()); s.setLong(3,v.requesterId());
        if(v.assigneeId()==null) s.setNull(4, Types.BIGINT); else s.setLong(4,v.assigneeId());
        s.setLong(5,v.equipmentId()); s.setLong(6,v.categoryId()); s.setDouble(7,v.urgency()); s.setDouble(8,v.impact());
        s.setDouble(9,v.criticality()); s.setDouble(10,v.priority());
    }
    @Override public boolean transition(long id, String expectedState, String nextState) {
        try(Connection c=DBUtil.open(); PreparedStatement s=c.prepareStatement("UPDATE ticket SET state=? WHERE id=? AND state=? AND assignee_id IS NOT NULL")) {
            s.setString(1,nextState); s.setLong(2,id); s.setString(3,expectedState); return s.executeUpdate()>0;
        } catch(SQLException ex) { throw new DataAccessException(ex); }
    }
    @Override public boolean hasAssignedTickets(long userId) {
        try(Connection c=DBUtil.open(); PreparedStatement s=c.prepareStatement("SELECT id FROM ticket WHERE assignee_id=? LIMIT 1")) {
            s.setLong(1,userId); try(ResultSet rs=s.executeQuery()) { return rs.next(); }
        } catch(SQLException ex) { throw new DataAccessException(ex); }
    }
}
