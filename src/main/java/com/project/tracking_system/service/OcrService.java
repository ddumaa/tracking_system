package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final TrackParcelService trackParcelService;

    @Autowired
    public OcrService(TypeDefinitionTrackPostService typeDefinitionTrackPostService, TrackParcelService trackParcelService) {
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.trackParcelService = trackParcelService;
    }

    public String processImage(MultipartFile file) throws IOException {
        try {
            BufferedImage bufferedImage = preprocessImage(file);
            String recognizedText = recognizeText(bufferedImage);

            if (recognizedText == null || recognizedText.trim().isEmpty()) {
                throw new RuntimeException("Ошибка: текст не распознан");
            }

            return recognizedText;
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

        // Бинаризация (пороговое преобразование)
        for (int y = 0; y < grayscaleImage.getHeight(); y++) {
            for (int x = 0; x < grayscaleImage.getWidth(); x++) {
                int color = grayscaleImage.getRGB(x, y) & 0xFF; // Извлекаем уровень яркости (0-255)
                int binaryColor = color > 128 ? 0xFFFFFF : 0; // Применяем порог
                grayscaleImage.setRGB(x, y, binaryColor);
            }
        }

        return grayscaleImage;
    }

    public String recognizeText(BufferedImage image) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/local/share/tessdata");
        tesseract.setLanguage("eng");
        tesseract.setVariable("user_defined_dpi", "300");
        return tesseract.doOCR(image);
    }

    public List<TrackingResultAdd> extractAndProcessTrackingNumbers(String text, String authenticatedUser) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Паттерн для поиска трек-номеров
        Pattern trackPattern = Pattern.compile("(BY\\d{12}|(PC|BV|BP)\\d{9}BY)");

        // Разделяем текст на строки
        String[] lines = text.split("\\R");
        List<TrackingResultAdd> trackInfoResult = new ArrayList<>();

        for (String line : lines) {
            line = line.trim(); // Убираем лишние пробелы

            // Логирование строки перед обработкой
            System.out.println("Обрабатываем строку: " + line);

            // Ищем все возможные трек-номера в строке
            Matcher matcher = trackPattern.matcher(line);
            while (matcher.find()) {
                // Извлекаем найденный трек-номер
                String trackNumber = matcher.group();
                System.out.println("Найден трек-номер: " + trackNumber);

                // Обработка трек-номера в блоке try-catch
                try {
                    // Получаем информацию о трек-номере
                    TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(trackNumber);

                    // Сохраняем данные о трек-номере в сервис
                    trackParcelService.save(trackNumber, trackInfo, authenticatedUser);

                    // Добавляем в результат успешное добавление
                    trackInfoResult.add(new TrackingResultAdd(trackNumber, "Добавлен"));
                } catch (Exception e) {
                    // В случае ошибки, добавляем в результат сообщение об ошибке
                    trackInfoResult.add(new TrackingResultAdd(trackNumber, "Ошибка: " + e.getMessage()));
                }
            }
        }

        // Логирование полученных трек-номеров
        System.out.println("Трек-номера: " + trackInfoResult);

        return trackInfoResult;
    }

    private TrackInfoListDTO processTrackingNumber(String number) {
        // Используем уже существующую службу для получения информации по трек-номеру
        return typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(number);
    }
}