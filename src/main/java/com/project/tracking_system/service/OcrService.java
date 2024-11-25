package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    @Autowired
    public OcrService(TypeDefinitionTrackPostService typeDefinitionTrackPostService) {
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
    }

    public String processImage(MultipartFile file) throws IOException {
        try {
            BufferedImage bufferedImage = preprocessImage(file);
            return recognizeText(bufferedImage);
        } catch (TesseractException e) {
            throw new RuntimeException("Ошибка OCR: " + e.getMessage(), e);
        }
    }

    private BufferedImage preprocessImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IOException("Формат изображения не поддерживается");
        }

        // Преобразование в оттенки серого
        BufferedImage grayscaleImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayscaleImage.getGraphics();
        g.drawImage(originalImage, 0, 0, null);
        g.dispose();
        return grayscaleImage;
    }

    public String recognizeText(BufferedImage image) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/local/share/tessdata");
        tesseract.setLanguage("rus+eng");
        return tesseract.doOCR(image);
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