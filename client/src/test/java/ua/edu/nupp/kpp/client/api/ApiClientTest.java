package ua.edu.nupp.kpp.client.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import ua.edu.nupp.kpp.client.model.Equipment;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ApiClientTest {
    private HttpServer server;
    private ApiClient api;
    private final AtomicReference<String> method = new AtomicReference<>(), body = new AtomicReference<>(), path = new AtomicReference<>();
    private int status = 200;
    private String response = "[]";
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api", exchange -> {
            method.set(exchange.getRequestMethod()); path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        api = new ApiClient("http://localhost:" + server.getAddress().getPort() + "/api/");
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void readsUnicodeJsonList() {
        response = "[{\"id\":1,\"inventoryNumber\":\"PC-1\",\"name\":\"Ноутбук\",\"location\":\"Офіс\",\"criticality\":80}]";
        var values = api.equipment(); assertEquals("GET", method.get()); assertEquals("Ноутбук", values.getFirst().name());
    }
    @Test void createSendsJsonUsingPost() {
        api.save("equipment", null, new Equipment(null, "PC-1", "ПК", "Офіс", 80));
        assertEquals("POST", method.get()); assertEquals("/api/equipment", path.get()); assertTrue(body.get().contains("\"criticality\":80.0"));
    }
    @Test void editUsesPutAndIdentifier() {
        api.save("equipment", 7L, new Equipment(7L, "PC-1", "ПК", "Офіс", 80));
        assertEquals("PUT", method.get()); assertEquals("/api/equipment/7", path.get());
    }
    @Test void deleteDoesNotUseGet() { api.delete("equipment", 9); assertEquals("DELETE", method.get()); assertEquals("/api/equipment/9", path.get()); }
    @Test void transitionUsesSeparateOperation() {
        api.transition(8, "ASSIGNED"); assertEquals("POST", method.get()); assertEquals("/api/tickets/8/transition", path.get()); assertEquals("{\"state\":\"ASSIGNED\"}", body.get());
    }
    @Test void exposesMeaningfulValidationError() {
        status = 400; response = "{\"message\":\"Критичність поза діапазоном\"}";
        var error = assertThrows(ApiException.class, api::equipment); assertEquals(400, error.statusCode()); assertEquals("Критичність поза діапазоном", error.getMessage());
    }
    @Test void reportsInvalidJson() { response = "not-json"; assertThrows(ApiException.class, api::equipment); }
}
