package com.project.tracking_system.controller;

import com.project.tracking_system.dto.*;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.analytics.StatsAggregationService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.admin.AdminService;
import com.project.tracking_system.service.admin.AppInfoService;
import com.project.tracking_system.service.admin.SubscriptionPlanService;
import com.project.tracking_system.service.DynamicSchedulerService;
import com.project.tracking_system.exception.UserAlreadyExistsException;
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
    private final SubscriptionPlanService subscriptionPlanService;
    private final StoreRepository storeRepository;
    private final StatsAggregationService statsAggregationService;
    private final AdminService adminService;
    private final AppInfoService appInfoService;
    private final DynamicSchedulerService dynamicSchedulerService;

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
        long paidUsers = userService.countPaidUsers();
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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);

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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Пользователи", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Пользователи", "/admin/users"),
                new BreadcrumbItemDTO("Новый пользователь", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
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
                             @RequestParam("subscriptionPlan") String subscriptionCode,
                             Model model) {
        try {
            userService.createUserByAdmin(email, password, role, subscriptionCode);
            return "redirect:/admin/users";
        } catch (UserAlreadyExistsException e) {
            // Пользователь с таким email уже существует
            log.warn("Не удалось создать пользователя: {} уже существует", email);
            model.addAttribute("errorMessage", e.getMessage());
        } catch (IllegalArgumentException e) {
            // Переданы некорректные параметры
            log.warn("Ошибка создания пользователя: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            // Прочие ошибки
            log.error("Неизвестная ошибка создания пользователя", e);
            model.addAttribute("errorMessage", e.getMessage());
        }

        // Возвращаем пользователя на форму с выбором тарифных планов
        model.addAttribute("plans", adminService.getPlans());
        return "admin/user-new";
    }


    /**
     * Отображает детальную информацию о выбранном пользователе.
     *
     * Загружает в модель список магазинов пользователя и связанные посылки,
     * а также список доступных тарифных планов для изменения подписки.
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

        String code = null;
        String planName = null;
        String subscriptionEndDate = null;

        if (subscription != null) {
            SubscriptionPlan plan = subscription.getSubscriptionPlan();
            if (plan != null) {
                code = plan.getCode();
                planName = plan.getName();
            }

            if (subscription.getSubscriptionEndDate() != null) {
                subscriptionEndDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .format(subscription.getSubscriptionEndDate());
            }
        }

        UserDetailsAdminInfoDTO adminInfoDTO = new UserDetailsAdminInfoDTO(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                planName,
                code,
                subscriptionEndDate
        );

        model.addAttribute("user", adminInfoDTO);
        model.addAttribute("stores", stores);
        model.addAttribute("storeParcels", storeParcels);
        // Список доступных тарифов для смены подписки
        model.addAttribute("plans", adminService.getPlans());

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Пользователи", "/admin/users"),
                new BreadcrumbItemDTO("Информация о пользователе", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);

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
     * Изменяет тарифный план пользователя.
     * <p>
     * При выборе платного плана можно указать срок действия в месяцах. Для бесплатных
     * тарифов дата окончания обнуляется.
     * </p>
     *
     * @param userId           идентификатор пользователя
     * @param subscriptionPlan код нового плана подписки
     * @param months           срок действия в месяцах (для платных тарифов)
     * @return редирект на страницу деталей пользователя
     */
    @PostMapping("/users/{userId}/change-subscription")
    public String changeUserSubscription(@PathVariable Long userId,
                                         @RequestParam("subscriptionPlan") String subscriptionPlan,
                                         @RequestParam(value = "months", required = false) Integer months) {
        if (subscriptionPlanService.isPaidPlan(subscriptionPlan)) {
            if (months == null || months <= 0) {
                months = 1; // защита от некорректных значений
            }
        } else {
            months = null; // для бесплатных тарифов срок не нужен
        }

        subscriptionService.changeSubscription(userId, subscriptionPlan, months);
        return "redirect:/admin/users/" + userId;
    }

    /**
     * Удаляет пользователя целиком.
     *
     * @param id идентификатор пользователя
     * @return редирект на список пользователей
     */
    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return "redirect:/admin/users";
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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Покупатели", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
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
     * Удаляет покупателя и его связи.
     *
     * @param id идентификатор покупателя
     * @return редирект на список покупателей
     */
    @PostMapping("/customers/{id}/delete")
    public String deleteCustomer(@PathVariable Long id) {
        adminService.deleteCustomer(id);
        return "redirect:/admin/customers";
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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Telegram", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Магазины", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
        return "admin/stores";
    }

    /**
     * Удаляет магазин с его данными.
     *
     * @param id идентификатор магазина
     * @return редирект на список магазинов
     */
    @PostMapping("/stores/{id}/delete")
    public String deleteStore(@PathVariable Long id) {
        adminService.deleteStore(id);
        return "redirect:/admin/stores";
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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Посылки", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);

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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Посылки", "/admin/parcels"),
                new BreadcrumbItemDTO("Детали посылки", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
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

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Подписки", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
        return "admin/subscriptions";
    }

    /**
     * Отображает страницу настроек администратора.
     *
     * @return имя шаблона настроек
     */
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("appVersion", appInfoService.getApplicationVersion());
        model.addAttribute("webhookEnabled", appInfoService.isTelegramWebhookEnabled());
        model.addAttribute("plans", appInfoService.getPlans());

        // Хлебные крошки
        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Настройки", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
        return "admin/settings";
    }

    /**
     * Просмотр расписания всех задач.
     *
     * @param model модель представления
     * @return страница расписания
     */
    @GetMapping("/schedules")
    public String schedules(Model model) {
        model.addAttribute("configs", dynamicSchedulerService.getAllConfigs());

        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Расписание", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
        return "admin/schedules";
    }

    /**
     * Обновление cron выражения задачи.
     *
     * @param id   идентификатор задачи
     * @param cron новое выражение cron
     * @return редирект на список задач
     */
    @PostMapping("/schedules/{id}")
    public String updateSchedule(@PathVariable Long id,
                                 @RequestParam String cron) {
        dynamicSchedulerService.updateCron(id, cron);
        return "redirect:/admin/schedules";
    }

    /**
     * Отображает список тарифных планов и форму их редактирования.
     *
     * @param model модель для передачи данных
     * @return имя шаблона управления тарифами
     */
    @GetMapping("/plans")
    public String plans(Model model) {
        model.addAttribute("plans", adminService.getPlans());

        List<BreadcrumbItemDTO> breadcrumbs = List.of(
                new BreadcrumbItemDTO("Админ Панель", "/admin"),
                new BreadcrumbItemDTO("Тарифы", "")
        );
        model.addAttribute("breadcrumbs", breadcrumbs);
        return "admin/plans";
    }

    /**
     * Создаёт новый тарифный план.
     *
     * @param dto параметры плана
     * @return редирект на страницу тарифов
     */
    @PostMapping("/plans")
    public String createPlan(@ModelAttribute SubscriptionPlanDTO dto) {
        adminService.createPlan(dto);
        return "redirect:/admin/plans";
    }

    /**
     * Обновляет существующий тарифный план.
     *
     * @param id  идентификатор плана
     * @param dto новые параметры
     * @return редирект на страницу тарифов
     */
    @PostMapping("/plans/{id}")
    public String updatePlan(@PathVariable Long id, @ModelAttribute SubscriptionPlanDTO dto) {
        adminService.updatePlan(id, dto);
        return "redirect:/admin/plans";
    }

    /**
     * Отключить или включить тарифный план.
     */
    @PostMapping("/plans/{id}/active")
    public String togglePlan(@PathVariable Long id, @RequestParam boolean active) {
        adminService.setPlanActive(id, active);
        return "redirect:/admin/plans";
    }

    /**
     * Удалить тарифный план.
     */
    @PostMapping("/plans/{id}/delete")
    public String deletePlan(@PathVariable Long id) {
        adminService.deletePlan(id);
        return "redirect:/admin/plans";
    }

    /**
     * Переместить тариф вверх в списке.
     *
     * @param id идентификатор плана
     * @return редирект на страницу тарифов
     */
    @PostMapping("/plans/{id}/move-up")
    public String movePlanUp(@PathVariable Long id) {
        subscriptionPlanService.movePlanUp(id);
        return "redirect:/admin/plans";
    }

    /**
     * Переместить тариф вниз в списке.
     *
     * @param id идентификатор плана
     * @return редирект на страницу тарифов
     */
    @PostMapping("/plans/{id}/move-down")
    public String movePlanDown(@PathVariable Long id) {
        subscriptionPlanService.movePlanDown(id);
        return "redirect:/admin/plans";
    }

}