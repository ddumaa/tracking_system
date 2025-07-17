package com.project.tracking_system.controller;

import com.project.tracking_system.dto.AppVersionDTO;
import com.project.tracking_system.service.admin.AppInfoService;
import com.project.tracking_system.utils.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер для получения версии приложения.
 */
@RestController
@RequiredArgsConstructor
public class AppVersionController {

    private final AppInfoService appInfoService;

    /**
     * Возвращает текущую версию приложения в формате JSON.
     *
     * @return {@link ResponseEntity} с {@link AppVersionDTO}
     */
    @GetMapping("/app/version")
    public ResponseEntity<AppVersionDTO> getVersion() {
        AppVersionDTO dto = new AppVersionDTO(appInfoService.getApplicationVersion());
        return ResponseBuilder.ok(dto);
    }
}
