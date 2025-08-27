package com.project.tracking_system.service.track;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис чтения XLS/XLSX-файлов с трек-номерами.
 * <p>
 * Возвращает список строк без нормализации значений.
 * </p>
 */
@Slf4j
@Service
public class TrackExcelParser {

    /**
     * Читает файл и извлекает все строки с данными.
     * Первая строка (заголовок) пропускается.
     * Ожидается пять столбцов: номер, магазин, телефон, ФИО, предрегистрация.
     *
     * @param file загруженный пользователем файл
     * @return список необработанных строк
     * @throws IOException при ошибке чтения файла
     */
    public List<TrackExcelRow> parse(MultipartFile file) throws IOException {
        List<TrackExcelRow> rows = new ArrayList<>();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int i = 1; i <= last; i++) { // пропускаем заголовок
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String number = null;
                Cell numberCell = row.getCell(0);
                if (numberCell != null) {
                    number = readCell(numberCell).trim();
                }
                String store = null;
                Cell storeCell = row.getCell(1);
                if (storeCell != null) {
                    store = readCell(storeCell).trim();
                }
                String phone = null;
                Cell phoneCell = row.getCell(2);
                if (phoneCell != null) {
                    phone = readCell(phoneCell).trim();
                }
                String fullName = null;
                Cell nameCell = row.getCell(3);
                if (nameCell != null) {
                    fullName = readCell(nameCell).trim();
                }
                boolean preRegistered = parsePreRegistration(row.getCell(4));
                if ((number == null || number.isBlank()) && !preRegistered) {
                    continue;
                }
                // Сохраняем значения строки без нормализации
                rows.add(new TrackExcelRow(number, store, phone, fullName, preRegistered));
            }
        }
        log.info("Разобрано {} строк из файла", rows.size());
        return rows;
    }

    private String readCell(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case STRING -> cell.getStringCellValue();
            default -> "";
        };
    }

    /**
     * Преобразует значение ячейки предрегистрации к булеву типу.
     * Допускаются значения "1", "true", "yes", "да" (без учёта регистра).
     */
    private boolean parsePreRegistration(Cell cell) {
        if (cell == null) {
            return false;
        }
        String value = readCell(cell).trim().toLowerCase();
        return value.equals("1") || value.equals("true")
                || value.equals("yes") || value.equals("да");
    }

}