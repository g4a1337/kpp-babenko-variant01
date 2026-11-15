package ua.edu.nupp.kpp.client.csv;

import ua.edu.nupp.kpp.client.model.Equipment;
import java.io.*;
import java.util.*;

/** Semicolon CSV with UTF-8 BOM, escaped quotes, quoted delimiters and multiline fields. */
public final class EquipmentCsv {
    public static final List<String> HEADER = List.of("inventoryNumber", "name", "location", "criticality");
    public record Row(int lineNumber, List<String> fields) { }
    private EquipmentCsv() { }

    public static List<Row> read(Reader input) throws IOException {
        PushbackReader reader = new PushbackReader(input, 2);
        List<Row> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, afterQuote = false, started = false, first = true;
        int line = 1, rowLine = 1, c;
        while ((c = reader.read()) != -1) {
            char ch = (char)c;
            if (first) { first = false; if (ch == '\ufeff') continue; }
            if (quoted) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; afterQuote = true; if (next != -1) reader.unread(next); }
                } else { field.append(ch); if (ch == '\n') line++; }
                continue;
            }
            if (ch == '"') {
                if (started || afterQuote) throw new IOException("Некоректні лапки CSV у рядку " + line);
                quoted = true; started = true;
            } else if (ch == ';') {
                fields.add(field.toString()); field.setLength(0); started = false; afterQuote = false;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r') { int next = reader.read(); if (next != '\n' && next != -1) reader.unread(next); }
                fields.add(field.toString());
                if (fields.stream().anyMatch(s -> !s.isBlank())) rows.add(new Row(rowLine, List.copyOf(fields)));
                field.setLength(0); fields.clear(); started = false; afterQuote = false;
                line++; rowLine = line;
            } else {
                if (afterQuote) throw new IOException("Зайві символи після лапок CSV у рядку " + line);
                field.append(ch); started = true;
            }
        }
        if (quoted) throw new IOException("Незакриті лапки CSV у рядку " + rowLine);
        if (started || afterQuote || !fields.isEmpty()) {
            fields.add(field.toString());
            if (fields.stream().anyMatch(s -> !s.isBlank())) rows.add(new Row(rowLine, List.copyOf(fields)));
        }
        if (rows.isEmpty() || !rows.getFirst().fields().equals(HEADER))
            throw new IOException("Очікується заголовок CSV: " + String.join(";", HEADER));
        return List.copyOf(rows.subList(1, rows.size()));
    }
    public static Equipment equipment(Row row) {
        if (row.fields().size() != 4) throw new IllegalArgumentException("Очікується 4 стовпці");
        String inventory = required(row.fields().get(0), "Інвентарний номер", 80);
        String name = required(row.fields().get(1), "Назва", 200);
        String location = required(row.fields().get(2), "Розташування", 200);
        double criticality;
        try { criticality = Double.parseDouble(row.fields().get(3).trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Критичність має бути числом 0–100"); }
        if (!Double.isFinite(criticality) || criticality < 0 || criticality > 100)
            throw new IllegalArgumentException("Критичність має бути числом 0–100");
        return new Equipment(null, inventory, name, location, criticality);
    }
    private static String required(String value, String name, int max) {
        String v = value.trim();
        if (v.isEmpty() || v.length() > max) throw new IllegalArgumentException(name + ": від 1 до " + max + " символів");
        return v;
    }
}
