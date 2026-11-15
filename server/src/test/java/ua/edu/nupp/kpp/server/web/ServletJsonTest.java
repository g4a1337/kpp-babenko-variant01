package ua.edu.nupp.kpp.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import ua.edu.nupp.kpp.server.model.Equipment;
import ua.edu.nupp.kpp.server.service.ApiException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ServletJsonTest {
    private static HttpServletRequest request(String contentType,String body) {
        ByteArrayInputStream input=new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        ServletInputStream stream=new ServletInputStream() {
            public int read() { return input.read(); }
            public boolean isFinished() { return input.available()==0; }
            public boolean isReady() { return true; }
            public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
        };
        return (HttpServletRequest)Proxy.newProxyInstance(ServletJsonTest.class.getClassLoader(),new Class<?>[]{HttpServletRequest.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getContentType" -> contentType;
                    case "getInputStream" -> stream;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    @Test void jsonLiteralNullIsBadRequest() {
        ApiException error=assertThrows(ApiException.class,()->CrudServlet.read(request("application/json","null"),Equipment.class));
        assertEquals(400,error.status());
    }
    @Test void unsupportedContentTypeIsRejected() {
        ApiException error=assertThrows(ApiException.class,()->CrudServlet.read(request("text/plain","{}"),Equipment.class));
        assertEquals(415,error.status());
    }
    @Test void missingContentTypeIsRejected() {
        assertThrows(ApiException.class,()->CrudServlet.read(request(null,"{}"),Equipment.class));
    }
    @Test void malformedJsonIsRejected() {
        assertThrows(JsonProcessingException.class,()->CrudServlet.read(request("application/json","{"),Equipment.class));
    }
    @Test void trailingJsonDocumentIsRejected() {
        assertThrows(JsonProcessingException.class,()->CrudServlet.read(request("application/json","{} {}"),Equipment.class));
    }
    @Test void explicitNullNumericFactorIsRejected() {
        assertThrows(JsonProcessingException.class,()->CrudServlet.read(request("application/json","{\"criticality\":null}"),Equipment.class));
    }
    @Test void validUtf8JsonIsDecoded() throws IOException {
        Equipment item=CrudServlet.read(request("application/json; charset=UTF-8","{\"inventoryNumber\":\"PC-9\",\"name\":\"Комп’ютер\",\"location\":\"Кабінет\",\"criticality\":75}"),Equipment.class);
        assertEquals("Комп’ютер",item.name()); assertEquals(75,item.criticality());
    }
}
