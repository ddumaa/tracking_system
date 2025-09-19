package com.project.tracking_system.controller;

import com.project.tracking_system.dto.AdminNotificationForm;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.AdminNotificationStatus;
import com.project.tracking_system.service.admin.AdminNotificationService;
import com.project.tracking_system.service.admin.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AdminController} force update flow.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @Mock
    private AdminNotificationService adminNotificationService;

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

    @Test
    void createNotification_DelegatesToServiceAndRedirects() {
        AdminNotificationForm form = new AdminNotificationForm();
        form.setTitle("Обновление");
        form.setBody("Первая строка\nВторая строка");

        AdminNotification created = new AdminNotification();
        created.setId(42L);
        created.setTitle("Обновление");
        created.setStatus(AdminNotificationStatus.ACTIVE);
        created.setResetRequested(true);
        when(adminNotificationService.createNotification(eq("Обновление"), eq(List.of("Первая строка", "Вторая строка"))))
                .thenReturn(created);

        RedirectAttributes attrs = new RedirectAttributesModelMap();
        String view = controller.createNotification(form, attrs);

        assertEquals("redirect:/admin/notifications", view);
        assertEquals("Уведомление создано", attrs.getFlashAttributes().get("successMessage"));
        assertEquals(created, attrs.getFlashAttributes().get("createdNotification"));
        AdminNotification flashNotification = (AdminNotification) attrs.getFlashAttributes().get("createdNotification");
        assertEquals(AdminNotificationStatus.ACTIVE, flashNotification.getStatus());
        assertTrue(flashNotification.isResetRequested());
        verify(adminNotificationService).createNotification(eq("Обновление"), eq(List.of("Первая строка", "Вторая строка")));
    }

    @Test
    void activateNotification_SetsFlashMessage() {
        RedirectAttributes attrs = new RedirectAttributesModelMap();

        String view = controller.activateNotification(5L, attrs);

        assertEquals("redirect:/admin/notifications", view);
        assertEquals("Уведомление активировано", attrs.getFlashAttributes().get("successMessage"));
        verify(adminNotificationService).activateNotification(5L);
    }

    @Test
    void resetNotification_DelegatesToService() {
        RedirectAttributes attrs = new RedirectAttributesModelMap();

        String view = controller.resetNotification(7L, attrs);

        assertEquals("redirect:/admin/notifications", view);
        assertEquals("Показ уведомления будет повторён", attrs.getFlashAttributes().get("successMessage"));
        verify(adminNotificationService).requestReset(7L);
    }
}
