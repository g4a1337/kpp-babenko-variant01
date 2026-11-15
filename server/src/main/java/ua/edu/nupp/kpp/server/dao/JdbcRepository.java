package ua.edu.nupp.kpp.server.dao;

import java.sql.*;
import java.util.*;

/** Shared JDBC mechanics; SQL identifiers below are developer constants, never user input. */
public abstract class JdbcRepository<T> implements Repository<T> {
    private final String table;
    protected JdbcRepository(String table) { this.table = table; }
    protected abstract T map(ResultSet rs) throws SQLException;
    protected abstract String insertSql();
    protected abstract String updateSql();
    protected abstract void bind(PreparedStatement statement, T value) throws SQLException;
    protected abstract int fieldCount();
    @Override public List<T> findAll() {
        try (Connection c = DBUtil.open(); PreparedStatement s = c.prepareStatement("SELECT * FROM " + table + " ORDER BY id"); ResultSet rs = s.executeQuery()) {
            List<T> values = new ArrayList<>();
            while (rs.next()) values.add(map(rs));
            return values;
        } catch (SQLException ex) { throw new DataAccessException(ex); }
    }
    @Override public Optional<T> findById(long id) {
        try (Connection c = DBUtil.open(); PreparedStatement s = c.prepareStatement("SELECT * FROM " + table + " WHERE id=?")) {
            s.setLong(1, id);
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        } catch (SQLException ex) { throw new DataAccessException(ex); }
    }
    @Override public long insert(T value) {
        try (Connection c = DBUtil.open(); PreparedStatement s = c.prepareStatement(insertSql(), Statement.RETURN_GENERATED_KEYS)) {
            bind(s, value); s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing generated id");
                return keys.getLong(1);
            }
        } catch (SQLException ex) { throw new DataAccessException(ex); }
    }
    @Override public boolean update(long id, T value) {
        try (Connection c = DBUtil.open(); PreparedStatement s = c.prepareStatement(updateSql())) {
            bind(s, value); s.setLong(fieldCount() + 1, id); return s.executeUpdate() > 0;
        } catch (SQLException ex) { throw new DataAccessException(ex); }
    }
    @Override public boolean delete(long id) {
        try (Connection c = DBUtil.open(); PreparedStatement s = c.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
            s.setLong(1, id); return s.executeUpdate() > 0;
        } catch (SQLException ex) { throw new DataAccessException(ex); }
    }
}
