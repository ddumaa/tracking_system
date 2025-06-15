package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.project.tracking_system.service.track.TrackFacade;

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
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackNumberOcrService {

    private final TrackFacade trackFacade;

    @Value("${opencv.lib.path}")
    private String opencvLibPath;

    @Value("${tesseract.datapath}")
    private String tesseractDataPath;

    /**
     * Инициализация библиотеки OpenCV.
     */
    @PostConstruct
    public void init() {
        System.load(opencvLibPath);
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
        tesseract.setDatapath(tesseractDataPath);
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
    public List<TrackingResultAdd> extractAndProcessTrackingNumbers(String text, Long storeId, Long userId) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        text = normalizeText(text); // Нормализуем текст

        Pattern trackPattern = Pattern.compile("(BY\\d{12}|(PC|BV|BP)\\d{9}BY)");
        String[] lines = text.split("\\R");
        List<TrackingResultAdd> trackInfoResult = new ArrayList<>();

        boolean canSave = userId != null;

        for (String line : lines) {
            line = line.trim();
            log.info("Обрабатываем строку: {}", line);

            Matcher matcher = trackPattern.matcher(line);
            while (matcher.find()) {
                String trackNumber = matcher.group();
                log.info("Найден трек-номер: {}", trackNumber);

                try {
                    // Используем processTrack для комплексной работы с треком.
                    TrackInfoListDTO trackInfo = trackFacade.processTrack(trackNumber, storeId, userId, canSave);

                    if (trackInfo != null) {
                        trackInfoResult.add(new TrackingResultAdd(trackNumber, "Добавлен"));
                    } else {
                        trackInfoResult.add(new TrackingResultAdd(trackNumber, "Нет данных"));
                    }
                } catch (IllegalArgumentException e) {
                    trackInfoResult.add(new TrackingResultAdd(trackNumber, "Ошибка: " + e.getMessage()));
                    log.warn("Ошибка обработки {}: {}", trackNumber, e.getMessage());
                } catch (Exception e) {
                    trackInfoResult.add(new TrackingResultAdd(trackNumber, "Ошибка обработки"));
                    log.error("Ошибка обработки {}: {}", trackNumber, e.getMessage(), e);
                }
            }
        }

        log.info("Обработанные трек-номера: {}", trackInfoResult);
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