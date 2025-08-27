package com.project.tracking_system.controller;

import com.project.tracking_system.dto.CustomerInfoDTO;
import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.service.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link CustomerController}.
 * <p>
 * Проверяем корректность обслуживания формы редактирования
 * и обновления номера телефона покупателя.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private CustomerController controller;

    /**
     * Проверяем, что форма редактирования
     * возвращается с корректными данными покупателя.
     */
    @Test
    void getCustomerByParcelId_ReturnsCustomerInfoFragment() {
        CustomerInfoDTO dto = new CustomerInfoDTO(
                "375291234567",
                "Иван Иванов",
                NameSource.MERCHANT_PROVIDED,
                1,
                1,
                0,
                100.0,
                BuyerReputation.RELIABLE
        );
        when(customerService.getCustomerInfoByParcelId(7L)).thenReturn(dto);
        Model model = new ExtendedModelMap();

        String view = controller.getCustomerByParcelId(7L, model);

        assertEquals("partials/customer-info", view);
        assertEquals(dto, model.getAttribute("customerInfo"));
        assertFalse((Boolean) model.getAttribute("notFound"));
        assertEquals(7L, model.getAttribute("trackId"));
    }

    /**
     * Проверяем, что при отправке нового номера
     * вызывается сервис обновления и возвращается тот же фрагмент.
     */
    @Test
    void changeCustomer_UpdatesPhoneAndDelegatesToService() {
        CustomerInfoDTO dto = new CustomerInfoDTO(
                "375299999999",
                "Пётр Петров",
                NameSource.USER_CONFIRMED,
                2,
                1,
                0,
                50.0,
                BuyerReputation.RELIABLE
        );
        when(customerService.assignCustomerToParcel(7L, "375299999999"))
                .thenReturn(dto);
        Model model = new ExtendedModelMap();

        String view = controller.changeCustomer(7L, "375299999999", model);

        assertEquals("partials/customer-info", view);
        assertEquals(dto, model.getAttribute("customerInfo"));
        assertFalse((Boolean) model.getAttribute("notFound"));
        assertEquals(7L, model.getAttribute("trackId"));
        verify(customerService).assignCustomerToParcel(7L, "375299999999");
    }

    /**
     * Проверяем успешный ответ при запросе информации по телефону.
     */
    @Test
    void getCustomerNameByPhone_ReturnsOk() {
        CustomerInfoDTO dto = new CustomerInfoDTO(
                "375291234567",
                "Иван Иванов",
                NameSource.MERCHANT_PROVIDED,
                1,
                1,
                0,
                100.0,
                BuyerReputation.RELIABLE
        );
        when(customerService.getCustomerInfoByPhone("375291234567"))
                .thenReturn(Optional.of(dto));

        ResponseEntity<CustomerInfoDTO> response = controller.getCustomerNameByPhone("375291234567");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    /**
     * Проверяем, что возвращается статус 404, если покупатель не найден.
     */
    @Test
    void getCustomerNameByPhone_ReturnsNotFound() {
        when(customerService.getCustomerInfoByPhone("375299999999"))
                .thenReturn(Optional.empty());

        ResponseEntity<CustomerInfoDTO> response = controller.getCustomerNameByPhone("375299999999");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }
}
