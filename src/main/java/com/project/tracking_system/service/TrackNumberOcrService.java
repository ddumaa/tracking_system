package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrackNumberOcrService {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final TrackParcelService trackParcelService;

    @Autowired
    public TrackNumberOcrService(TypeDefinitionTrackPostService typeDefinitionTrackPostService, TrackParcelService trackParcelService) {
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.trackParcelService = trackParcelService;
    }

    @PostConstruct
    public void init() {
        System.load("/usr/lib/jni/libopencv_java460.so");
    }

    public String processImage(MultipartFile file) throws IOException {
        try {
            BufferedImage preprocessedImage = preprocessImage(file);
            return recognizeText(preprocessedImage);
        } catch (TesseractException e) {
            throw new RuntimeException("Ошибка при распознавании текста: " + e.getMessage(), e);
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

        // Преобразование в Mat для дальнейшей обработки с OpenCV
        Mat mat = bufferedImageToMat(grayscaleImage);

        // Удаление шума с помощью медианного размытия
        Imgproc.medianBlur(mat, mat, 3);

        // Применение адаптивной бинаризации
        Mat binaryMat = new Mat();
        Imgproc.adaptiveThreshold(mat, binaryMat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, 10);

        // Выравнивание изображения
        //Mat deskewedMat = deskewImage(binaryMat);

        return matToBufferedImage(binaryMat);
    }

    private Mat deskewImage(Mat binaryMat) {
        // Найти контуры для анализа наклона
        Mat lines = new Mat();
        Imgproc.HoughLinesP(binaryMat, lines, 1, Math.PI / 180, 100, 100, 10);

        double angle = 0;
        int count = 0;

        // Рассчитать средний угол всех линий
        for (int i = 0; i < lines.rows(); i++) {
            double[] line = lines.get(i, 0);
            double dx = line[2] - line[0];
            double dy = line[3] - line[1];
            if (dx != 0) {
                angle += Math.atan2(dy, dx);
                count++;
            }
        }

        if (count > 0) {
            angle = angle / count; // Средний угол
        }

        angle = Math.toDegrees(angle); // Преобразование в градусы

        // Корректировать изображение
        Point center = new Point(binaryMat.cols() / 2, binaryMat.rows() / 2);
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(new org.opencv.core.Point(center.x, center.y), angle, 1.0);
        Mat rotated = new Mat();
        Imgproc.warpAffine(binaryMat, rotated, rotationMatrix, new Size(binaryMat.cols(), binaryMat.rows()), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(255));

        return rotated;
    }

    public String recognizeText(BufferedImage image) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/local/share/tessdata");
        tesseract.setLanguage("rus+eng");
        tesseract.setVariable("user_defined_dpi", "300");
        tesseract.setPageSegMode(3);
        tesseract.setOcrEngineMode(1); // Только LSTM

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