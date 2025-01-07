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
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сервис для обработки изображений с номерами отслеживания, включая их предобработку и распознавание текста с помощью OCR.
 * <p>
 * Этот сервис использует библиотеку Tesseract для распознавания текста и OpenCV для предварительной обработки изображений.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date Добавлено 07.01.2025
 */
@Service
public class TrackNumberOcrService {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final TrackParcelService trackParcelService;

    /**
     * Конструктор сервиса.
     * @param typeDefinitionTrackPostService Сервис для получения информации о трек-номере.
     * @param trackParcelService Сервис для сохранения данных о посылках.
     */
    @Autowired
    public TrackNumberOcrService(TypeDefinitionTrackPostService typeDefinitionTrackPostService, TrackParcelService trackParcelService) {
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.trackParcelService = trackParcelService;
    }

    /**
     * Инициализация библиотеки OpenCV.
     */
    @PostConstruct
    public void init() {
        System.load("/usr/lib/jni/libopencv_java460.so");
    }

    /**
     * Обрабатывает изображение, извлекая с него текст с номером отслеживания.
     * <p>
     * Изображение передается через MultipartFile, далее оно проходит предобработку, и затем распознается текст с использованием Tesseract.
     * </p>
     * @param file Изображение с номером отслеживания.
     * @return Извлеченный номер отслеживания в виде строки.
     * @throws IOException Если возникла ошибка при обработке изображения.
     * @throws TesseractException Если возникла ошибка при распознавании текста.
     */
    public String processImage(MultipartFile file) throws IOException {
        try {
            BufferedImage preprocessedImage = preprocessImage(file);
            return recognizeText(preprocessedImage);
        } catch (TesseractException e) {
            throw new RuntimeException("Ошибка при распознавании текста: " + e.getMessage(), e);
        }
    }

    /**
     * Выполняет предобработку изображения: преобразование в оттенки серого, медианное размытие и бинаризацию.
     * @param file Изображение для предобработки.
     * @return Изображение после предобработки в виде объекта BufferedImage.
     * @throws IOException Если возникла ошибка при чтении изображения.
     */
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

        return matToBufferedImage(binaryMat);
    }

    /**
     * Распознает текст на изображении с использованием Tesseract OCR.
     * @param image Изображение, с которого нужно распознать текст.
     * @return Распознанный текст.
     * @throws TesseractException Если возникла ошибка при распознавании текста.
     */
    public String recognizeText(BufferedImage image) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/local/share/tessdata");
        tesseract.setLanguage("rus+eng");
        tesseract.setVariable("user_defined_dpi", "300");
        tesseract.setPageSegMode(3);
        tesseract.setOcrEngineMode(1); // Только LSTM

        return tesseract.doOCR(image);
    }

    /**
     * Извлекает и обрабатывает номера отслеживания из распознанного текста.
     * <p>
     * Каждый найденный трек-номер обрабатывается, и для каждого номера выполняется сохранение данных о посылке.
     * </p>
     * @param text Распознанный текст с изображений.
     * @param authenticatedUser Пользователь, который выполнил запрос.
     * @return Список объектов TrackingResultAdd, содержащих результат обработки каждого трек-номера.
     */
    public List<TrackingResultAdd> extractAndProcessTrackingNumbers(String text, String authenticatedUser) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Нормализуем текст
        text = normalizeText(text);

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

    private String normalizeText(String text) {
        // Шаблон для поиска всех цифр подряд
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(text);

        // Поиск всех числовых последовательностей в строке
        while (matcher.find()) {
            String digits = matcher.group(); // Все найденные цифры

            // Если чисел больше или равно 12, то берём последние 12 цифр
            if (digits.length() >= 12 && digits.length() <= 14) {
                String last12Digits = digits.substring(digits.length() - 12); // Берём последние 12 цифр
                text = text.replace(digits, "BY" + last12Digits); // Заменяем на "BY" + последние 12 цифр
            }
        }

        return text;
    }
}