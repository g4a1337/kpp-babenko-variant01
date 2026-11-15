package ua.edu.nupp.kpp.client;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import ua.edu.nupp.kpp.client.api.ApiClient;
import ua.edu.nupp.kpp.client.csv.ImportEquipmentTask;
import ua.edu.nupp.kpp.client.model.*;

import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;

/** JavaFX presentation layer. SQL and domain rules remain on the server. */
public class MainApp extends Application {
    private static final Logger LOG = Logger.getLogger(MainApp.class.getName());
    private final ApiClient api = new ApiClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "it-support-client-worker"); t.setDaemon(true); return t;
    });
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<Category> categories = FXCollections.observableArrayList();
    private final ObservableList<Equipment> equipment = FXCollections.observableArrayList();
    private final ObservableList<Ticket> tickets = FXCollections.observableArrayList();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty importing = new SimpleBooleanProperty(false);
    private final Label status = new Label("Підключення до сервера…");
    private final Label counts = new Label("Завантаження даних");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private Stage stage;
    private TabPane tabs;
    private boolean captured;

    private record Dataset(List<User> users, List<Category> categories, List<Equipment> equipment, List<Ticket> tickets) { }

    @Override public void start(Stage stage) {
        this.stage = stage;
        Label title = new Label("IT SUPPORT"); title.getStyleClass().add("brand");
        Label subtitle = new Label("Опрацювання заявок ІТ-підтримки"); subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(3, title, subtitle);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox identity = new VBox(4, new Label("Бабенко Дмитро Володимирович"), new Label("204-ТН  ·  Варіант №1"));
        identity.setAlignment(Pos.CENTER_RIGHT); identity.getStyleClass().add("identity");
        HBox header = new HBox(20, heading, spacer, identity); header.setAlignment(Pos.CENTER_LEFT); header.getStyleClass().add("header");
        Button refresh = new Button("Оновити дані"); refresh.getStyleClass().add("secondary");
        refresh.disableProperty().bind(busy.or(importing)); refresh.setOnAction(e -> load("Дані оновлено"));
        Region gap = new Region(); HBox.setHgrow(gap, Priority.ALWAYS);
        HBox summary = new HBox(15, counts, gap, refresh); summary.setAlignment(Pos.CENTER_LEFT); summary.getStyleClass().add("summary");
        tabs = new TabPane(ticketTab(), userTab(), equipmentTab(), categoryTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        spinner.setPrefSize(18, 18); spinner.setMaxSize(18, 18); spinner.visibleProperty().bind(busy.or(importing));
        Label address = new Label(api.baseUrl()); address.getStyleClass().add("muted");
        status.setWrapText(true); HBox.setHgrow(status, Priority.ALWAYS);
        HBox footer = new HBox(10, spinner, status, address); footer.setAlignment(Pos.CENTER_LEFT); footer.getStyleClass().add("footer");
        BorderPane root = new BorderPane(tabs, new VBox(header, summary), null, footer, null);
        Scene scene = new Scene(root, 1460, 880);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/client.css")).toExternalForm());
        stage.setScene(scene); stage.setTitle("ІТ-підтримка · Бабенко Дмитро · Варіант 1");
        stage.setMinWidth(1120); stage.setMinHeight(720); stage.show();
        load("Підключено. Дані завантажено.");
    }

    private Tab userTab() {
        TextField fullName = input("Наприклад, Олена Коваль"), email = input("name@example.com"), department = input("Підрозділ або кафедра");
        CheckBox technician = new CheckBox("ІТ-фахівець · може бути виконавцем"); technician.setWrapText(true);
        GridPane form = form(); row(form, 0, "ПІБ *", fullName); row(form, 1, "Електронна пошта *", email); row(form, 2, "Підрозділ *", department); row(form, 3, "Роль", technician);
        var pane = new CrudPane<User>("users", users, User::id, "Користувач", form);
        pane.table.getColumns().addAll(numericColumn("ID", v -> "" + v.id(), 55), column("ПІБ", User::fullName, 220),
                column("Електронна пошта", User::email, 230), column("Підрозділ", User::department, 160),
                column("Роль", v -> v.technician() ? "ІТ-фахівець" : "Користувач", 130));
        pane.read = () -> new User(pane.id(), required(fullName, "ПІБ", 120), email(email), required(department, "Підрозділ", 120), technician.isSelected());
        pane.fill = v -> { fullName.setText(v.fullName()); email.setText(v.email()); department.setText(v.department()); technician.setSelected(v.technician()); };
        pane.clear = () -> { fullName.clear(); email.clear(); department.clear(); technician.setSelected(false); };
        pane.wire(); return new Tab("Користувачі", pane);
    }

    private Tab categoryTab() {
        TextField name = input("Наприклад, Мережа"), description = input("Які звернення охоплює категорія");
        GridPane form = form(); row(form, 0, "Назва *", name); row(form, 1, "Опис", description);
        var pane = new CrudPane<Category>("categories", categories, Category::id, "Категорія", form);
        pane.table.getColumns().addAll(numericColumn("ID", v -> "" + v.id(), 55), column("Категорія", Category::name, 250), column("Опис", Category::description, 420));
        pane.read = () -> new Category(pane.id(), required(name, "Назва", 100), limited(description, "Опис", 1000));
        pane.fill = v -> { name.setText(v.name()); description.setText(v.description()); };
        pane.clear = () -> { name.clear(); description.clear(); };
        pane.wire(); return new Tab("Категорії", pane);
    }

    private Tab equipmentTab() {
        TextField inventory = input("Наприклад, PC-004"), name = input("Назва обладнання"), location = input("Корпус, кабінет"), criticality = input("0–100");
        GridPane form = form(); row(form, 0, "Інвентарний номер *", inventory); row(form, 1, "Назва *", name); row(form, 2, "Розташування *", location); row(form, 3, "Критичність F3 *", criticality);
        var pane = new CrudPane<Equipment>("equipment", equipment, Equipment::id, "Обладнання", form);
        pane.table.getColumns().addAll(numericColumn("ID", v -> "" + v.id(), 55), column("Інвентарний №", Equipment::inventoryNumber, 160),
                column("Назва", Equipment::name, 250), column("Розташування", Equipment::location, 180), numericColumn("Критичність F3", v -> number(v.criticality()), 130));
        pane.read = () -> new Equipment(pane.id(), required(inventory, "Інвентарний номер", 80), required(name, "Назва", 200), required(location, "Розташування", 200), score(criticality, "Критичність"));
        pane.fill = v -> { inventory.setText(v.inventoryNumber()); name.setText(v.name()); location.setText(v.location()); criticality.setText(number(v.criticality())); };
        pane.clear = () -> { inventory.clear(); name.clear(); location.clear(); criticality.clear(); };
        pane.wire();
        Button importButton = new Button("Імпортувати CSV"); importButton.getStyleClass().add("secondary");
        importButton.disableProperty().bind(busy.or(importing));
        ProgressBar progress = new ProgressBar(0); progress.setPrefWidth(230);
        Label importStatus = new Label("UTF-8 · роздільник ; · 4 стовпці"); importStatus.setWrapText(true);
        Button cancel = new Button("Скасувати"); cancel.setVisible(false); cancel.managedProperty().bind(cancel.visibleProperty());
        importButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("Імпорт переліку обладнання");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV-файли", "*.csv"));
            var file = chooser.showOpenDialog(stage); if (file == null) return;
            ImportEquipmentTask task = new ImportEquipmentTask(file.toPath(), api);
            importing.set(true); status.setText("Імпорт обладнання у фоновому режимі…");
            progress.progressProperty().bind(task.progressProperty()); importStatus.textProperty().bind(task.messageProperty());
            cancel.setVisible(true); cancel.setDisable(false); cancel.setOnAction(event -> { task.requestCancellation(); cancel.setDisable(true); status.setText("Завершення поточного рядка перед скасуванням…"); });
            Runnable finish = () -> { progress.progressProperty().unbind(); importStatus.textProperty().unbind(); cancel.setVisible(false); importing.set(false); };
            task.setOnSucceeded(event -> {
                finish.run(); var result = task.getValue();
                String summary = (result.cancelled() ? "Імпорт скасовано: " : "Імпорт завершено: ") + "додано " + result.imported() + ", пропущено " + result.skipped();
                if (!result.cancelled()) progress.setProgress(1); importStatus.setText(summary); load(summary);
                if (!result.errors().isEmpty()) details("Звіт імпорту", summary, String.join("\n", result.errors()));
            });
            task.setOnFailed(event -> { finish.run(); importStatus.setText("Імпорт зупинено. Перевірте звіт."); failure(task.getException()); load("Дані перечитано після зупинки імпорту"); });
            task.setOnCancelled(event -> { finish.run(); importStatus.setText("Скасовано. Уже збережені записи залишаються в БД."); load("Імпорт скасовано. Дані оновлено."); });
            executor.submit(task);
        });
        HBox actions = new HBox(12, importButton, progress, cancel); actions.setAlignment(Pos.CENTER_LEFT);
        VBox importBox = new VBox(8, actions, importStatus); importBox.getStyleClass().add("import-bar");
        pane.setBottom(importBox); return new Tab("Обладнання", pane);
    }

    private Tab ticketTab() {
        TextField title = input("Коротко опишіть проблему"), urgency = input("0–100"), impact = input("0–100");
        TextArea description = new TextArea(); description.setPromptText("Симптоми та обставини виникнення"); description.setWrapText(true); description.setPrefRowCount(3);
        ComboBox<User> requester = combo(users), assignee = combo(FXCollections.observableArrayList());
        users.addListener((ListChangeListener<User>) change -> assignee.getItems().setAll(users.filtered(User::technician)));
        ComboBox<Equipment> asset = combo(equipment); ComboBox<Category> category = combo(categories);
        assignee.setPromptText("Не призначено"); Button clearAssignee = new Button("×"); clearAssignee.setOnAction(e -> assignee.setValue(null));
        HBox assigneeBox = new HBox(5, assignee, clearAssignee); HBox.setHgrow(assignee, Priority.ALWAYS);
        Label priority = new Label("Індекс пріоритету обчислює сервер"); priority.setWrapText(true); priority.getStyleClass().add("formula");
        GridPane form = form(); row(form, 0, "Тема *", title); row(form, 1, "Опис *", description); row(form, 2, "Заявник *", requester); row(form, 3, "Виконавець", assigneeBox); row(form, 4, "Обладнання *", asset); row(form, 5, "Категорія *", category); row(form, 6, "Терміновість F1 *", urgency); row(form, 7, "Вплив на роботу F2 *", impact); row(form, 8, "Пріоритет", priority);
        var pane = new CrudPane<Ticket>("tickets", tickets, Ticket::id, "Заявка", form);
        pane.table.getColumns().addAll(numericColumn("№", v -> "" + v.id(), 45), column("Тема", Ticket::title, 230),
                column("Стан", v -> stateName(v.state()), 130), numericColumn("Пріоритет I", v -> number(v.priority()), 100),
                column("Заявник", v -> userName(v.requesterId()), 150), column("Виконавець", v -> userName(v.assigneeId()), 150),
                column("Обладнання", v -> equipmentName(v.equipmentId()), 180));
        pane.read = () -> {
            Ticket current = pane.table.getSelectionModel().getSelectedItem();
            return new Ticket(pane.id(), required(title, "Тема", 200), required(description.getText(), "Опис", 4000),
                    selected(requester, "Заявник").id(), assignee.getValue() == null ? null : assignee.getValue().id(),
                    selected(asset, "Обладнання").id(), selected(category, "Категорія").id(), current == null ? "NEW" : current.state(),
                    score(urgency, "Терміновість"), score(impact, "Вплив на роботу"), 0, 0, current == null ? null : current.createdAt());
        };
        pane.fill = v -> {
            title.setText(v.title()); description.setText(v.description()); requester.setValue(find(users, User::id, v.requesterId()));
            assignee.setValue(find(users, User::id, v.assigneeId())); asset.setValue(find(equipment, Equipment::id, v.equipmentId()));
            category.setValue(find(categories, Category::id, v.categoryId())); urgency.setText(number(v.urgency())); impact.setText(number(v.impact()));
            priority.setText("I = " + number(v.priority()) + " / 100\nF3 = " + number(v.criticality()) + " · " + stateName(v.state()));
        };
        pane.clear = () -> { title.clear(); description.clear(); requester.setValue(null); assignee.setValue(null); asset.setValue(null); category.setValue(null); urgency.clear(); impact.clear(); priority.setText("I = 0,40·F1 + 0,35·F2 + 0,25·F3"); };
        pane.wire();
        Label lifecycle = new Label("НОВА → ПРИЗНАЧЕНА → В РОБОТІ → ЗАКРИТА"); lifecycle.setWrapText(true); lifecycle.getStyleClass().add("muted");
        Button next = new Button("Перевести в наступний стан"); next.getStyleClass().add("secondary"); next.setMaxWidth(Double.MAX_VALUE);
        next.disableProperty().bind(busy.or(importing).or(pane.table.getSelectionModel().selectedItemProperty().isNull()));
        next.setOnAction(e -> {
            Ticket ticket = pane.table.getSelectionModel().getSelectedItem();
            String target = nextState(ticket.state());
            if (target == null) { status.setText("Заявку вже закрито. Подальших переходів немає."); return; }
            if (ticket.assigneeId() == null) { error("Спочатку оберіть виконавця та збережіть заявку."); return; }
            if (confirm("Змінити стан заявки №" + ticket.id() + "?", stateName(ticket.state()) + " → " + stateName(target)))
                mutate(() -> api.transition(ticket.id(), target), "Стан заявки змінено на «" + stateName(target) + "»");
        });
        pane.side.getChildren().addAll(new Separator(), lifecycle, next);
        return new Tab("Заявки", pane);
    }

    private final class CrudPane<T> extends BorderPane {
        final TableView<T> table;
        final VBox side;
        final String resource;
        final Function<T, Long> identifier;
        final Label formTitle;
        Supplier<T> read;
        Consumer<T> fill;
        Runnable clear;
        CrudPane(String resource, ObservableList<T> items, Function<T, Long> identifier, String title, GridPane fields) {
            this.resource = resource; this.identifier = identifier;
            table = new TableView<>(items); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.setPlaceholder(new Label("Записів немає. Створіть перший запис або оновіть дані."));
            table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            formTitle = new Label(title + " · новий запис"); formTitle.getStyleClass().add("form-title");
            side = new VBox(13, formTitle, fields); side.getStyleClass().add("editor"); side.setPrefWidth(350); side.setMinWidth(315); side.setMaxWidth(350);
            ScrollPane scroll = new ScrollPane(side); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setPrefWidth(370);
            setCenter(table); setRight(scroll); setPadding(new Insets(18)); BorderPane.setMargin(scroll, new Insets(0, 0, 0, 18));
        }
        Long id() { T selected = table.getSelectionModel().getSelectedItem(); return selected == null ? null : identifier.apply(selected); }
        void wire() {
            Button save = new Button("Зберегти"); save.getStyleClass().add("primary"); save.setMaxWidth(Double.MAX_VALUE);
            Button create = new Button("Новий запис"); create.getStyleClass().add("secondary"); create.setMaxWidth(Double.MAX_VALUE);
            Button delete = new Button("Видалити"); delete.getStyleClass().add("danger"); delete.setMaxWidth(Double.MAX_VALUE);
            save.disableProperty().bind(busy.or(importing)); create.disableProperty().bind(busy.or(importing));
            delete.disableProperty().bind(busy.or(importing).or(table.getSelectionModel().selectedItemProperty().isNull()));
            create.setOnAction(e -> { table.getSelectionModel().clearSelection(); clear.run(); formTitle.setText("Новий запис"); status.setText("Заповніть обов’язкові поля, позначені *"); });
            save.setOnAction(e -> {
                try { T value = read.get(); Long id = id(); mutate(() -> api.save(resource, id, value), id == null ? "Запис створено" : "Зміни збережено"); }
                catch (IllegalArgumentException ex) { error(ex.getMessage()); }
            });
            delete.setOnAction(e -> {
                Long id = id(); if (id == null) return;
                if (confirm("Видалити запис №" + id + "?", "Операцію неможливо скасувати. Пов’язані записи захищено від видалення."))
                    mutate(() -> api.delete(resource, id), "Запис видалено");
            });
            side.getChildren().addAll(save, new HBox(9, create, delete), note("* Обов’язкові поля. Для редагування виберіть рядок у таблиці."));
            HBox.setHgrow(create, Priority.ALWAYS); HBox.setHgrow(delete, Priority.ALWAYS);
            table.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> {
                if (value != null) { formTitle.setText("Редагування · №" + identifier.apply(value)); fill.accept(value); }
                else { formTitle.setText("Новий запис"); clear.run(); }
            });
        }
    }

    private Dataset fetch() { return new Dataset(api.users(), api.categories(), api.equipment(), api.tickets()); }
    private void load(String message) { runTask(this::fetch, data -> { apply(data); status.setText(message); captureIfRequested(); }); }
    private void mutate(Runnable action, String message) {
        runTask(() -> { action.run(); return fetch(); }, data -> { apply(data); status.setText(message); });
    }
    private <T> void runTask(Callable<T> action, Consumer<T> success) {
        if (busy.get()) return;
        busy.set(true); status.setText("Виконується операція…");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        task.setOnSucceeded(e -> { busy.set(false); success.accept(task.getValue()); });
        task.setOnFailed(e -> { busy.set(false); failure(task.getException()); });
        task.setOnCancelled(e -> { busy.set(false); status.setText("Операцію скасовано"); });
        executor.submit(task);
    }
    private void apply(Dataset data) {
        users.setAll(data.users()); categories.setAll(data.categories()); equipment.setAll(data.equipment()); tickets.setAll(data.tickets());
        long active = tickets.stream().filter(t -> !"CLOSED".equals(t.state())).count();
        counts.setText("Заявок: " + tickets.size() + "   ·   Активних: " + active + "   ·   Обладнання: " + equipment.size() + "   ·   Користувачів: " + users.size());
    }
    private void failure(Throwable e) {
        LOG.log(Level.WARNING, "Client operation failed", e);
        String message = e.getMessage() == null ? "Не вдалося виконати операцію" : e.getMessage();
        status.setText(message); error(message);
    }
    private void error(String message) { Alert alert = new Alert(Alert.AlertType.ERROR); alert.initOwner(stage); alert.setTitle("Помилка"); alert.setHeaderText("Операцію не виконано"); alert.setContentText(message); alert.show(); }
    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL); alert.initOwner(stage); alert.setTitle("Підтвердження"); alert.setHeaderText(title);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void details(String title, String summary, String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.initOwner(stage); alert.setTitle(title); alert.setHeaderText(summary);
        TextArea content = new TextArea(text); content.setEditable(false); content.setWrapText(true); content.setPrefSize(620, 330); alert.getDialogPane().setContent(content); alert.show();
    }

    private static TextField input(String prompt) { TextField t = new TextField(); t.setPromptText(prompt); return t; }
    private static <T> ComboBox<T> combo(ObservableList<T> items) { ComboBox<T> c = new ComboBox<>(items); c.setMaxWidth(Double.MAX_VALUE); return c; }
    private static GridPane form() { GridPane p = new GridPane(); p.setVgap(7); ColumnConstraints c = new ColumnConstraints(); c.setHgrow(Priority.ALWAYS); c.setFillWidth(true); p.getColumnConstraints().add(c); return p; }
    private static void row(GridPane form, int n, String label, Node field) { Label l = new Label(label); l.getStyleClass().add("field-label"); form.add(l, 0, n * 2); form.add(field, 0, n * 2 + 1); GridPane.setHgrow(field, Priority.ALWAYS); }
    private static Label note(String text) { Label l = new Label(text); l.setWrapText(true); l.getStyleClass().add("muted"); return l; }
    private static <T> TableColumn<T, String> column(String title, Function<T, String> getter, double width) {
        TableColumn<T, String> column = new TableColumn<>(title); column.setCellValueFactory(v -> new SimpleStringProperty(getter.apply(v.getValue()))); column.setPrefWidth(width); column.setMinWidth(Math.min(width, 60)); return column;
    }
    private static <T> TableColumn<T, String> numericColumn(String title, Function<T, String> getter, double width) {
        TableColumn<T, String> column = column(title, getter, width);
        column.setComparator(Comparator.comparingDouble(Double::parseDouble));
        return column;
    }
    private static String required(TextField field, String name, int max) { return required(field.getText(), name, max); }
    private static String required(String value, String name, int max) { String text = value == null ? "" : value.trim(); if (text.isEmpty() || text.length() > max) throw new IllegalArgumentException(name + ": потрібно від 1 до " + max + " символів"); return text; }
    private static String limited(TextField value, String name, int max) { String text = value.getText().trim(); if (text.length() > max) throw new IllegalArgumentException(name + ": не більше " + max + " символів"); return text; }
    private static String email(TextField field) { String value = required(field, "Електронна пошта", 190); if (!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Укажіть коректну електронну пошту"); return value; }
    private static double score(TextField field, String name) { try { double value = Double.parseDouble(field.getText().trim().replace(',', '.')); if (Double.isFinite(value) && value >= 0 && value <= 100) return value; } catch (NumberFormatException ignored) { } throw new IllegalArgumentException(name + ": укажіть число від 0 до 100"); }
    private static <T> T selected(ComboBox<T> combo, String name) { if (combo.getValue() == null) throw new IllegalArgumentException("Оберіть значення: " + name); return combo.getValue(); }
    private static <T> T find(List<T> values, Function<T, Long> identifier, Long id) { return id == null ? null : values.stream().filter(v -> id.equals(identifier.apply(v))).findFirst().orElse(null); }
    private String userName(Long id) { User u = find(users, User::id, id); return u == null ? "—" : u.fullName(); }
    private String equipmentName(Long id) { Equipment e = find(equipment, Equipment::id, id); return e == null ? "—" : e.name(); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    public static String stateName(String state) { return switch (state) { case "NEW" -> "НОВА"; case "ASSIGNED" -> "ПРИЗНАЧЕНА"; case "IN_PROGRESS" -> "В РОБОТІ"; case "CLOSED" -> "ЗАКРИТА"; default -> state; }; }
    public static String nextState(String state) { return switch (state) { case "NEW" -> "ASSIGNED"; case "ASSIGNED" -> "IN_PROGRESS"; case "IN_PROGRESS" -> "CLOSED"; default -> null; }; }

    /** Optional evidence capture of the actual running application after real API data load. */
    private void captureIfRequested() {
        String dir = System.getProperty("app.screenshotDir");
        if (captured || dir == null || dir.isBlank()) return;
        captured = true;
        captureTab(Path.of(dir), 0);
    }
    private void captureTab(Path dir, int index) {
        if (index >= tabs.getTabs().size()) { tabs.getSelectionModel().selectFirst(); return; }
        tabs.getSelectionModel().select(index);
        PauseTransition pause = new PauseTransition(Duration.millis(700)); pause.setOnFinished(e -> {
            try {
                Files.createDirectories(dir);
                String[] names = {"01-tickets", "02-users", "03-equipment", "04-categories"};
                ImageIO.write(SwingFXUtils.fromFXImage(stage.getScene().snapshot(null), null), "png", dir.resolve(names[index] + ".png").toFile());
            } catch (Exception ex) { LOG.log(Level.WARNING, "Screenshot capture failed", ex); }
            captureTab(dir, index + 1);
        }); pause.play();
    }
    @Override public void stop() { executor.shutdownNow(); }
    public static void main(String[] args) { launch(args); }
}
