package ua.edu.nupp.kpp.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBUtil {
    private DBUtil() {}
    public static Connection open() throws SQLException {
        // Explicit registration also works in Servlet containers with isolated class loaders.
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException ex) { throw new SQLException("MySQL JDBC driver is missing", ex); }
        return DriverManager.getConnection(setting("DB_URL", "jdbc:mysql://127.0.0.1:3307/it_support?characterEncoding=UTF-8&serverTimezone=UTC"),
                setting("DB_USER", "coursework"), setting("DB_PASSWORD", ""));
    }
    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }
}
