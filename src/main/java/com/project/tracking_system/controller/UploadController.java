package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.model.TrackingResponse;
import com.project.tracking_system.service.track.TrackNumberOcrService;
import com.project.tracking_system.service.track.TrackingNumberServiceXLS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.project.tracking_system.entity.User;

import java.io.IOException;
import java.util.List;

/**
 * Контроллер для загрузки файлов и распознавания номеров посылок.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/")
public class UploadController {

    private final TrackingNumberServiceXLS trackingNumberServiceXLS;
    private final TrackNumberOcrService trackNumberOcrService;

    /**
     * Обрабатывает загрузку файла (XLS, XLSX или изображения).
     *
     * @param file загружаемый файл
     * @param storeId идентификатор магазина (может быть null)
     * @param model модель для добавления данных в представление
     * @param user текущий пользователь
     * @return имя представления домашней страницы
     */
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "storeId", required = false) Long storeId,
                             Model model,
                             @AuthenticationPrincipal User user) {
        Long userId = null;

        if (user != null) {
            userId = user.getId();
        }

        if (file.isEmpty()) {
            model.addAttribute("customError", "Пожалуйста, выберите XLS, XLSX или изображение для загрузки.");
            return "home";
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            model.addAttribute("customError", "Не удалось определить тип файла.");
            return "home";
        }

        try {
            if (contentType.equals("application/vnd.ms-excel") || contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                TrackingResponse trackingResponse = trackingNumberServiceXLS.processTrackingNumber(file, userId);

                log.info("Передаём в модель limitExceededMessage: {}", trackingResponse.getLimitExceededMessage());

                model.addAttribute("trackingResults", trackingResponse.getTrackingResults());
                model.addAttribute("limitExceededMessage", trackingResponse.getLimitExceededMessage());
            } else if (contentType.startsWith("image/")) {
                String recognizedText = trackNumberOcrService.processImage(file);
                List<TrackingResultAdd> trackingResults = trackNumberOcrService.extractAndProcessTrackingNumbers(recognizedText, storeId, userId);
                model.addAttribute("trackingResults", trackingResults);

                return "home";
            } else {
                model.addAttribute("customError", "Неподдерживаемый тип файла. Загрузите XLS, XLSX или изображение.");
                return "home";
            }
        } catch (IOException e) {
            model.addAttribute("generalError", "Ошибка при обработке файла: " + e.getMessage());
            log.error("IOException при обработке файла: {}", e.getMessage(), e);
        } catch (Exception e) {
            model.addAttribute("generalError", "Ошибка: " + e.getMessage());
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
        }

        return "home";
    }
}
