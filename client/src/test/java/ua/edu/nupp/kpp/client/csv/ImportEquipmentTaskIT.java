package ua.edu.nupp.kpp.client.csv;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ua.edu.nupp.kpp.client.api.ApiClient;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit live integration check: mvn -Dtest=ImportEquipmentTaskIT -Dit.api=... test */
@EnabledIfSystemProperty(named = "it.api", matches = ".+")
class ImportEquipmentTaskIT {
    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        assertTrue(ready.await(15, TimeUnit.SECONDS), "JavaFX toolkit starts");
    }
    @AfterAll static void stopFx() { Platform.exit(); }

    @Test void actualTaskImportsIntoMySqlReportsErrorsAndKeepsFxResponsive() throws Exception {
        String base = System.getProperty("it.api");
        ApiClient live = new ApiClient(base);
        String prefix = "CSV-IT-" + UUID.randomUUID().toString().substring(0, 8);
        Path csv = Files.createTempFile("equipment-it-", ".csv");
        Files.writeString(csv, "inventoryNumber;name;location;criticality\n"
                + prefix + "-A;Інтеграційний ПК;Тестова аудиторія;80\n"
                + prefix + "-BAD;Поза межами;Тестова аудиторія;150\n"
                + prefix + "-A;Повтор;Тестова аудиторія;20\n"
                + prefix + "-B;Тестовий проєктор;Тестова аудиторія;45\n", StandardCharsets.UTF_8);

        // Delay only the transport boundary; every accepted request is forwarded to the live Servlet/MySQL.
        // This gives a deterministic window in which an FX event must run while import is in progress.
        HttpClient transport = HttpClient.newHttpClient();
        HttpServer proxy = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        CountDownLatch requestStarted = new CountDownLatch(1), fxPulse = new CountDownLatch(1), completed = new CountDownLatch(1);
        AtomicBoolean uiEventDuringNetwork = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<ImportEquipmentTask.Result> result = new AtomicReference<>();
        AtomicReference<Double> progress = new AtomicReference<>(0d);
        AtomicInteger progressEvents = new AtomicInteger();
        proxy.createContext("/api", exchange -> {
            try {
                String resource = exchange.getRequestURI().getPath().substring("/api".length());
                byte[] input = exchange.getRequestBody().readAllBytes();
                requestStarted.countDown();
                Thread.sleep(150);
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + resource))
                        .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json; charset=UTF-8")
                        .method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(input)).build();
                HttpResponse<byte[]> response = transport.send(request, HttpResponse.BodyHandlers.ofByteArray());
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                if (response.statusCode() == 204) exchange.sendResponseHeaders(204, -1);
                else { exchange.sendResponseHeaders(response.statusCode(), response.body().length); exchange.getResponseBody().write(response.body()); }
            } catch (Exception e) { exchange.sendResponseHeaders(502, -1); }
            finally { exchange.close(); }
        });
        proxy.start();
        ImportEquipmentTask task = new ImportEquipmentTask(csv, new ApiClient("http://localhost:" + proxy.getAddress().getPort() + "/api"));
        try {
            Platform.runLater(() -> {
                task.progressProperty().addListener((o, old, value) -> { progress.set(value.doubleValue()); progressEvents.incrementAndGet(); });
                task.setOnSucceeded(e -> { result.set(task.getValue()); completed.countDown(); });
                task.setOnFailed(e -> { failure.set(task.getException()); completed.countDown(); });
                Thread worker = new Thread(task, "csv-integration-worker"); worker.setDaemon(true); worker.start();
            });
            assertTrue(requestStarted.await(15, TimeUnit.SECONDS), "Background Task reaches live API");
            Platform.runLater(() -> { uiEventDuringNetwork.set(task.isRunning()); fxPulse.countDown(); });
            assertTrue(fxPulse.await(3, TimeUnit.SECONDS), "FX event runs during CSV network work");
            assertTrue(completed.await(25, TimeUnit.SECONDS), "Import finishes");
            assertNull(failure.get(), () -> "Task failed: " + failure.get());
            assertTrue(uiEventDuringNetwork.get(), "UI thread remains responsive while the Task is running");
            assertEquals(2, result.get().imported()); assertEquals(2, result.get().skipped());
            assertEquals(2, result.get().errors().size()); assertFalse(result.get().cancelled());
            assertTrue(progressEvents.get() >= 1); assertEquals(1d, progress.get(), 0.00001);
            assertEquals(2, live.equipment().stream().filter(e -> e.inventoryNumber().startsWith(prefix)).count());
            System.out.println("LIVE_CSV_IMPORT_OK imported=2 skipped=2 progress=1.0 fxResponsive=true MySQLRows=2");
        } finally {
            proxy.stop(0); Files.deleteIfExists(csv);
            for (var value : live.equipment()) if (value.inventoryNumber().startsWith(prefix)) live.delete("equipment", value.id());
        }
    }

    @Test void cancellationFinishesCurrentRowAndDoesNotSendFollowingRows() throws Exception {
        String base = System.getProperty("it.api");
        ApiClient live = new ApiClient(base);
        String prefix = "CSV-CANCEL-" + UUID.randomUUID().toString().substring(0, 8);
        Path csv = Files.createTempFile("equipment-cancel-it-", ".csv");
        Files.writeString(csv, "inventoryNumber;name;location;criticality\n"
                + prefix + "-A;Перший ПК;Тестова аудиторія;80\n"
                + prefix + "-B;Другий ПК;Тестова аудиторія;45\n"
                + prefix + "-C;Третій ПК;Тестова аудиторія;20\n", StandardCharsets.UTF_8);
        CountDownLatch requestStarted = new CountDownLatch(1), allowCurrentRequest = new CountDownLatch(1);
        CountDownLatch cancellationSet = new CountDownLatch(1), completed = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean fxResponsive = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<ImportEquipmentTask.Result> result = new AtomicReference<>();
        AtomicReference<Double> progress = new AtomicReference<>(0d);
        HttpClient transport = HttpClient.newHttpClient();
        HttpServer proxy = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        proxy.createContext("/api", exchange -> {
            try {
                requests.incrementAndGet();
                byte[] input = exchange.getRequestBody().readAllBytes();
                requestStarted.countDown();
                if (!allowCurrentRequest.await(15, TimeUnit.SECONDS)) throw new TimeoutException("Cancellation was not requested");
                String resource = exchange.getRequestURI().getPath().substring("/api".length());
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + resource))
                        .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json; charset=UTF-8")
                        .method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(input)).build();
                HttpResponse<byte[]> response = transport.send(request, HttpResponse.BodyHandlers.ofByteArray());
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(response.statusCode(), response.body().length);
                exchange.getResponseBody().write(response.body());
            } catch (Exception e) { exchange.sendResponseHeaders(502, -1); }
            finally { exchange.close(); }
        });
        proxy.start();
        ImportEquipmentTask task = new ImportEquipmentTask(csv, new ApiClient("http://localhost:" + proxy.getAddress().getPort() + "/api"));
        try {
            Platform.runLater(() -> {
                task.progressProperty().addListener((o, old, value) -> progress.set(value.doubleValue()));
                task.setOnSucceeded(e -> { result.set(task.getValue()); completed.countDown(); });
                task.setOnFailed(e -> { failure.set(task.getException()); completed.countDown(); });
                Thread worker = new Thread(task, "csv-cancellation-worker"); worker.setDaemon(true); worker.start();
            });
            assertTrue(requestStarted.await(15, TimeUnit.SECONDS), "First import request reaches the transport");
            Platform.runLater(() -> {
                fxResponsive.set(task.isRunning());
                task.requestCancellation();
                cancellationSet.countDown();
            });
            assertTrue(cancellationSet.await(3, TimeUnit.SECONDS), "FX thread handles cancellation while HTTP is pending");
            allowCurrentRequest.countDown();
            assertTrue(completed.await(25, TimeUnit.SECONDS), "Task finishes its current row after cancellation");
            assertNull(failure.get(), () -> "Task failed: " + failure.get());
            assertTrue(fxResponsive.get());
            assertTrue(result.get().cancelled());
            assertEquals(1, result.get().imported());
            assertEquals(0, result.get().skipped());
            assertTrue(result.get().errors().isEmpty());
            assertEquals(1, requests.get(), "Rows after cancellation are never sent");
            assertEquals(1d / 3d, progress.get(), 0.00001, "Progress reports the one committed row");
            assertEquals(1, live.equipment().stream().filter(e -> e.inventoryNumber().startsWith(prefix)).count());
            System.out.println("LIVE_CSV_CANCEL_OK imported=1 skipped=0 cancelled=true requests=1 progress=1/3 fxResponsive=true MySQLRows=1");
        } finally {
            allowCurrentRequest.countDown();
            proxy.stop(0); Files.deleteIfExists(csv);
            for (var value : live.equipment()) if (value.inventoryNumber().startsWith(prefix)) live.delete("equipment", value.id());
        }
    }
}
