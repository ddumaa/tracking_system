package com.project.tracking_system.controller;

import com.project.tracking_system.dto.CustomerInfoDTO;
import com.project.tracking_system.service.customer.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Контроллер для получения информации о покупателях.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/app/customers")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Возвращает данные покупателя по идентификатору посылки.
     *
     * @param parcelId идентификатор посылки
     * @param model    модель для передачи данных во фрагмент
     * @return имя Thymeleaf-фрагмента
     */
    @GetMapping("/parcel/{parcelId}")
    public String getCustomerByParcelId(@PathVariable Long parcelId, Model model) {
        CustomerInfoDTO dto = customerService.getCustomerInfoByParcelId(parcelId);
        model.addAttribute("customerInfo", dto);
        model.addAttribute("notFound", dto == null);
        model.addAttribute("trackId", parcelId);
        return "partials/customer-info";
    }

    /**
     * Привязывает покупателя к посылке по номеру телефона.
     *
     * @param trackId идентификатор посылки
     * @param phone   номер телефона покупателя
     * @param model   модель для передачи данных во фрагмент
     * @return HTML-фрагмент с обновлённой информацией о покупателе
     */
    @PostMapping("/assign")
    public String assignCustomer(@RequestParam Long trackId,
                                 @RequestParam String phone,
                                 Model model) {
        return updateCustomer(trackId, phone, model);
    }

    /**
     * Изменяет номер телефона покупателя, связанного с посылкой.
     * <p>
     * Метод используется при редактировании существующей привязки. После
     * сохранения возвращается тот же Thymeleaf-фрагмент с обновлёнными данными.
     * </p>
     *
     * @param trackId идентификатор посылки
     * @param phone   новый номер телефона
     * @param model   модель для передачи данных во фрагмент
     * @return HTML-фрагмент с актуальной информацией о покупателе
     */
    @PostMapping("/change")
    public String changeCustomer(@RequestParam Long trackId,
                                 @RequestParam String phone,
                                 Model model) {
        return updateCustomer(trackId, phone, model);
    }

    /**
     * Выполняет привязку покупателя к посылке и формирует модель представления.
     * Выделено в отдельный метод для повторного использования в разных
     * обработчиках.
     */
    private String updateCustomer(Long trackId, String phone, Model model) {
        CustomerInfoDTO dto = customerService.assignCustomerToParcel(trackId, phone);
        model.addAttribute("customerInfo", dto);
        model.addAttribute("notFound", dto == null);
        model.addAttribute("trackId", trackId);
        return "partials/customer-info";
    }
}
