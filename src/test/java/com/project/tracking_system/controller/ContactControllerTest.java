package com.project.tracking_system.controller;

import com.project.tracking_system.configuration.GlobalControllerAdvice;
import com.project.tracking_system.dto.ContactFormRequest;
import com.project.tracking_system.service.captcha.CaptchaService;
import com.project.tracking_system.service.contact.ContactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Тесты контроллера страницы контактов.
 */
// Ограничиваем сканирование контекста только тестируемым контроллером и исключаем глобальный совет,
// требующий дополнительных зависимостей и конфигураций.
@WebMvcTest(value = ContactController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = GlobalControllerAdvice.class))
@AutoConfigureMockMvc(addFilters = false)
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContactService contactService;

    @MockBean
    private CaptchaService captchaService;

    /**
     * Проверяет, что GET-запрос отображает форму с пустыми данными и ключом reCAPTCHA.
     * <p>
     * Данный тест демонстрирует ответственность контроллера за подготовку данных,
     * необходимых представлению, не затрагивая работу других слоев приложения.
     * </p>
     */
    @Test
    void contactPage_returnsViewWithCaptchaKey() throws Exception {
        // Возвращаем тестовый ключ сайта для рендеринга виджета
        when(captchaService.getSiteKey()).thenReturn("site-key");

        mockMvc.perform(get("/contacts"))
                .andExpect(status().isOk())
                .andExpect(view().name("marketing/contacts"))
                .andExpect(model().attributeExists("contactForm", "recaptchaSiteKey"))
                .andExpect(model().attribute("recaptchaSiteKey", "site-key"));

        // Убеждаемся, что ключ действительно был запрошен у сервиса
        verify(captchaService).getSiteKey();
    }

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

        verify(captchaService).verifyToken(anyString(), anyString());
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

        verify(captchaService).verifyToken(anyString(), anyString());
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

        verify(captchaService).verifyToken(anyString(), anyString());
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

    /**
     * Проверяет, что при превышении лимита запросов отображается форма с ошибкой.
     */
    @Test
    void submitContactForm_whenRateLimitExceeded_returnsContactView() throws Exception {
        when(captchaService.verifyToken(anyString(), anyString())).thenReturn(true);
        doThrow(new com.project.tracking_system.exception.RateLimitExceededException("too many"))
                .when(contactService).processContactRequest(any(ContactFormRequest.class), anyString());

        mockMvc.perform(post("/contacts/submit")
                        .param("name", "Иван")
                        .param("email", "test@example.com")
                        .param("message", "Сообщение")
                        .param("g-recaptcha-response", "token"))
                .andExpect(status().isOk())
                .andExpect(view().name("marketing/contacts"))
                .andExpect(model().attributeHasErrors("contactForm"));

        verify(captchaService).verifyToken(anyString(), anyString());
        verify(contactService).processContactRequest(any(ContactFormRequest.class), anyString());
    }
}
