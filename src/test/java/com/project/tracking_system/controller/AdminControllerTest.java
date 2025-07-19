package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.service.admin.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AdminController} force update flow.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController controller;

    @Test
    void forceUpdateParcel_AddsFlashAttribute() {
        TrackingResultAdd result = new TrackingResultAdd("A1", "ok");
        when(adminService.forceUpdateParcel(1L)).thenReturn(result);

        RedirectAttributes attrs = new RedirectAttributesModelMap();
        String view = controller.forceUpdateParcel(1L, attrs);

        assertEquals("redirect:/admin/parcels/1", view);
        assertEquals("ok", attrs.getFlashAttributes().get("updateStatus"));
        verify(adminService).forceUpdateParcel(1L);
    }
}
