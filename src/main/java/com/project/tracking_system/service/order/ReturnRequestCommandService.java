package com.project.tracking_system.service.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import com.project.tracking_system.dto.CommandDto;
import com.project.tracking_system.dto.RequestDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnCommandLog;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.ActionNotAllowedException;
import com.project.tracking_system.exception.IdempotencyConflictException;
import com.project.tracking_system.exception.ValidationException;
import com.project.tracking_system.repository.ReturnCommandLogRepository;
import com.project.tracking_system.service.order.payload.ExchangeShipmentPayload;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayload;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayloadFactory;
import com.project.tracking_system.service.order.payload.StageMarkPayload;
import com.project.tracking_system.service.order.payload.UpdateReverseTrackPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

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
public class ReturnRequestCommandService {

    private static final String HASH_ALGORITHM = "SHA-256";

    private final OrderReturnRequestService orderReturnRequestService;
    private final ReturnRequestMapper returnRequestMapper;
    private final ReturnCommandLogRepository returnCommandLogRepository;
    private final ObjectMapper objectMapper;
    private final ReturnRequestCommandPayloadFactory payloadFactory;

    /**
     * Конструирует сервис и подготавливает зависимости для обработки команд.
     */
    public ReturnRequestCommandService(OrderReturnRequestService orderReturnRequestService,
                                       ReturnRequestMapper returnRequestMapper,
                                       ReturnCommandLogRepository returnCommandLogRepository,
                                       ObjectMapper objectMapper,
                                       ReturnRequestCommandPayloadFactory payloadFactory) {
        this.orderReturnRequestService = orderReturnRequestService;
        this.returnRequestMapper = returnRequestMapper;
        this.returnCommandLogRepository = returnCommandLogRepository;
        this.objectMapper = objectMapper;
        this.payloadFactory = payloadFactory;
    }

    /**
     * Выполняет команду с учётом идемпотентности и возвращает DTO заявки.
     *
     * @param requestKey UUID идентификатора заявки
     * @param commandType тип выполняемой команды
     * @param command данные запроса, включая ключ идемпотентности
     * @param user текущий пользователь
     * @param userZone временная зона пользователя для форматирования дат
     * @return DTO заявки после выполнения команды или сохранённый ранее результат
     */
    @Transactional
    public RequestDto executeCommand(UUID requestKey,
                                     ReturnRequestCommandType commandType,
                                     CommandDto command,
                                     User user,
                                     ZoneId userZone) {
        validateArguments(requestKey, commandType, command, user);

        OrderReturnRequest context = orderReturnRequestService.getOwnedRequest(requestKey, user);
        Long requestId = requirePersistentId(context);
        Long parcelId = resolveParcelId(context);

        String normalizedKey = command.idempotencyKey().trim();
        ReturnRequestCommandPayload payload = createPayload(commandType, command);
        String payloadHash = computePayloadHash(commandType, command, payload);

        ReturnCommandLog logEntry = reserveLogEntry(requestId, normalizedKey, commandType, payloadHash);
        if (logEntry.getResponseSnapshot() != null) {
            return restoreResponse(logEntry);
        }

        CommandContext commandContext = new CommandContext(requestKey, requestId, parcelId, user);
        OrderReturnRequest updated = performCommand(commandType, payload, commandContext);
        RequestDto response = returnRequestMapper.toDto(updated, userZone);
        completeLogEntry(logEntry, response);
        return response;
    }

    /**
     * Проверяет корректность входных параметров запроса.
     */
    private void validateArguments(UUID requestKey,
                                   ReturnRequestCommandType commandType,
                                   CommandDto command,
                                   User user) {
        if (requestKey == null) {
            throw new ValidationException("Не указан идентификатор заявки");
        }
        if (commandType == null) {
            throw new ValidationException("Неизвестная команда управления заявкой");
        }
        if (command == null) {
            throw new ValidationException("Не передано тело команды");
        }
        if (!StringUtils.hasText(command.idempotencyKey())) {
            throw new ValidationException("Не указан идемпотентный ключ команды");
        }
        if (!StringUtils.hasText(command.action())) {
            throw new ValidationException("Не указан код действия команды");
        }
        if (requiresPayload(commandType) && command.payload() == null) {
            throw new ValidationException("Команда " + commandType.name() + " требует объект payload с параметрами");
        }
        if (user == null) {
            throw new ValidationException("Не указан пользователь");
        }
    }

