package ua.edu.nupp.kpp.client.csv;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentCsvTest {
    private static final String HEADER = "inventoryNumber;name;location;criticality\n";
    @Test void readsUtf8BomAndQuotedDelimiter() throws Exception {
        var rows = EquipmentCsv.read(new StringReader("\ufeff" + HEADER + "PC-1;\"Ноутбук; робочий\";Офіс;80\n"));
        var value = EquipmentCsv.equipment(rows.getFirst());
        assertEquals("Ноутбук; робочий", value.name()); assertEquals(80, value.criticality());
    }
    @Test void handlesEscapedQuotes() throws Exception {
        var rows = EquipmentCsv.read(new StringReader(HEADER + "PC;\"Монітор \"\"Dell\"\"\";Офіс;10"));
        assertEquals("Монітор \"Dell\"", EquipmentCsv.equipment(rows.getFirst()).name());
    }
    @Test void preservesMultilineQuotedFieldAndLineNumbers() throws Exception {
        var rows = EquipmentCsv.read(new StringReader(HEADER + "PC;\"Два\nрядки\";Офіс;10\nPR;Принтер;Офіс;20"));
        assertEquals("Два\nрядки", rows.get(0).fields().get(1)); assertEquals(4, rows.get(1).lineNumber());
    }
    @Test void ignoresEmptyLinesAndHandlesWindowsNewlines() throws Exception {
        var rows = EquipmentCsv.read(new StringReader(HEADER.replace("\n", "\r\n") + "\r\nPC;ПК;Офіс;10\r\n"));
        assertEquals(1, rows.size()); assertEquals(3, rows.getFirst().lineNumber());
    }
    @Test void rejectsWrongHeader() { assertThrows(IOException.class, () -> EquipmentCsv.read(new StringReader("name;criticality\n"))); }
    @Test void rejectsUnclosedQuotes() { assertThrows(IOException.class, () -> EquipmentCsv.read(new StringReader(HEADER + "PC;\"Назва;Офіс;1"))); }
    @Test void rejectsGarbageAfterQuote() { assertThrows(IOException.class, () -> EquipmentCsv.read(new StringReader(HEADER + "PC;\"Назва\"x;Офіс;1"))); }
    @Test void rejectsNonFiniteCriticality() { assertThrows(IllegalArgumentException.class, () -> EquipmentCsv.equipment(row("PC", "ПК", "Офіс", "NaN"))); }
    @Test void rejectsOutOfRangeCriticality() { assertThrows(IllegalArgumentException.class, () -> EquipmentCsv.equipment(row("PC", "ПК", "Офіс", "100.01"))); }
    @Test void acceptsBoundaryValues() { assertEquals(0, EquipmentCsv.equipment(row("PC", "ПК", "Офіс", "0")).criticality()); assertEquals(100, EquipmentCsv.equipment(row("PC", "ПК", "Офіс", "100")).criticality()); }
    @Test void rejectsMissingName() { assertThrows(IllegalArgumentException.class, () -> EquipmentCsv.equipment(row("PC", "  ", "Офіс", "20"))); }
    @Test void rejectsWrongNumberOfColumns() { assertThrows(IllegalArgumentException.class, () -> EquipmentCsv.equipment(row("PC", "ПК", "Офіс"))); }
    private EquipmentCsv.Row row(String... fields) { return new EquipmentCsv.Row(2, List.of(fields)); }
}
