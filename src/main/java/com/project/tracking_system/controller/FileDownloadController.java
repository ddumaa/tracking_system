package com.project.tracking_system.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.project.tracking_system.utils.ResponseBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * @author Dmitriy Anisimov
 * @date 14.03.2025
 */
@RestController
public class FileDownloadController {

    /**
     * Эндпоинт для скачивания образца файла.
     *
     * @return ResponseEntity с файлом для скачивания
     */
    @GetMapping("/download-sample")
    public ResponseEntity<Resource> downloadSample() {
        // Имя файла, которое должно использоваться при скачивании
        String filename = "Пример заполнения.XLSX";
        // Загружаем ресурс из папки sample в classpath
        Resource resource = new ClassPathResource("sample/" + filename);

        if (!resource.exists()) {
            return ResponseBuilder.error(HttpStatus.NOT_FOUND, "Файл не найден");
        }

        // Используем ContentDisposition builder для корректного формирования заголовка
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

}
