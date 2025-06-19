package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.TrackParcelAdminInfoDTO;
import com.project.tracking_system.dto.UserDetailsAdminInfoDTO;
import com.project.tracking_system.dto.UserListAdminInfoDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.analytics.StatsAggregationService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.admin.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Административный контроллер
 *
 * @author Dmitriy Anisimov
 * @date 07.02.2025
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminController {

    private final UserService userService;
    private final TrackParcelService trackParcelService;
    private final SubscriptionService subscriptionService;
    private final StoreRepository storeRepository;
    private final StatsAggregationService statsAggregationService;
    private final AdminService adminService;

    /**
     * Отображает дашборд администратора.
     * <p>
     * Метод подготавливает общую статистику по пользователям и отслеживаниям
     * и передает её в модель.
     * </p>
     *
     * @param model модель для передачи статистики во представление
     * @return имя шаблона панели администратора
     */
    @GetMapping()
    public String adminDashboard(Model model) {
        long totalUsers = userService.countUsers();
        long paidUsers = userService.countUsersBySubscriptionPlan("PREMIUM");
        long totalParcels = trackParcelService.countAllParcels();
        long totalCustomers = adminService.countCustomers();
        long telegramBound = adminService.countTelegramBoundCustomers();
        long storesCount = adminService.countStores();

        // Добавляем статистику в модель
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("paidUsers", paidUsers);
        model.addAttribute("totalParcels", totalParcels);
        model.addAttribute("totalCustomers", totalCustomers);
        model.addAttribute("telegramBound", telegramBound);
        model.addAttribute("storesCount", storesCount);

        return "admin/dashboard";
    }

    /**
     * Отображает список всех пользователей системы с возможностью фильтрации.
     *
     * @param search       строка поиска по email
     * @param role         фильтр по роли
     * @param subscription фильтр по подписке
     * @param model        модель для передачи данных
     * @return имя шаблона со списком пользователей
     */
    @GetMapping("/users")
    public String getAllUsers(@RequestParam(value = "search", required = false) String search,
                              @RequestParam(value = "role", required = false) String role,
                              @RequestParam(value = "subscription", required = false) String subscription,
                              Model model) {
        List<UserListAdminInfoDTO> users = adminService.getUsers(search, role, subscription);
        model.addAttribute("users", users);
        model.addAttribute("roles", Role.values());
        model.addAttribute("plans", adminService.getPlans());
        model.addAttribute("search", search);
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedSubscription", subscription);
        return "admin/user-list";
    }

    /**
     * Отображает форму создания нового пользователя.
     *
     * Загружает список доступных тарифов в модель.
     *
     * @param model модель для передачи тарифных планов
     * @return имя шаблона формы
     */
    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        model.addAttribute("plans", adminService.getPlans());
        return "admin/user-new";
    }

    /**
     * Создаёт нового пользователя по введённым данным.
     *
     * @param email    адрес почты
     * @param password пароль пользователя
     * @param role     роль пользователя
     * @param subscriptionPlan начальный тариф
     * @param model    модель для передачи сообщений
     * @return редирект на список пользователей или форма с ошибкой
     */
    @PostMapping("/users/new")
    public String createUser(@RequestParam String email,
                             @RequestParam String password,
                             @RequestParam String role,
                             @RequestParam("subscriptionPlan") String subscriptionPlan,
                             Model model) {
        try {
            userService.createUserByAdmin(email, password, role, subscriptionPlan);
            return "redirect:/admin/users";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("plans", adminService.getPlans());
            return "admin/user-new";
        }
    }

    /**
     * Отображает детальную информацию о выбранном пользователе.
     *
     * Загружает в модель список магазинов пользователя и связанные посылки.
     *
     * @param userId идентификатор пользователя
     * @param model  модель для передачи деталей пользователя
     * @return имя шаблона с деталями пользователя
     */
    @GetMapping("/users/{userId}")
    public String getUserDetails(@PathVariable Long userId, Model model) {
        User user = userService.findUserById(userId);

        // Загружаем магазины пользователя
        List<Store> stores = storeRepository.findByOwnerId(userId);
        Map<Long, List<TrackParcelDTO>> storeParcels = new HashMap<>();

        for (Store store : stores) {
            Long storeId = store.getId();
            List<TrackParcelDTO> parcels = trackParcelService.findAllByStoreTracks(storeId, userId);
            storeParcels.put(storeId, parcels);
        }

        // Получаем подписку пользователя (если есть)
        UserSubscription subscription = user.getSubscription();
        String subscriptionName = (subscription != null)
                ? subscription.getSubscriptionPlan().getName()
                : "NONE"; // Если подписки нет, указываем "NONE" или "FREE"

        String subscriptionEndDate = null;
        if (subscription != null && subscription.getSubscriptionEndDate() != null) {
            subscriptionEndDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(subscription.getSubscriptionEndDate());
        }

        UserDetailsAdminInfoDTO adminInfoDTO = new UserDetailsAdminInfoDTO(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                subscriptionName,
                subscriptionEndDate
        );

        model.addAttribute("user", adminInfoDTO);
        model.addAttribute("stores", stores); // Передаём магазины
        model.addAttribute("storeParcels", storeParcels); // Передаём посылки по магазинам
        return "admin/user-details";
    }


    /**
     * Обновляет роль пользователя.
     *
     * @param usersId идентификатор пользователя
     * @param role    новая роль
     * @return редирект на страницу деталей пользователя
     */
    @PostMapping("/users/{usersId}/role-update")
    public String updateUserRole(@PathVariable Long usersId,
                                 @RequestParam("role") String role) {
        userService.updateUserRole(usersId, role);
        return "redirect:/admin/users/" + usersId;
    }

    /**
     * Изменяет подписку пользователя.
     * <p>
     * При выборе премиум-плана возможно продление подписки на указанное количество месяцев.
     * </p>
     *
     * @param userId           идентификатор пользователя
     * @param subscriptionPlan название плана подписки
     * @param months           количество месяцев продления (необязательно)
     * @return редирект на страницу деталей пользователя
     */
    @PostMapping("/users/{userId}/change-subscription")
    public String changeUserSubscription(@PathVariable Long userId,
                                         @RequestParam("subscriptionPlan") String subscriptionPlan,
                                         @RequestParam(value = "months", required = false) Integer months) {
        // Проверяем, если месяц не передан, ставим значение по умолчанию 1
        if ("PREMIUM".equalsIgnoreCase(subscriptionPlan)) {
            if (months == null) {
                months = 1;
            }
            subscriptionService.upgradeOrExtendSubscription(userId, months); // Продление платной подписки
        } else {
            subscriptionService.changeSubscription(userId, subscriptionPlan, null);  // Смена подписки
        }
        return "redirect:/admin/users/" + userId;
    }

    /**
     * Запускает агрегацию недельной, месячной и годовой статистики за вчерашний день.
     *
     * @return редирект на административную страницу
     */
    @PostMapping("/aggregate-stats")
    public String triggerAggregation() {
        statsAggregationService.aggregateYesterday();
        return "redirect:/admin";
    }

    /**
     * Запускает агрегацию статистики за указанный диапазон дат.
     *
     * @param from дата начала в формате ISO (yyyy-MM-dd)
     * @param to   дата окончания в формате ISO (yyyy-MM-dd)
     * @return редирект на административную страницу
     */
    @PostMapping("/aggregate-stats/range")
    public String triggerAggregationRange(@RequestParam("from")
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                          LocalDate from,
                                          @RequestParam("to")
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                          LocalDate to) {
        statsAggregationService.aggregateForRange(from, to);
        return "redirect:/admin";
    }

    /**
     * Отображает статистику по покупателям.
     *
     * @param model модель, в которую передаётся информация о количестве покупателей
     * @return имя шаблона со статистикой по покупателям
     */
    @GetMapping("/customers")
    public String customerStats(Model model) {
        long total = adminService.countCustomers();
        long unreliable = adminService.countUnreliableCustomers();
        double percent = total > 0 ? (double) unreliable / total * 100 : 0;
        model.addAttribute("totalCustomers", total);
        model.addAttribute("unreliablePercent", String.format("%.2f", percent));
        model.addAttribute("riskCustomers", adminService.getUnreliableCustomers());
        return "admin/customers";
    }

    /**
     * Экспорт списка ненадёжных покупателей в формате CSV.
     *
     * @return CSV-строка со списком покупателей
     */
    @GetMapping(value = "/customers/export", produces = "text/csv")
    @ResponseBody
    public String exportCustomersCsv() {
        return adminService.toCsv(adminService.getUnreliableCustomers());
    }

    /**
     * Отображает статистику активности Telegram-бота.
     *
     * @param model модель, в которую передаются данные об активности
     * @return имя шаблона со статистикой Telegram
     */
    @GetMapping("/telegram")
    public String telegramStats(Model model) {
        model.addAttribute("boundCustomers", adminService.countTelegramBoundCustomers());
        model.addAttribute("remindersEnabled", adminService.countStoresWithReminders());
        model.addAttribute("logs", adminService.getRecentLogs());
        return "admin/telegram";
    }

    /**
     * Отображает список магазинов с Telegram-настройками и подпиской владельца.
     *
     * @param model модель, в которую передаётся информация о магазинах
     * @return имя шаблона со списком магазинов
     */
    @GetMapping("/stores")
    public String stores(Model model) {
        model.addAttribute("stores", adminService.getStoresInfo());
        return "admin/stores";
    }

    /**
     * Отображает список всех посылок в системе.
     *
     * @param model модель для передачи данных о посылках
     * @return имя шаблона со списком посылок
     */
    @GetMapping("/parcels")
    public String parcels(@RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "20") int size,
                          Model model) {
        // Загружаем посылки постранично
        org.springframework.data.domain.Page<TrackParcelAdminInfoDTO> parcelPage = adminService.getAllParcels(page, size);

        model.addAttribute("parcels", parcelPage.getContent());
        model.addAttribute("currentPage", parcelPage.getNumber());
        model.addAttribute("totalPages", parcelPage.getTotalPages());
        model.addAttribute("size", size);

        return "admin/parcels";
    }

    /**
     * Поиск посылки по номеру и редирект на страницу деталей.
     *
     * @param number трек-номер
     * @return редирект на страницу подробной информации
     */
    @GetMapping("/parcels/search")
    public String searchParcel(@RequestParam("number") String number) {
        TrackParcelAdminInfoDTO parcel = adminService.findParcelByNumber(number);
        if (parcel == null) {
            return "redirect:/admin/parcels";
        }
        return "redirect:/admin/parcels/" + parcel.getId();
    }

    /**
     * Отображает подробную информацию о посылке.
     *
     * @param id идентификатор посылки
     * @param model модель для передачи данных
     * @return имя шаблона с деталями посылки
     */
    @GetMapping("/parcels/{id}")
    public String parcelDetails(@PathVariable Long id, Model model) {
        model.addAttribute("parcel", adminService.getParcelById(id));
        return "admin/parcel-details";
    }

    /**
     * Удаляет посылку из системы.
     *
     * @param id идентификатор посылки
     * @return редирект на список посылок
     */
    @PostMapping("/parcels/{id}/delete")
    public String deleteParcel(@PathVariable Long id) {
        adminService.deleteParcel(id);
        return "redirect:/admin/parcels";
    }

    /**
     * Принудительно обновляет статус посылки.
     *
     * @param id идентификатор посылки
     * @return редирект на страницу деталей
     */
    @PostMapping("/parcels/{id}/force-update")
    public String forceUpdateParcel(@PathVariable Long id) {
        adminService.forceUpdateParcel(id);
        return "redirect:/admin/parcels/" + id;
    }

    /**
     * Отображает список подписок пользователей и доступных тарифов.
     *
     * @param model модель, в которую передаются сведения о подписках
     * @return имя шаблона с информацией о подписках
     */
    @GetMapping("/subscriptions")
    public String subscriptions(Model model) {
        model.addAttribute("subscriptions", adminService.getAllUserSubscriptions());
        model.addAttribute("plans", adminService.getPlans());
        return "admin/subscriptions";
    }

    /**
     * Отображает страницу настроек администратора.
     *
     * @return имя шаблона настроек
     */
    @GetMapping("/settings")
    public String settings() {
        return "admin/settings";
    }

}