package com.project.tracking_system.controller;

import com.project.tracking_system.dto.CustomerInfoDTO;
import com.project.tracking_system.service.customer.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Контроллер для получения информации о покупателях.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/customers")
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
        return "partials/customer-info";
    }
}
