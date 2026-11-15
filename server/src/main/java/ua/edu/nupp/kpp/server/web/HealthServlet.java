package ua.edu.nupp.kpp.server.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import ua.edu.nupp.kpp.server.dao.DBUtil;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.Map;
import java.util.logging.*;

@WebServlet("/api/health")
public final class HealthServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(HealthServlet.class.getName());
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws IOException {
        res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
        boolean ready=false;
        try(Connection c=DBUtil.open(); PreparedStatement s=c.prepareStatement("SELECT 1"); ResultSet rs=s.executeQuery()) { ready=rs.next(); }
        catch(SQLException ex) { LOG.log(Level.WARNING,"Health check: database unavailable",ex); }
        res.setStatus(ready?200:503);
        new ObjectMapper().writeValue(res.getOutputStream(),Map.of("status",ready?"UP":"DOWN","database",ready?"UP":"DOWN","variant",1));
    }
}
