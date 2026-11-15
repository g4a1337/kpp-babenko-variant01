package ua.edu.nupp.kpp.server.dao;

public class DataAccessException extends RuntimeException {
    private final boolean conflict;
    public DataAccessException(java.sql.SQLException cause) {
        super("Database operation failed", cause);
        conflict = cause.getSQLState() != null && cause.getSQLState().startsWith("23");
    }
    public boolean isConflict() { return conflict; }
}
