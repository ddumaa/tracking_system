package com.project.tracking_system.controller;

import com.project.tracking_system.service.track.TrackUploadProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.project.tracking_system.entity.User;

import java.io.IOException;

/**
 * Контроллер для загрузки файлов и распознавания номеров посылок.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/app")
public class UploadController {

    private final TrackUploadProcessorService trackUploadProcessorService;

    /**
     * Обрабатывает загрузку Excel-файла с треками.
     * После успешной передачи файла запускается фоновая обработка,
     * о ходе которой пользователь уведомляется через WebSocket.
     *
     * @param file   загружаемый файл
     * @param storeId идентификатор магазина (может быть null)
     * @param model  модель представления
     * @param user   текущий пользователь
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
            model.addAttribute("customError", "Пожалуйста, выберите XLS или XLSX для загрузки.");
            return "app/home";
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            model.addAttribute("customError", "Не удалось определить тип файла.");
            return "app/home";
        }

        try {
            if (contentType.equals("application/vnd.ms-excel") || contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                trackUploadProcessorService.process(file, userId);
                // Таблица с результатами появится после получения первых данных
                // по WebSocket. На этом этапе её не заполняем.
                model.addAttribute("successMessage", "Файл принят, обработка начата.");
            } else {
                model.addAttribute("customError", "Неподдерживаемый тип файла. Загрузите XLS или XLSX.");
                return "app/home";
            }
        } catch (IOException e) {
            model.addAttribute("generalError", "Ошибка при обработке файла: " + e.getMessage());
            log.error("IOException при обработке файла: {}", e.getMessage(), e);
        } catch (Exception e) {
            model.addAttribute("generalError", "Ошибка: " + e.getMessage());
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
        }

        return "app/home";
    }
}
