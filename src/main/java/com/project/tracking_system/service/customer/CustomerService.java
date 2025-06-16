package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.dto.CustomerInfoDTO;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.project.tracking_system.service.customer.CustomerTransactionalService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Сервис управления покупателями.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final CustomerTransactionalService transactionalService;

    /**
     * Зарегистрировать нового покупателя или получить существующего по телефону.
     * <p>
     * Все операции поиска и сохранения выполняются в отдельных транзакциях,
     * что исключает ошибку "current transaction is aborted" при конкурентной записи.
     * </p>
     *
     * @param rawPhone телефон в произвольном формате
     * @return сущность покупателя
     */
    public Customer registerOrGetByPhone(String rawPhone) {
        String phone = PhoneUtils.normalizePhone(rawPhone);
        // Первый поиск выполняем отдельно, чтобы не создавать дубликаты
        Optional<Customer> existing = transactionalService.findByPhone(phone);
        if (existing.isPresent()) {
            return existing.get();
        }

        Customer customer = new Customer();
        customer.setPhone(phone);
        try {
            Customer saved = transactionalService.saveCustomer(customer);
            log.info("Создан новый покупатель с номером {}", phone);
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.info("Покупатель с номером {} уже существует, выполняем повторный поиск", phone);
            return transactionalService.findByPhone(phone)
                    .orElseThrow(() -> new IllegalStateException("Покупатель не найден после ошибки сохранения"));
        }
    }

    /**
     * Увеличить счётчик отправленных посылок для покупателя.
     *
     * @param track посылка, связанная с покупателем
     */
    @Transactional
    public void updateStatsOnTrackAdd(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }
        Customer customer = track.getCustomer();
        customer.setSentCount(customer.getSentCount() + 1);
        customer.recalculateReputation();
        customerRepository.save(customer);
    }

    /**
     * Увеличить счётчик забранных посылок при доставке.
     *
     * @param track посылка, связанная с покупателем
     */
    @Transactional
    public void updateStatsOnTrackDelivered(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }
        Customer customer = track.getCustomer();
        customer.setPickedUpCount(customer.getPickedUpCount() + 1);
        customer.recalculateReputation();
        customerRepository.save(customer);
    }

    /**
     * Откатить статистику при удалении посылки.
     *
     * @param track удаляемая посылка
     */
    @Transactional
    public void rollbackStatsOnTrackDelete(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }
        Customer customer = track.getCustomer();
        if (customer.getSentCount() > 0) {
            customer.setSentCount(customer.getSentCount() - 1);
        }
        if (track.getStatus() != null && track.getStatus().isFinal() && customer.getPickedUpCount() > 0) {
            customer.setPickedUpCount(customer.getPickedUpCount() - 1);
        }
        customer.recalculateReputation();
        customerRepository.save(customer);
    }

    /**
     * Получить информацию о покупателе по идентификатору посылки.
     *
     * @param parcelId идентификатор посылки
     * @return DTO с информацией о покупателе или {@code null}, если покупатель не найден
     */
    @Transactional(readOnly = true)
    public CustomerInfoDTO getCustomerInfoByParcelId(Long parcelId) {
        return trackParcelRepository.findById(parcelId)
                .map(TrackParcel::getCustomer)
                .map(this::toInfoDto)
                .orElse(null);
    }

    /**
     * Привязать покупателя к посылке по телефону.
     *
     * @param parcelId идентификатор посылки
     * @param rawPhone телефон покупателя
     * @return обновлённая информация о покупателе
     */
    @Transactional
    public CustomerInfoDTO assignCustomerToParcel(Long parcelId, String rawPhone) {
        // Загружаем посылку и нового покупателя
        TrackParcel parcel = trackParcelRepository.findById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Посылка не найдена"));
        Customer newCustomer = registerOrGetByPhone(rawPhone);

        Customer current = parcel.getCustomer();
        // Если посылка уже привязана к этому же покупателю, ничего не меняем
        if (current != null && current.getId().equals(newCustomer.getId())) {
            return toInfoDto(current);
        }

        // Если посылка была связана с другим покупателем, корректируем статистику старого
        if (current != null) {
            rollbackStatsOnTrackDelete(parcel);
        }

        // Привязываем нового покупателя
        parcel.setCustomer(newCustomer);
        trackParcelRepository.save(parcel);

        // Статистику увеличиваем только при фактическом добавлении нового покупателя
        updateStatsOnTrackAdd(parcel);
        return toInfoDto(newCustomer);
    }

    private CustomerInfoDTO toInfoDto(Customer customer) {
        if (customer == null) {
            return null;
        }
        double percentage = customer.getSentCount() > 0
                ? (double) customer.getPickedUpCount() / customer.getSentCount() * 100
                : 0.0;
        return new CustomerInfoDTO(
                customer.getPhone(),
                customer.getSentCount(),
                customer.getPickedUpCount(),
                Math.round(percentage * 100.0) / 100.0,
                customer.getReputation()
        );
    }
}
