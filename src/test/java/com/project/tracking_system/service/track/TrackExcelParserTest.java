package com.project.tracking_system.service.track;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackExcelParserTest {

    @Test
    void parse_ReturnsRows() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet();
        sheet.createRow(0).createCell(0).setCellValue("num");
        var row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("AA111");
        row1.createCell(1).setCellValue("1");
        row1.createCell(2).setCellValue("+375291234567");
        row1.createCell(3).setCellValue("Иван Иванов");
        row1.createCell(4).setCellValue("0");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        MockMultipartFile file = new MockMultipartFile("f", out.toByteArray());

        TrackExcelParser parser = new TrackExcelParser();
        List<TrackExcelRow> rows = parser.parse(file);

        assertEquals(1, rows.size());
        assertEquals("AA111", rows.get(0).number());
        assertEquals("1", rows.get(0).store());
        assertEquals("+375291234567", rows.get(0).phone());
        assertEquals("Иван Иванов", rows.get(0).fullName());
        assertFalse(rows.get(0).preRegistered());
    }

    @Test
    void parse_ReadsPreRegistrationFlag() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet();
        sheet.createRow(0).createCell(0).setCellValue("num");
        var row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("");
        row1.createCell(4).setCellValue("да");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        MockMultipartFile file = new MockMultipartFile("f", out.toByteArray());

        TrackExcelParser parser = new TrackExcelParser();
        List<TrackExcelRow> rows = parser.parse(file);

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).preRegistered());
    }
}
