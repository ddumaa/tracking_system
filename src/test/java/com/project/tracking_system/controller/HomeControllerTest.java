package com.project.tracking_system.controller;

import com.project.tracking_system.dto.CustomerInfoDTO;
import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.registration.PreRegistrationService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.BelPostManualService;
import com.project.tracking_system.service.track.TrackFacade;
import com.project.tracking_system.service.track.TrackServiceClassifier;
import com.project.tracking_system.service.admin.AppInfoService;
import com.project.tracking_system.service.ratelimit.Bucket4jRateLimiter;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

/**
 * Тесты для {@link HomeController}.
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;
    @MockBean
    private AppInfoService appInfoService;
    @MockBean
    private Bucket4jRateLimiter bucket4jRateLimiter; // Мокаем ограничитель для ApiRateLimitFilter

    /**
     * Проверяем, что при подтверждённом пользователем имени поле ФИО
     * отображается только для чтения, а переключатель отключён.
     *
     * Предусловие: покупатель с указанным телефоном найден и подтвердил своё имя.
     * Ожидаемое поведение: HTML содержит атрибуты readonly и disabled.
     */
    @Test
    void home_WithConfirmedName_DisablesEditing() throws Exception {
        CustomerInfoDTO dto = new CustomerInfoDTO(
                "375291112233",
                "Иван Иванов",
                NameSource.USER_CONFIRMED,
                0, 0, 0,
                0.0,
                BuyerReputation.RELIABLE
        );
        when(customerService.getCustomerInfoByPhone("375291112233"))
                .thenReturn(Optional.of(dto));
        // Мокаем получение версии приложения, используемой глобальным советом контроллеров
        when(appInfoService.getApplicationVersion()).thenReturn("1.0");

        // Мокаем бакеты, чтобы фильтр ограничения запросов не вызывал реальных зависимостей
        when(bucket4jRateLimiter.resolveCustomerBucket(Mockito.anyString()))
                .thenReturn(Mockito.mock(Bucket.class));
        when(bucket4jRateLimiter.resolveTelegramBucket(Mockito.anyString()))
                .thenReturn(Mockito.mock(Bucket.class));

        mockMvc.perform(get("/app").param("phone", "375291112233"))
                .andExpect(status().isOk())
                .andExpect(xpath("//input[@id='fullName'][@readonly]").exists())
                .andExpect(xpath("//input[@id='toggleFullName'][@disabled]").exists());
    }
}
