package com.project.tracking_system.controller;

import com.project.tracking_system.dto.ContactFormRequest;
import com.project.tracking_system.service.captcha.CaptchaService;
import com.project.tracking_system.service.contact.ContactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Тесты контроллера страницы контактов.
 */
@WebMvcTest(ContactController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContactService contactService;

    @MockBean
    private CaptchaService captchaService;

    /**
     * Проверяет, что при ошибках валидации пользователь остаётся на странице формы.
     */
    @Test
    void submitContactForm_whenValidationFails_returnsContactView() throws Exception {
        when(captchaService.verifyToken(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/contacts/submit")
                        .param("name", "")
                        .param("email", "invalid")
                        .param("message", "")
                        .param("g-recaptcha-response", "token"))
                .andExpect(status().isOk())
                .andExpect(view().name("marketing/contacts"));

        verify(contactService, never()).processContactRequest(any(ContactFormRequest.class), anyString());
    }

    /**
     * Проверяет, что при корректных данных происходит редирект и вызов сервиса.
     */
    @Test
    void submitContactForm_whenValid_redirects() throws Exception {
        when(captchaService.verifyToken(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/contacts/submit")
                        .param("name", "Иван")
                        .param("email", "test@example.com")
                        .param("message", "Сообщение")
                        .param("g-recaptcha-response", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts"));

        verify(contactService).processContactRequest(any(ContactFormRequest.class), anyString());
    }

    /**
     * Проверяет, что при неверной капче запрос блокируется.
     */
    @Test
    void submitContactForm_withInvalidCaptcha_returnsContactView() throws Exception {
        when(captchaService.verifyToken(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/contacts/submit")
                        .param("name", "Иван")
                        .param("email", "test@example.com")
                        .param("message", "Сообщение")
                        .param("g-recaptcha-response", "bad"))
                .andExpect(status().isOk())
                .andExpect(view().name("marketing/contacts"));

        verify(contactService, never()).processContactRequest(any(ContactFormRequest.class), anyString());
    }

    /**
     * Проверяет, что при отсутствии капчи запрос блокируется.
     */
    @Test
    void submitContactForm_withoutCaptcha_returnsContactView() throws Exception {
        mockMvc.perform(post("/contacts/submit")
                        .param("name", "Иван")
                        .param("email", "test@example.com")
                        .param("message", "Сообщение"))
                .andExpect(status().isOk())
                .andExpect(view().name("marketing/contacts"));

        verify(captchaService, never()).verifyToken(anyString(), anyString());
        verify(contactService, never()).processContactRequest(any(ContactFormRequest.class), anyString());
    }
}
