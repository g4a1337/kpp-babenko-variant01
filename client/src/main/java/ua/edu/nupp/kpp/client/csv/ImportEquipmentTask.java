package ua.edu.nupp.kpp.client.csv;

import javafx.concurrent.Task;
import ua.edu.nupp.kpp.client.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public final class ImportEquipmentTask extends Task<ImportEquipmentTask.Result> {
    private static final Logger LOG = Logger.getLogger(ImportEquipmentTask.class.getName());
    public record Result(int imported, int skipped, List<String> errors, boolean cancelled) { }
    private final Path file;
    private final ApiClient api;
    private volatile boolean cancellationRequested;
    public ImportEquipmentTask(Path file, ApiClient api) { this.file = file; this.api = api; }
    /** Finish the current HTTP request before stopping, so the summary reflects committed rows. */
    public void requestCancellation() { cancellationRequested = true; }
    @Override protected Result call() throws Exception {
        List<EquipmentCsv.Row> rows;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { rows = EquipmentCsv.read(reader); }
        Set<String> seen = new HashSet<>();
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        updateProgress(0, Math.max(1, rows.size()));
        for (var row : rows) {
            if (isCancelled() || cancellationRequested) break;
            try {
                var equipment = EquipmentCsv.equipment(row);
                if (!seen.add(equipment.inventoryNumber().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Повтор інвентарного номера у файлі");
                api.save("equipment", null, equipment);
                imported++;
            } catch (ApiException e) {
                if (e.statusCode() == 0 || e.statusCode() >= 500) {
                    throw new IllegalStateException("Імпорт зупинено через помилку зв’язку або сервера. Збережено: "
                            + imported + ". Оброблено: " + processed + ". " + e.getMessage(), e);
                }
                errors.add("Рядок " + row.lineNumber() + ": " + e.getMessage());
            } catch (IllegalArgumentException e) {
                errors.add("Рядок " + row.lineNumber() + ": " + e.getMessage());
            }
            processed++;
            updateProgress(processed, Math.max(1, rows.size()));
            updateMessage("Оброблено " + processed + "/" + rows.size() + " · додано " + imported + " · пропущено " + errors.size());
        }
        if (rows.isEmpty()) updateProgress(1, 1);
        LOG.info("Equipment CSV import: imported=" + imported + ", skipped=" + errors.size());
        return new Result(imported, errors.size(), List.copyOf(errors), cancellationRequested);
    }
}
