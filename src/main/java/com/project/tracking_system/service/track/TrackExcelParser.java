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
                Cell numberCell = row.getCell(0);
                if (numberCell == null) {
                    continue;
                }
                String number = readCell(numberCell).trim();
                if (number.isEmpty()) {
                    continue;
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
                rows.add(new TrackExcelRow(number, store, phone));
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
}
