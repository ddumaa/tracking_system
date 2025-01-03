package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
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
        System.out.println("Entering rocessImage method");
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

        // Применение адаптивной бинаризации (используем OpenCV для лучшей точности)
        Mat mat = bufferedImageToMat(grayscaleImage);
        Mat binaryMat = new Mat();
        Imgproc.adaptiveThreshold(mat, binaryMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 15, 10);
        return matToBufferedImage(binaryMat);
    }

    public String recognizeText(BufferedImage image) throws TesseractException {
        System.out.println("Entering recognizeText method");
        System.out.println("java.library.path: " + System.getProperty("java.library.path"));

        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/local/share/tessdata");
        tesseract.setLanguage("rus+eng");
        tesseract.setVariable("user_defined_dpi", "300");
        tesseract.setPageSegMode(6); // Анализ текста построчно
        tesseract.setOcrEngineMode(3); // Нейронные сети
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

    private Mat bufferedImageToMat(BufferedImage bi) {
        byte[] pixels = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC1);
        mat.put(0, 0, pixels);
        return mat;
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        mat.get(0, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData());
        return image;
    }
}