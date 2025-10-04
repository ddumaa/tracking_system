package com.project.tracking_system.service.customer;

import com.project.tracking_system.dto.TelegramParcelsOverviewDTO;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.OrderReturnRequestActionRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionType;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.order.ExchangeApprovalResult;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.telegram.FullNameValidator;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link CustomerTelegramService}, проверяющие подтверждение имени и формирование сводки статусов.
 */
@ExtendWith(MockitoExtension.class)
class CustomerTelegramServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private CustomerNotificationLogRepository notificationLogRepository;
    @Mock
    private TelegramNotificationService telegramNotificationService;
    @Mock
    private OrderReturnRequestRepository returnRequestRepository;
    @Mock
    private OrderReturnRequestService orderReturnRequestService;

    @Spy
    private FullNameValidator fullNameValidator = new FullNameValidator();

    @InjectMocks
    private CustomerTelegramService customerTelegramService;

    /**
     * Убеждаемся, что при подтверждении существующего ФИО источник обновляется до USER_CONFIRMED.
     */
    @Test
    void confirmName_whenFullNameExistsAndSourceNotConfirmed_updatesSourceAndTimestamp() {
        Long chatId = 42L;
        ZonedDateTime oldTimestamp = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setTelegramChatId(chatId);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setNameUpdatedAt(oldTimestamp);

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = customerTelegramService.confirmName(chatId);

        assertTrue(result, "Метод должен вернуть истину при успешном подтверждении");
        assertEquals(NameSource.USER_CONFIRMED, customer.getNameSource(),
                "Источник имени обязан переключиться на USER_CONFIRMED");
        assertNotNull(customer.getNameUpdatedAt(), "Дата обновления имени должна быть установлена");
        assertTrue(customer.getNameUpdatedAt().isAfter(oldTimestamp),
                "Метка времени обязана быть новее предыдущей");

        verify(customerRepository).save(customer);
        verify(customerService, never()).updateCustomerName(any(), anyString(), any());
    }

    /**
     * Убеждаемся, что при некорректном ФИО подтверждение отклоняется и источник сбрасывается.
     */
    @Test
    void confirmName_whenStoredNameInvalid_resetsSourceAndReturnsFalse() {
        Long chatId = 43L;
        ZonedDateTime oldTimestamp = ZonedDateTime.now(ZoneOffset.UTC).minusDays(2);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setTelegramChatId(chatId);
        customer.setFullName("Иван");
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setNameUpdatedAt(oldTimestamp);

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = customerTelegramService.confirmName(chatId);

        assertFalse(result, "Метод обязан отказать в подтверждении некорректного ФИО");
        assertEquals(NameSource.MERCHANT_PROVIDED, customer.getNameSource(),
                "Источник имени должен быть сброшен до MERCHANT_PROVIDED");
        assertNotNull(customer.getNameUpdatedAt(), "Дата обновления обязана быть заполнена");
        assertTrue(customer.getNameUpdatedAt().isAfter(oldTimestamp),
                "Метка времени должна обновляться при сбросе источника");

        verify(customerRepository).save(customer);
        verify(customerService, never()).updateCustomerName(any(), anyString(), any());
    }

    /**
     * Проверяем, что сводка корректно распределяет статусы по разделам Telegram.
     */
    @Test
    void getParcelsOverview_groupsStatusesAccordingToRules() {
        Long chatId = 100L;
        Long customerId = 555L;

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setTelegramChatId(chatId);

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));

        TrackParcel deliveredParcel = parcelWithStatus("DEL-1", GlobalStatus.DELIVERED, ZonedDateTime.now(ZoneOffset.UTC));
        deliveredParcel.setId(101L);
        TrackParcel waitingParcel = parcelWithStatus("WAIT-1", GlobalStatus.WAITING_FOR_CUSTOMER,
                ZonedDateTime.now(ZoneOffset.UTC).minusHours(2));
        waitingParcel.setId(102L);
        TrackParcel notPickingParcel = parcelWithStatus("NOT-PICK", GlobalStatus.CUSTOMER_NOT_PICKING_UP,
                ZonedDateTime.now(ZoneOffset.UTC).minusHours(3));
        notPickingParcel.setId(103L);
        TrackParcel preRegisteredParcel = parcelWithStatus("PRE-1", GlobalStatus.PRE_REGISTERED,
                ZonedDateTime.now(ZoneOffset.UTC).minusDays(1));
        preRegisteredParcel.setId(104L);
        TrackParcel registeredParcel = parcelWithStatus("REG-1", GlobalStatus.REGISTERED,
                ZonedDateTime.now(ZoneOffset.UTC).minusHours(5));
        registeredParcel.setId(105L);
        TrackParcel inTransitParcel = parcelWithStatus("TRANS-1", GlobalStatus.IN_TRANSIT,
                ZonedDateTime.now(ZoneOffset.UTC).minusHours(6));
        inTransitParcel.setId(106L);

        List<GlobalStatus> deliveredStatuses = List.of(GlobalStatus.DELIVERED);
        List<GlobalStatus> waitingStatuses = List.of(
                GlobalStatus.WAITING_FOR_CUSTOMER,
                GlobalStatus.CUSTOMER_NOT_PICKING_UP
        );
        List<GlobalStatus> inTransitStatuses = List.of(
                GlobalStatus.PRE_REGISTERED,
                GlobalStatus.REGISTERED,
                GlobalStatus.IN_TRANSIT
        );

        when(trackParcelRepository.findByCustomerIdAndStatusIn(eq(customerId), anyList()))
                .thenAnswer(invocation -> {
                    List<GlobalStatus> statuses = invocation.getArgument(1);
                    if (statuses.equals(deliveredStatuses)) {
                        return List.of(deliveredParcel);
                    }
                    if (statuses.equals(waitingStatuses)) {
                        return List.of(waitingParcel, notPickingParcel);
                    }
                    if (statuses.equals(inTransitStatuses)) {
                        return List.of(preRegisteredParcel, registeredParcel, inTransitParcel);
                    }
                    fail("Неожиданный набор статусов: " + statuses);
                    return List.of();
                });

        when(returnRequestRepository.findParcelIdsByCustomerAndStatusIn(eq(customerId), anyCollection()))
                .thenReturn(List.of(deliveredParcel.getId()));

        Optional<TelegramParcelsOverviewDTO> result = customerTelegramService.getParcelsOverview(chatId);

        assertTrue(result.isPresent(), "Сводка должна формироваться для известного чата");

        TelegramParcelsOverviewDTO overview = result.get();
        assertEquals(1, overview.getDelivered().size(), "Раздел доставленных должен содержать одну посылку");
        assertEquals(2, overview.getWaitingForPickup().size(), "Раздел ожидания обязан включать оба статуса ожидания");
        assertEquals(3, overview.getInTransit().size(), "Раздел в пути обязан включать ключевые статусы до выдачи");
        assertTrue(overview.getDelivered().get(0).hasActiveReturnRequest(),
                "У доставленной посылки должен отмечаться активный запрос возврата");

        verify(trackParcelRepository).findByCustomerIdAndStatusIn(customerId, deliveredStatuses);
        verify(trackParcelRepository).findByCustomerIdAndStatusIn(customerId, waitingStatuses);
        verify(trackParcelRepository).findByCustomerIdAndStatusIn(customerId, inTransitStatuses);
        verify(returnRequestRepository).findParcelIdsByCustomerAndStatusIn(eq(customerId), anyCollection());
    }

    /**
     * Убеждаемся, что регистрация заявки через Telegram проверяет принадлежность посылки и делегирует сервису возвратов.
     */
    @Test
    void registerReturnRequestFromTelegram_whenDataValid_delegatesToOrderService() {
        Long chatId = 777L;
        Long parcelId = 1001L;
        String key = "key-123";
        String reason = "Не подошёл размер";

        Customer customer = new Customer();
        customer.setId(55L);
        customer.setTelegramChatId(chatId);

        User owner = new User();
        owner.setId(9L);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setCustomer(customer);
        parcel.setUser(owner);

        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(88L);
        request.setStatus(OrderReturnRequestStatus.REGISTERED);

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(trackParcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        ArgumentCaptor<ZonedDateTime> requestedAtCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        when(orderReturnRequestService.registerReturn(eq(parcelId), eq(owner), eq(key), eq(reason), isNull(), any(ZonedDateTime.class), isNull()))
                .thenReturn(request);

        OrderReturnRequest result = customerTelegramService.registerReturnRequestFromTelegram(
                chatId,
                parcelId,
                key,
                reason
        );

        assertSame(request, result, "Метод обязан возвращать заявку, полученную от доменного сервиса");
        verify(orderReturnRequestService).registerReturn(eq(parcelId), eq(owner), eq(key), eq(reason), isNull(), requestedAtCaptor.capture(), isNull());
        ZonedDateTime capturedRequestedAt = requestedAtCaptor.getValue();
        assertNotNull(capturedRequestedAt, "Дата регистрации должна вычисляться автоматически");
        assertEquals(ZoneOffset.UTC, capturedRequestedAt.getZone(), "Дата должна фиксироваться в UTC");
        assertTrue(Duration.between(capturedRequestedAt, ZonedDateTime.now(ZoneOffset.UTC)).abs().getSeconds() < 5,
                "Дата регистрации должна соответствовать текущему времени");
    }

    /**
     * Проверяем, что регистрация заявки отклоняется, если посылка принадлежит другому покупателю.
     */
    @Test
    void registerReturnRequestFromTelegram_whenParcelNotOwned_throwsAccessDenied() {
        Long chatId = 888L;
        Long parcelId = 2002L;

        Customer customer = new Customer();
        customer.setId(77L);
        customer.setTelegramChatId(chatId);

        Customer foreignCustomer = new Customer();
        foreignCustomer.setId(78L);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setCustomer(foreignCustomer);

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(trackParcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        assertThrows(AccessDeniedException.class, () -> customerTelegramService.registerReturnRequestFromTelegram(
                chatId,
                parcelId,
                "key",
                "Причина"
        ));
        verify(orderReturnRequestService, never()).registerReturn(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Убеждаемся, что запуск обмена через Telegram проходит проверку владельца и делегируется бизнес-сервису.
     */
    @Test
    void approveExchangeFromTelegram_whenRequestValid_callsOrderService() {
        Long chatId = 909L;
        Long parcelId = 3003L;
        Long requestId = 4004L;

        Customer customer = new Customer();
        customer.setId(91L);
        customer.setTelegramChatId(chatId);

        User owner = new User();
        owner.setId(15L);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setCustomer(customer);
        parcel.setUser(owner);

        ExchangeApprovalResult approvalResult = new ExchangeApprovalResult(new OrderReturnRequest(), new TrackParcel());

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(trackParcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(orderReturnRequestService.approveExchange(requestId, parcelId, owner)).thenReturn(approvalResult);

        ExchangeApprovalResult result = customerTelegramService.approveExchangeFromTelegram(chatId, parcelId, requestId);

        assertSame(approvalResult, result, "Метод должен возвращать результат обмена от OrderReturnRequestService");
        verify(orderReturnRequestService).approveExchange(requestId, parcelId, owner);
    }

    @Test
    void updateReturnRequestDetailsFromTelegram_whenValid_delegatesToOrderService() {
        Long chatId = 1009L;
        Long parcelId = 5005L;
        Long requestId = 6006L;

        Customer customer = new Customer();
        customer.setId(92L);
        customer.setTelegramChatId(chatId);

        User owner = new User();
        owner.setId(16L);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setCustomer(customer);
        parcel.setUser(owner);

        ReturnRequestUpdateResponse response = new ReturnRequestUpdateResponse(requestId, "RT123", "Комментарий", OrderReturnRequestStatus.REGISTERED);

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(trackParcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(orderReturnRequestService.updateReverseTrackAndComment(requestId, parcelId, owner, "track", "comment"))
                .thenReturn(response);

        ReturnRequestUpdateResponse result = customerTelegramService.updateReturnRequestDetailsFromTelegram(
                chatId,
                parcelId,
                requestId,
                "track",
                "comment"
        );

        assertSame(response, result, "Ответ сервиса должен возвращаться без изменений");
        verify(orderReturnRequestService).updateReverseTrackAndComment(requestId, parcelId, owner, "track", "comment");
    }

    @Test
    void updateReturnRequestDetailsFromTelegram_whenParcelNotOwned_throwsAccessDenied() {
        Long chatId = 1010L;
        Long parcelId = 7007L;
        Long requestId = 8008L;

        Customer customer = new Customer();
        customer.setId(93L);
        customer.setTelegramChatId(chatId);

        Customer foreign = new Customer();
        foreign.setId(94L);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setCustomer(foreign);

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(trackParcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        assertThrows(AccessDeniedException.class, () -> customerTelegramService.updateReturnRequestDetailsFromTelegram(
                chatId,
                parcelId,
                requestId,
                "track",
                "comment"
        ));
        verify(orderReturnRequestService, never()).updateReverseTrackAndComment(any(), any(), any(), any(), any());
    }

    @Test
    void requestExchangeCancellationFromTelegram_whenValid_createsMerchantRequest() {
        Long chatId = 1101L;
        Long parcelId = 9009L;
        Long requestId = 9010L;

        Customer customer = new Customer();
        customer.setId(95L);
        customer.setTelegramChatId(chatId);

        User owner = new User();
        owner.setId(17L);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setCustomer(customer);
        parcel.setUser(owner);

        OrderReturnRequestActionRequest actionRequest = new OrderReturnRequestActionRequest();

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(trackParcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(orderReturnRequestService.requestMerchantAction(requestId, parcelId, owner, customer,
                OrderReturnRequestActionType.CANCEL_EXCHANGE)).thenReturn(actionRequest);

        OrderReturnRequestActionRequest result = customerTelegramService.requestExchangeCancellationFromTelegram(
                chatId,
                parcelId,
                requestId
        );

        assertSame(actionRequest, result, "Сервис должен возвращать созданный запрос к магазину");
        verify(orderReturnRequestService).requestMerchantAction(requestId, parcelId, owner, customer,
                OrderReturnRequestActionType.CANCEL_EXCHANGE);
    }

    @Test
    void requestExchangeConversionFromTelegram_whenValid_createsMerchantRequest() {
        Long chatId = 1102L;
        Long parcelId = 9011L;
        Long requestId = 9012L;

        Customer customer = new Customer();
        customer.setId(96L);
        customer.setTelegramChatId(chatId);

        User owner = new User();
        owner.setId(18L);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setCustomer(customer);
        parcel.setUser(owner);

        OrderReturnRequestActionRequest actionRequest = new OrderReturnRequestActionRequest();

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(trackParcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(orderReturnRequestService.requestMerchantAction(requestId, parcelId, owner, customer,
                OrderReturnRequestActionType.CONVERT_TO_RETURN)).thenReturn(actionRequest);

        OrderReturnRequestActionRequest result = customerTelegramService.requestExchangeConversionFromTelegram(
                chatId,
                parcelId,
                requestId
        );

        assertSame(actionRequest, result, "Метод обязан возвращать запрос на перевод обмена");
        verify(orderReturnRequestService).requestMerchantAction(requestId, parcelId, owner, customer,
                OrderReturnRequestActionType.CONVERT_TO_RETURN);
    }

    private TrackParcel parcelWithStatus(String number, GlobalStatus status, ZonedDateTime lastUpdate) {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber(number);
        parcel.setStatus(status);
        parcel.setLastUpdate(lastUpdate);
        Store store = new Store();
        store.setName("Store for " + status.name());
        parcel.setStore(store);
        return parcel;
    }
}
