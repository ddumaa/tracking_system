package com.project.tracking_system.service.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import com.project.tracking_system.dto.CommandDto;
import com.project.tracking_system.dto.RequestDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.ReturnCommandLog;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.ReturnCommandLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Сервис идемпотентного выполнения команд по заявкам возврата.
 * <p>
 * Компонент инкапсулирует проверку повторов, вычисление хеша полезной нагрузки
 * и фиксацию результата в журнале. Это позволяет централизовать все правила
 * идемпотентности и не дублировать их в REST-контроллере или других слоях.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnRequestCommandService {

    private static final String HASH_ALGORITHM = "SHA-256";

    private final OrderReturnRequestService orderReturnRequestService;
    private final ReturnRequestMapper returnRequestMapper;
    private final ReturnCommandLogRepository returnCommandLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Выполняет команду с учётом идемпотентности и возвращает DTO заявки.
     *
     * @param requestId идентификатор заявки
     * @param commandType тип выполняемой команды
     * @param command данные запроса, включая ключ идемпотентности
     * @param user текущий пользователь
     * @param userZone временная зона пользователя для форматирования дат
     * @return DTO заявки после выполнения команды или сохранённый ранее результат
     */
    @Transactional
    public RequestDto executeCommand(Long requestId,
                                     ReturnRequestCommandType commandType,
                                     CommandDto command,
                                     User user,
                                     ZoneId userZone) {
        validateArguments(requestId, commandType, command, user);

        OrderReturnRequest context = orderReturnRequestService.getOwnedRequest(requestId, user);
        Long parcelId = resolveParcelId(context);

        String normalizedKey = command.idempotencyKey().trim();
        String payloadHash = computePayloadHash(commandType, command);

        ReturnCommandLog logEntry = reserveLogEntry(requestId, normalizedKey, commandType, payloadHash);
        if (logEntry.getResponseSnapshot() != null) {
            return restoreResponse(logEntry);
        }

        OrderReturnRequest updated = performCommand(commandType, command, user, requestId, parcelId);
        RequestDto response = returnRequestMapper.toDto(updated, userZone);
        completeLogEntry(logEntry, response);
        return response;
    }

    /**
     * Проверяет корректность входных параметров запроса.
     */
    private void validateArguments(Long requestId,
                                   ReturnRequestCommandType commandType,
                                   CommandDto command,
                                   User user) {
        if (requestId == null) {
            throw new IllegalArgumentException("Не указан идентификатор заявки");
        }
        if (commandType == null) {
            throw new IllegalArgumentException("Неизвестная команда управления заявкой");
        }
        if (command == null) {
            throw new IllegalArgumentException("Не передано тело команды");
        }
        if (!StringUtils.hasText(command.idempotencyKey())) {
            throw new IllegalArgumentException("Не указан идемпотентный ключ команды");
        }
        if (!StringUtils.hasText(command.action())) {
            throw new IllegalArgumentException("Не указан код действия команды");
        }
        if (user == null) {
            throw new IllegalArgumentException("Не указан пользователь");
        }
    }

    /**
     * Вычисляет хеш полезной нагрузки команды.
     */
    private String computePayloadHash(ReturnRequestCommandType type, CommandDto command) {
        MessageDigest digest = getDigest();
        digest.update(type.name().getBytes(StandardCharsets.UTF_8));
        digest.update(nullSafeBytes(command.action()));
        CommandDto.Payload payload = command.payload();
        if (payload != null) {
            digest.update(nullSafeBytes(payload.reverseTrack()));
            digest.update(nullSafeBytes(payload.comment()));
        }
        byte[] hash = digest.digest();
        return HexFormat.of().formatHex(hash);
    }

    /**
     * Выполняет бизнес-логику команды через соответствующий сервис.
     */
    private OrderReturnRequest performCommand(ReturnRequestCommandType type,
                                              CommandDto command,
                                              User user,
                                              Long requestId,
                                              Long parcelId) {
        return switch (type) {
            case START_EXCHANGE -> orderReturnRequestService.approveExchange(requestId, parcelId, user);
            case CREATE_EXCHANGE_PARCEL -> {
                orderReturnRequestService.createExchangeParcel(requestId, parcelId, user);
                yield orderReturnRequestService.getOwnedRequest(requestId, user);
            }
            case CLOSE -> orderReturnRequestService.closeWithoutExchange(requestId, parcelId, user);
            case CONFIRM_RECEIPT -> orderReturnRequestService.confirmReturnProcessing(requestId, parcelId, user);
            case UPDATE_DETAILS -> {
                CommandDto.Payload payload = Optional.ofNullable(command.payload()).orElse(new CommandDto.Payload(null, null));
                orderReturnRequestService.updateReverseTrackAndComment(
                        requestId,
                        parcelId,
                        user,
                        payload.reverseTrack(),
                        payload.comment()
                );
                yield orderReturnRequestService.getOwnedRequest(requestId, user);
            }
            case REOPEN -> orderReturnRequestService.reopenAsReturn(requestId, parcelId, user);
            case CANCEL_EXCHANGE -> orderReturnRequestService.cancelExchange(requestId, parcelId, user);
        };
    }

    /**
     * Завершает запись журнала, добавляя снимок ответа после успешного выполнения команды.
     */
    private void completeLogEntry(ReturnCommandLog logEntry, RequestDto response) {
        logEntry.setResponseSnapshot(serializeResponse(response));
        returnCommandLogRepository.saveAndFlush(logEntry);
        log.info("Зафиксировано выполнение команды {} по заявке {}", logEntry.getAction(), logEntry.getRequestId());
    }

    /**
     * Резервирует запись журнала под выполняемую команду или возвращает существующий результат.
     */
    private ReturnCommandLog reserveLogEntry(Long requestId,
                                             String idempotencyKey,
                                             ReturnRequestCommandType type,
                                             String payloadHash) {
        Optional<ReturnCommandLog> existingLog = returnCommandLogRepository
                .findFirstByRequestIdAndIdempotencyKey(requestId, idempotencyKey);
        if (existingLog.isPresent()) {
            ReturnCommandLog logEntry = existingLog.get();
            validatePayloadMatches(payloadHash, logEntry);
            return logEntry;
        }

        ReturnCommandLog logEntry = new ReturnCommandLog();
        logEntry.setRequestId(requestId);
        logEntry.setIdempotencyKey(idempotencyKey);
        logEntry.setAction(type.name());
        logEntry.setPayloadHash(payloadHash);

        try {
            return returnCommandLogRepository.saveAndFlush(logEntry);
        } catch (DataIntegrityViolationException ex) {
            ReturnCommandLog concurrentLog = returnCommandLogRepository
                    .findFirstByRequestIdAndIdempotencyKey(requestId, idempotencyKey)
                    .orElseThrow(() -> ex);
            validatePayloadMatches(payloadHash, concurrentLog);
            if (concurrentLog.getResponseSnapshot() == null) {
                throw new IllegalStateException("Команда с таким ключом уже выполняется, повторите позже", ex);
            }
            return concurrentLog;
        }
    }

    /**
     * Проверяет соответствие хеша полезной нагрузки ранее выполненной команде.
     */
    private void validatePayloadMatches(String payloadHash, ReturnCommandLog logEntry) {
        if (!payloadHash.equals(logEntry.getPayloadHash())) {
            throw new IllegalStateException("Команда с таким ключом уже выполнена с другими данными");
        }
    }

    /**
     * Восстанавливает DTO ответа из сохранённого снимка.
     */
    private RequestDto restoreResponse(ReturnCommandLog logEntry) {
        try {
            return objectMapper.readValue(logEntry.getResponseSnapshot(), RequestDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось восстановить ответ из журнала команд", ex);
        }
    }

    /**
     * Сериализует DTO ответа в JSON для хранения в журнале.
     */
    private String serializeResponse(RequestDto response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать ответ команды", ex);
        }
    }

    /**
     * Возвращает массив байт строки или пустой массив, если строка пуста.
     */
    private byte[] nullSafeBytes(String value) {
        return value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    /**
     * Возвращает digest для вычисления хеша.
     */
    private MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Алгоритм SHA-256 недоступен", ex);
        }
    }

    /**
     * Извлекает идентификатор посылки из заявки с проверкой наличия.
     */
    private Long resolveParcelId(OrderReturnRequest request) {
        TrackParcel parcel = Optional.ofNullable(request)
                .map(OrderReturnRequest::getParcel)
                .orElse(null);
        if (parcel == null || parcel.getId() == null) {
            throw new IllegalArgumentException("Заявка не привязана к посылке");
        }
        return parcel.getId();
    }
}
