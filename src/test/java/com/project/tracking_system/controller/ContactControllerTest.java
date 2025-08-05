package com.project.tracking_system.controller;

import com.project.tracking_system.dto.ContactFormRequest;
import com.project.tracking_system.service.captcha.CaptchaService;
import com.project.tracking_system.service.contact.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link ContactController}.
 */
@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    @Mock
    private ContactService contactService;

    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private ContactController controller;

    /**
     * Проверяет, что при открытии страницы контактов
     * модель заполняется необходимыми атрибутами.
     */
    @Test
    void contactPage_ShouldPrepareModel() {
        Model model = new ExtendedModelMap();
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(captchaService.getSiteKey()).thenReturn("site-key");
        when(request.getAttribute("nonce")).thenReturn("test-nonce");

        String view = controller.contactPage(model, request);

        assertEquals("marketing/contacts", view);
        assertNotNull(model.getAttribute("contactForm"));
        assertEquals("site-key", model.getAttribute("recaptchaSiteKey"));
        assertEquals("test-nonce", model.getAttribute("nonce"));
    }

    /**
     * Проверяет, что при неверной капче
     * страница возвращается с заполненной моделью и ошибкой.
     */
    @Test
    void submitContactForm_InvalidCaptcha_ReturnsForm() {
        ContactFormRequest form = new ContactFormRequest("Иван", "test@example.com", "Привет");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "contactForm");
        HttpServletRequest request = mock(HttpServletRequest.class);
        Model model = new ExtendedModelMap();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(captchaService.getSiteKey()).thenReturn("site-key");
        when(request.getAttribute("nonce")).thenReturn("test-nonce");

        String view = controller.submitContactForm(
                form,
                bindingResult,
                null,
                request,
                model,
                redirectAttributes);

        assertEquals("marketing/contacts", view);
        assertTrue(bindingResult.hasErrors(), "Ожидалась ошибка валидации");
        assertEquals("site-key", model.getAttribute("recaptchaSiteKey"));
        assertEquals("test-nonce", model.getAttribute("nonce"));
    }
}

