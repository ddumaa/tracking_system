package com.project.tracking_system.controller;

import com.project.tracking_system.model.customer.CustomerNameDecision;
import com.project.tracking_system.service.customer.CustomerNameEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контроллер решения покупателя по изменению ФИО.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer")
public class CustomerNameDecisionController {

    private final CustomerNameEventService eventService;

    /**
     * Применить действие покупателя к заявке на подтверждение ФИО.
     *
     * @param eventId    идентификатор заявки
     * @param customerId идентификатор покупателя
     * @param decision   действие покупателя
     * @param newName    новое ФИО, если выбрано {@code CHANGE}
     * @return код 200 при успешном обновлении, иначе 404
     */
    @PostMapping("/name-decision")
    public ResponseEntity<Void> decide(@RequestParam Long eventId,
                                       @RequestParam Long customerId,
                                       @RequestParam CustomerNameDecision decision,
                                       @RequestParam(required = false) String newName) {
        boolean updated = switch (decision) {
            case YES -> eventService.confirmFromTelegram(eventId, customerId);
            case NO -> eventService.rejectFromTelegram(eventId, customerId);
            case CHANGE -> eventService.changeFromTelegram(eventId, customerId, newName);
        };
        return updated ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
