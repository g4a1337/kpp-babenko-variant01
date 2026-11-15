package ua.edu.nupp.kpp.client.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import ua.edu.nupp.kpp.client.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** HTTP/JSON transport. All calls are made by background tasks, never from the FX thread. */
public final class ApiClient {
    private static final Logger LOG = Logger.getLogger(ApiClient.class.getName());
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiClient() {
        this(System.getProperty("api.baseUrl", System.getenv().getOrDefault("API_BASE_URL",
                "http://localhost:8080/it-support-server/api")));
    }
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        URI uri = URI.create(this.baseUrl);
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("Некоректна адреса API");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
    public String baseUrl() { return baseUrl; }
    public List<User> users() { return list("users", User.class); }
    public List<Category> categories() { return list("categories", Category.class); }
    public List<Equipment> equipment() { return list("equipment", Equipment.class); }
    public List<Ticket> tickets() { return list("tickets", Ticket.class); }
    public void save(String resource, Long id, Object value) {
        send(id == null ? "POST" : "PUT", resource + (id == null ? "" : "/" + id), value);
    }
    public void delete(String resource, long id) { send("DELETE", resource + "/" + id, null); }
    public void transition(long id, String state) {
        send("POST", "tickets/" + id + "/transition", Map.of("state", state));
    }
    private <T> List<T> list(String resource, Class<T> type) {
        String json = send("GET", resource, null);
        try { return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, type)); }
        catch (IOException e) { throw new ApiException(0, "Сервер повернув некоректні дані JSON"); }
    }
    private String send(String method, String path, Object value) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + path))
                    .timeout(Duration.ofSeconds(25)).header("Accept", "application/json");
            if (value == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json; charset=UTF-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(value)));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "Помилка сервера HTTP " + response.statusCode();
                try {
                    var body = mapper.readTree(response.body());
                    if (body.hasNonNull("message")) message = body.get("message").asText();
                    else if (body.hasNonNull("error")) message = body.get("error").asText();
                } catch (Exception ignored) { }
                throw new ApiException(response.statusCode(), message);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Мережеву операцію перервано");
        } catch (IOException e) {
            LOG.warning("HTTP connection failed: " + e.getClass().getSimpleName());
            throw new ApiException(0, "Немає зв’язку із сервером. Перевірте запуск Tomcat і адресу API.");
        }
    }
}
