package com.project.tracking_system.service.customer;

import com.project.tracking_system.dto.TelegramParcelsOverviewDTO;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.telegram.FullNameValidator;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