    /**
     * Вычисляет хеш полезной нагрузки команды.
     */
    private String computePayloadHash(ReturnRequestCommandType type,
                                      CommandDto command,
                                      ReturnRequestCommandPayload payload) {
        MessageDigest digest = getDigest();
        digest.update(type.name().getBytes(StandardCharsets.UTF_8));
        digest.update(nullSafeBytes(command.action()));
        payload.updateDigest(digest, objectMapper);
        byte[] hash = digest.digest();
        return HexFormat.of().formatHex(hash);
    }

    /**
     * Создаёт и валидирует полезную нагрузку команды, переводя технические ошибки валидации
     * в доменное исключение {@link ValidationException} для единообразной обработки REST-слоем.
     */
    private ReturnRequestCommandPayload createPayload(ReturnRequestCommandType commandType, CommandDto command) {
        try {
            return payloadFactory.create(commandType, command.payload());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage(), ex);
        }
    }

    /**
     * Выполняет бизнес-логику команды через соответствующий сервис.
     */
    private OrderReturnRequest performCommand(ReturnRequestCommandType type,
                                              ReturnRequestCommandPayload payload,
                                              CommandContext context) {
        return switch (type) {
            case SET_MODE_EXCHANGE -> executeModeSwitch(context,
                    ReturnRequestMode.EXCHANGE,
                    OrderReturnRequestService.ModeSwitchTrigger.MANUAL_DECISION);
            case SET_MODE_RETURN -> executeModeSwitch(context,
                    ReturnRequestMode.RETURN,
                    OrderReturnRequestService.ModeSwitchTrigger.MANUAL_DECISION);
            case CLOSE_REQUEST -> executeCloseRequest(context);
            case UPDATE_REVERSE_TRACK -> executeUpdateReverseTrack(context, payload);
            case MARK_OUTBOUND_SENT -> executeStageCommand(context, payload, type,
                    orderReturnRequestService::markOutboundSent);
            case MARK_INBOUND_ARRIVED -> executeStageCommand(context, payload, type,
                    orderReturnRequestService::markInboundArrived);
            case MARK_INBOUND_PICKED_UP -> executeStageCommand(context, payload, type,
                    orderReturnRequestService::markInboundPickedUp);
            case REGISTER_EXCHANGE_PARCEL -> executeExchangeCommand(context, payload, type,
                    orderReturnRequestService::registerExchangeParcel);
            case MARK_EXCHANGE_SENT -> executeExchangeCommand(context, payload, type,
                    orderReturnRequestService::markExchangeSent);
            case MARK_EXCHANGE_DELIVERED -> executeStageCommand(context, payload, type,
                    orderReturnRequestService::markExchangeDelivered);
            default -> throw new ActionNotAllowedException("Команда " + type.name() + " не поддерживается системой");
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
                throw new IdempotencyConflictException("Команда с таким ключом уже выполняется, повторите позже", ex);
            }
            return concurrentLog;
        }
    }

    /**
     * Проверяет соответствие хеша полезной нагрузки ранее выполненной команде.
     */
    private void validatePayloadMatches(String payloadHash, ReturnCommandLog logEntry) {
        if (!payloadHash.equals(logEntry.getPayloadHash())) {
            throw new IdempotencyConflictException("Команда с таким ключом уже выполнена с другими данными");
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
            throw new ValidationException("Заявка не привязана к посылке");
        }
        return parcel.getId();
    }

    /**
     * Гарантирует наличие персистентного идентификатора заявки.
     *
     * @param request заявка, полученная из сервисного слоя
     * @return идентификатор в базе данных
     */
    private Long requirePersistentId(OrderReturnRequest request) {
        Long id = Optional.ofNullable(request)
                .map(OrderReturnRequest::getId)
                .orElse(null);
        if (id == null) {
            throw new IllegalStateException("У заявки отсутствует сохранённый идентификатор");
        }
        return id;
    }

    /**
     * Обрабатывает команду обновления обратного трека и возвращает актуальную заявку.
     */
    private OrderReturnRequest executeUpdateReverseTrack(CommandContext context,
                                                        ReturnRequestCommandPayload payload) {
        UpdateReverseTrackPayload updatePayload = requirePayload(payload, UpdateReverseTrackPayload.class,
                ReturnRequestCommandType.UPDATE_REVERSE_TRACK);
        orderReturnRequestService.updateReverseTrack(
                context.requestId(),
                context.parcelId(),
                context.user(),
                updatePayload.reverseTrack(),
                updatePayload.comment()
        );
        return orderReturnRequestService.getOwnedRequest(context.requestKey(), context.user());
    }

    /**
     * Выполняет закрытие заявки, учитывая её текущий статус.
     */
    private OrderReturnRequest executeCloseRequest(CommandContext context) {
        OrderReturnRequest request = orderReturnRequestService.getOwnedRequest(context.requestKey(), context.user());
        return switch (request.getStatus()) {
            case REGISTERED -> orderReturnRequestService.closeRequest(context.requestId(),
                    context.parcelId(),
                    context.user());
            case EXCHANGE_APPROVED -> throw new ActionNotAllowedException("Перед закрытием переведите заявку в режим возврата");
            case CLOSED_NO_EXCHANGE -> throw new ActionNotAllowedException("Заявка уже закрыта");
        };
    }

    /**
     * Выполняет обработку стадийной команды с опциональным временем наступления.
     */
    private OrderReturnRequest executeStageCommand(CommandContext context,
                                                   ReturnRequestCommandPayload payload,
                                                   ReturnRequestCommandType type,
                                                   StageCommandExecutor executor) {
        StageMarkPayload stagePayload = requirePayload(payload, StageMarkPayload.class, type);
        return executor.execute(context.requestId(),
                context.parcelId(),
                context.user(),
                stagePayload.stageMoment());
    }

    /**
     * Обрабатывает команду, работающую с обменной посылкой.
     */
    private OrderReturnRequest executeExchangeCommand(CommandContext context,
                                                       ReturnRequestCommandPayload payload,
                                                       ReturnRequestCommandType type,
                                                       ExchangeShipmentExecutor executor) {
        ExchangeShipmentPayload exchangePayload = requirePayload(payload, ExchangeShipmentPayload.class, type);
        return executor.execute(context.requestId(),
                context.parcelId(),
                context.user(),
                exchangePayload.exchangeTrack(),
                exchangePayload.stageMoment());
    }

    /**
     * Приводит payload к ожидаемому типу или выбрасывает исключение с понятным описанием.
     */
    private <T extends ReturnRequestCommandPayload> T requirePayload(ReturnRequestCommandPayload payload,
                                                                     Class<T> payloadType,
                                                                     ReturnRequestCommandType type) {
        if (!payloadType.isInstance(payload)) {
            throw new ValidationException("Команда " + type.name()
                    + " передана с неподдерживаемой структурой данных");
        }
        return payloadType.cast(payload);
    }

    /**
     * Выполняет переключение режима заявки.
     */
    private OrderReturnRequest executeModeSwitch(CommandContext context,
                                                 ReturnRequestMode targetMode,
                                                 OrderReturnRequestService.ModeSwitchTrigger trigger) {
        return switch (targetMode) {
            case EXCHANGE -> orderReturnRequestService.setModeExchange(
                    context.requestId(),
                    context.parcelId(),
                    context.user(),
                    trigger
            );
            case RETURN -> orderReturnRequestService.setModeReturn(
                    context.requestId(),
                    context.parcelId(),
                    context.user(),
                    trigger
            );
        };
    }

    /**
     * Определяет, требует ли команда наличия объекта payload.
     */
    private boolean requiresPayload(ReturnRequestCommandType type) {
        return switch (type) {
            case UPDATE_REVERSE_TRACK, REGISTER_EXCHANGE_PARCEL, MARK_EXCHANGE_SENT -> true;
            default -> false;
        };
    }

    /**
     * Контекст выполняемой команды, содержащий ключевые параметры запроса.
     */
    private record CommandContext(UUID requestKey,
                                  Long requestId,
                                  Long parcelId,
                                  User user) {
    }

    /**
     * Стратегия стадийных команд с отметкой времени.
     */
    @FunctionalInterface
    private interface StageCommandExecutor {
        OrderReturnRequest execute(Long requestId, Long parcelId, User user, ZonedDateTime stageMoment);
    }

    /**
     * Стратегия команд, работающих с обменной отправкой.
     */
    @FunctionalInterface
    private interface ExchangeShipmentExecutor {
        OrderReturnRequest execute(Long requestId,
                                   Long parcelId,
                                   User user,
                                   String exchangeTrack,
                                   ZonedDateTime stageMoment);
    }
}
