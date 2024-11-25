package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    @Autowired
    public OcrService(TypeDefinitionTrackPostService typeDefinitionTrackPostService) {
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
    }

    public String processImage(MultipartFile file) throws IOException {
        // Логика обработки изображения из файла
        String imagePath = saveImage(file);
        try {
            return recognizeText(imagePath);
        } catch (TesseractException e) {
            throw new RuntimeException(e);
        }
    }

    private String saveImage(MultipartFile file) throws IOException {
        // Сохранение изображения на диск для обработки OCR
        String path = "/tmp/" + UUID.randomUUID().toString() + ".png"; // Пример пути
        File dest = new File(path);
        file.transferTo(dest);
        return path;
    }

    public String recognizeText(String imagePath) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/share/tesseract-ocr/5.3.4/tessdata/");
        tesseract.setLanguage("rus+eng");

        try {
            String result = tesseract.doOCR(new File(imagePath));
            return result;
        } catch (TesseractException e) {
            e.printStackTrace();
            throw new RuntimeException("Ошибка OCR: " + e.getMessage(), e);
        }
    }

    public List<TrackInfoListDTO> extractAndProcessTrackingNumbers(String text) {
        Pattern belPostPattern = Pattern.compile("^(PC|BV|BP)\\d{9}BY$");
        Pattern byPattern = Pattern.compile("^BY\\d{12}$");

        String[] lines = text.split("\\R"); // Разделяем текст по строкам
        List<TrackInfoListDTO> trackInfoList = new ArrayList<>();

        for (String line : lines) {
            line = line.trim(); // Убираем лишние пробелы
            if (belPostPattern.matcher(line).matches() || byPattern.matcher(line).matches()) {
                // Если трек-номер соответствует паттерну, вызываем соответствующую службу
                TrackInfoListDTO trackInfo = processTrackingNumber(line);
                trackInfoList.add(trackInfo);
            }
        }

        return trackInfoList;
    }

    private TrackInfoListDTO processTrackingNumber(String number) {
        // Используем уже существующую службу для получения информации по трек-номеру
        return typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(number);
    }
}