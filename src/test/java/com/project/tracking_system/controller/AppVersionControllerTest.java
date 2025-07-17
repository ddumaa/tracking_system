package com.project.tracking_system.controller;

import com.project.tracking_system.dto.AppVersionDTO;
import com.project.tracking_system.service.admin.AppInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Проверка {@link AppVersionController}.
 */
@ExtendWith(MockitoExtension.class)
class AppVersionControllerTest {

    @Mock
    private AppInfoService appInfoService;

    @InjectMocks
    private AppVersionController controller;

    @Test
    void getVersion_ReturnsVersion() {
        when(appInfoService.getApplicationVersion()).thenReturn("1.2.3");

        ResponseEntity<AppVersionDTO> response = controller.getVersion();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("1.2.3", response.getBody().getVersion());
    }
}
