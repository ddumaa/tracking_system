package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.TrackFacade;
import com.project.tracking_system.service.track.TrackServiceClassifier;
import com.project.tracking_system.service.track.BelPostManualService;
import com.project.tracking_system.service.registration.PreRegistrationService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.utils.TrackNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер для отображения главной страницы и отслеживания посылок.
 * <p>
 * Обрабатывает домашнюю страницу и взаимодействует с сервисами отслеживания.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@RequiredArgsConstructor
@Slf4j
@Controller
@RequestMapping("/app")
public class HomeController {

    private final TrackFacade trackFacade;
    private final StoreService storeService;
    /** Сервис классификации трек-номеров по типу почтовой службы. */
    private final TrackServiceClassifier trackServiceClassifier;
    /** Сервис постановки в очередь треков Белпочты. */
    private final BelPostManualService belPostManualService;
    /** Сервис предрегистрации посылок. */
    private final PreRegistrationService preRegistrationService;
    /** Сервис работы с покупателями. */
    private final CustomerService customerService;

    /**
     * Обрабатывает запросы на главной странице. Отображает домашнюю страницу.
     *
     * @return имя представления домашней страницы
     */
    @GetMapping
    public String home(@AuthenticationPrincipal User user, Model model) {
        if (user != null) {
            // Получаем магазины пользователя
            List<Store> stores = storeService.getUserStores(user.getId());
            model.addAttribute("stores", stores);
        }
        return "app/home";
    }

    /**
     * Обрабатывает POST-запросы на главной странице.
     * <p>
     * Выполняет отслеживание посылки по номеру.
     * <p>
     * Номера Белпочты помещаются в очередь через {@link BelPostManualService}
     * и обрабатываются асинхронно. Для остальных номеров информация
     * загружается синхронно через {@link TrackFacade}, который при необходимости
     * сохраняет трек в систему. При указании телефона трек связывается с покупателем.
     * </p>
     * </p>
     *
     * @param number              номер посылки
     * @param storeId             идентификатор магазина
     * @param phone               телефон покупателя
     * @param preRegistered      признак предрегистрации
     * @param fullName           ФИО покупателя
     * @param registrationSource источник регистрации
     * @param model              модель представления
     * @param user               аутентифицированный пользователь
     * @return имя представления домашней страницы
     */
    @PostMapping
    public String home(@ModelAttribute("number") String number,
                       @RequestParam(value = "storeId", required = false) Long storeId,
                       @RequestParam(value = "phone", required = false) String phone,
                       @RequestParam(value = "preRegistered", required = false) Boolean preRegistered,
                       @RequestParam(value = "fullName", required = false) String fullName,
                       @RequestParam(value = "registrationSource", required = false) String registrationSource,
                       Model model,
                       @AuthenticationPrincipal User user) {
        Long userId = user != null ? user.getId() : null;

        boolean canSave = userId != null;

        model.addAttribute("number", number);

        // Получаем магазины пользователя и определяем ID магазина
        List<Store> stores = userId != null ? storeService.getUserStores(userId) : List.of();
        storeId = storeService.resolveStoreId(storeId, stores);
        model.addAttribute("stores", stores);

        String normalizedNumber = TrackNumberUtils.normalize(number);

        try {
            PostalServiceType type = trackServiceClassifier.detect(normalizedNumber);

            // Обрабатываем предрегистрацию, если пользователь указал соответствующий флаг
            handlePreRegistration(preRegistered, normalizedNumber, registrationSource, storeId, userId);

            if (type == PostalServiceType.BELPOST && userId != null) {
                boolean queued = belPostManualService.enqueueIfAllowed(normalizedNumber, storeId, userId, phone);
                if (queued) {
                    model.addAttribute("successMessage", "Номер добавлен в очередь обработки.");
                } else {
                    model.addAttribute("customError", "Трек уже обновлялся недавно или имеет финальный статус.");
                }
                return "app/home";
            }

            // Получаем данные о треке через фасад, который также сохранит его при необходимости
            TrackInfoListDTO trackInfo = trackFacade.processTrack(
                    normalizedNumber, storeId, userId, canSave, phone);

            if (trackInfo == null || trackInfo.getList().isEmpty()) {
                model.addAttribute("customError", "Нет данных для указанного номера посылки.");
                log.warn("Нет данных для номера: {}", number);
                return "app/home";
            }

            model.addAttribute("trackInfo", trackInfo);

            // Обновляем ФИО покупателя при наличии телефона
            updateCustomerName(phone, fullName);
        } catch (IllegalArgumentException e) {
            model.addAttribute("customError", e.getMessage());
            log.warn("Ошибка: {}", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("generalError", "Произошла ошибка при обработке запроса.");
            log.error("Общая ошибка: {}", e.getMessage(), e);
        }

        return "app/home";
    }

    /**
     * Выполняет предрегистрацию трека через соответствующий сервис.
     *
     * @param preRegistered      признак предрегистрации
     * @param number             номер трека
     * @param registrationSource источник регистрации
     * @param storeId            идентификатор магазина
     * @param userId             идентификатор пользователя
     */
    private void handlePreRegistration(Boolean preRegistered,
                                       String number,
                                       String registrationSource,
                                       Long storeId,
                                       Long userId) {
        if (preRegistered == null || !preRegistered) {
            return;
        }
        preRegistrationService.preRegister(number, registrationSource, storeId, userId);
    }

    /**
     * Обновляет ФИО покупателя, если оно передано вместе с телефоном.
     *
     * @param phone    номер телефона покупателя
     * @param fullName новое ФИО
     */
    private void updateCustomerName(String phone, String fullName) {
        if (phone == null || phone.isBlank() || fullName == null || fullName.isBlank()) {
            return;
        }
        Customer customer = customerService.registerOrGetByPhone(phone);
        customerService.updateCustomerName(customer, fullName, NameSource.MERCHANT_PROVIDED);
    }
}
