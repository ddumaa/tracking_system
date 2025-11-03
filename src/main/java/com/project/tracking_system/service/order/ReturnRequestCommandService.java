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
import com.project.tracking_system.repository.ReturnCommandLogRepository;
import com.project.tracking_system.service.order.payload.ExchangeShipmentPayload;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayload;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayloadFactory;
import com.project.tracking_system.service.order.payload.StageMarkPayload;
import com.project.tracking_system.service.order.payload.UpdateDetailsPayload;
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
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
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
public class ReturnRequestCommandService {

    private static final String HASH_ALGORITHM = "SHA-256";

    private final OrderReturnRequestService orderReturnRequestService;
    private final ReturnRequestMapper returnRequestMapper;
    private final ReturnCommandLogRepository returnCommandLogRepository;
    private final ObjectMapper objectMapper;
    private final ReturnRequestCommandPayloadFactory payloadFactory;
    private final Map<ReturnRequestCommandType, ReturnRequestCommandHandler> commandHandlers;

    /**
     * Конструирует сервис и регистрирует обработчики доступных команд.
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
        this.commandHandlers = createHandlers(orderReturnRequestService);
    }

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
        ReturnRequestCommandPayload payload = payloadFactory.create(commandType, command.payload());
        String payloadHash = computePayloadHash(commandType, command, payload);

        ReturnCommandLog logEntry = reserveLogEntry(requestId, normalizedKey, commandType, payloadHash);
        if (logEntry.getResponseSnapshot() != null) {
            return restoreResponse(logEntry);
        }

        CommandContext commandContext = new CommandContext(requestId, parcelId, user);
        OrderReturnRequest updated = performCommand(commandType, payload, commandContext);
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
     * Выполняет бизнес-логику команды через соответствующий сервис.
     */
    private OrderReturnRequest performCommand(ReturnRequestCommandType type,
                                              ReturnRequestCommandPayload payload,
                                              CommandContext context) {
        ReturnRequestCommandHandler handler = commandHandlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Команда " + type.name() + " не поддерживается системой");
        }
        return handler.handle(context, payload);
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

    /**
     * Формирует реестр обработчиков команд на основании сервисных методов доменного слоя.
     */
    private Map<ReturnRequestCommandType, ReturnRequestCommandHandler> createHandlers(OrderReturnRequestService service) {
        EnumMap<ReturnRequestCommandType, ReturnRequestCommandHandler> handlers = new EnumMap<>(ReturnRequestCommandType.class);
        handlers.put(ReturnRequestCommandType.SET_MODE_EXCHANGE,
                modeSwitchHandler(ReturnRequestMode.EXCHANGE, OrderReturnRequestService.ModeSwitchTrigger.MANUAL_DECISION));
        handlers.put(ReturnRequestCommandType.SET_MODE_RETURN,
                modeSwitchHandler(ReturnRequestMode.RETURN, OrderReturnRequestService.ModeSwitchTrigger.MANUAL_DECISION));
        handlers.put(ReturnRequestCommandType.CANCEL_EXCHANGE, this::handleCancelExchange);
        handlers.put(ReturnRequestCommandType.CLOSE_REQUEST, this::handleCloseRequest);
        handlers.put(ReturnRequestCommandType.REGISTER_EXCHANGE_PARCEL,
                exchangeHandler(ReturnRequestCommandType.REGISTER_EXCHANGE_PARCEL, service::registerExchangeParcel));
        handlers.put(ReturnRequestCommandType.MARK_EXCHANGE_SENT,
                exchangeHandler(ReturnRequestCommandType.MARK_EXCHANGE_SENT, service::markExchangeSent));
        handlers.put(ReturnRequestCommandType.MARK_EXCHANGE_DELIVERED,
                stageHandler(ReturnRequestCommandType.MARK_EXCHANGE_DELIVERED, service::markExchangeDelivered));
        handlers.put(ReturnRequestCommandType.MARK_OUTBOUND_SENT,
                stageHandler(ReturnRequestCommandType.MARK_OUTBOUND_SENT, service::markOutboundSent));
        handlers.put(ReturnRequestCommandType.MARK_INBOUND_ARRIVED,
                stageHandler(ReturnRequestCommandType.MARK_INBOUND_ARRIVED, service::markInboundArrived));
        handlers.put(ReturnRequestCommandType.MARK_INBOUND_PICKED_UP,
                stageHandler(ReturnRequestCommandType.MARK_INBOUND_PICKED_UP, service::markInboundPickedUp));
        handlers.put(ReturnRequestCommandType.UPDATE_REVERSE_TRACK,
                this::handleUpdateReverseTrack);
        return Map.copyOf(handlers);
    }

    /**
     * Обрабатывает команду обновления обратного трека и возвращает актуальную заявку.
     */
    private OrderReturnRequest handleUpdateReverseTrack(CommandContext context, ReturnRequestCommandPayload payload) {
        UpdateDetailsPayload updatePayload = requirePayload(payload, UpdateDetailsPayload.class,
                ReturnRequestCommandType.UPDATE_REVERSE_TRACK);
        orderReturnRequestService.updateReverseTrackAndComment(
                context.requestId(),
                context.parcelId(),
                context.user(),
                updatePayload.reverseTrack(),
                updatePayload.comment()
        );
        return orderReturnRequestService.getOwnedRequest(context.requestId(), context.user());
    }

    /**
     * Выполняет закрытие заявки, учитывая её текущий статус.
     */
    private OrderReturnRequest handleCloseRequest(CommandContext context, ReturnRequestCommandPayload payload) {
        OrderReturnRequest request = orderReturnRequestService.getOwnedRequest(context.requestId(), context.user());
        return switch (request.getStatus()) {
            case REGISTERED -> orderReturnRequestService.closeWithoutExchange(context.requestId(),
                    context.parcelId(),
                    context.user());
            case EXCHANGE_APPROVED -> throw new IllegalStateException("Для обменных заявок используйте отмену обмена");
            case CLOSED_NO_EXCHANGE -> throw new IllegalStateException("Заявка уже закрыта");
        };
    }

    /**
     * Обрабатывает отмену обмена с последующим закрытием заявки.
     */
    private OrderReturnRequest handleCancelExchange(CommandContext context, ReturnRequestCommandPayload payload) {
        orderReturnRequestService.switchMode(
                context.requestId(),
                context.parcelId(),
                context.user(),
                ReturnRequestMode.RETURN,
                OrderReturnRequestService.ModeSwitchTrigger.EXCHANGE_CANCELLATION
        );
        return orderReturnRequestService.closeWithoutExchange(
                context.requestId(),
                context.parcelId(),
                context.user()
        );
    }

    /**
     * Формирует обработчик команд без полезной нагрузки.
     */
    private ReturnRequestCommandHandler simpleHandler(SimpleCommandExecutor executor) {
        return (context, payload) -> executor.execute(context.requestId(), context.parcelId(), context.user());
    }

    /**
     * Формирует обработчик стадийных команд, требующих отметки времени.
     */
    private ReturnRequestCommandHandler stageHandler(ReturnRequestCommandType type,
                                                    StageCommandExecutor executor) {
        return (context, payload) -> {
            StageMarkPayload stagePayload = requirePayload(payload, StageMarkPayload.class, type);
            return executor.execute(context.requestId(),
                    context.parcelId(),
                    context.user(),
                    stagePayload.stageMoment());
        };
    }

    /**
     * Формирует обработчик команд, работающих с обменными отправлениями.
     */
    private ReturnRequestCommandHandler exchangeHandler(ReturnRequestCommandType type,
                                                        ExchangeShipmentExecutor executor) {
        return (context, payload) -> {
            ExchangeShipmentPayload exchangePayload = requirePayload(payload, ExchangeShipmentPayload.class, type);
            return executor.execute(context.requestId(),
                    context.parcelId(),
                    context.user(),
                    exchangePayload.exchangeTrack(),
                    exchangePayload.stageMoment());
        };
    }

    /**
     * Приводит payload к ожидаемому типу или выбрасывает исключение с понятным описанием.
     */
    private <T extends ReturnRequestCommandPayload> T requirePayload(ReturnRequestCommandPayload payload,
                                                                     Class<T> payloadType,
                                                                     ReturnRequestCommandType type) {
        if (!payloadType.isInstance(payload)) {
            throw new IllegalArgumentException("Команда " + type.name()
                    + " передана с неподдерживаемой структурой данных");
        }
        return payloadType.cast(payload);
    }

    /**
     * Формирует обработчик переключения режима заявки.
     */
    private ReturnRequestCommandHandler modeSwitchHandler(ReturnRequestMode targetMode,
                                                         OrderReturnRequestService.ModeSwitchTrigger trigger) {
        return (context, payload) -> orderReturnRequestService.switchMode(
                context.requestId(),
                context.parcelId(),
                context.user(),
                targetMode,
                trigger
        );
    }

    /**
     * Контекст выполняемой команды, содержащий ключевые параметры запроса.
     */
    private record CommandContext(Long requestId,
                                  Long parcelId,
                                  User user) {
    }

    /**
     * Интерфейс обработчика конкретной команды.
     */
    @FunctionalInterface
    private interface ReturnRequestCommandHandler {
        OrderReturnRequest handle(CommandContext context, ReturnRequestCommandPayload payload);
    }

    /**
     * Стратегия команд без дополнительных параметров.
     */
    @FunctionalInterface
    private interface SimpleCommandExecutor {
        OrderReturnRequest execute(Long requestId, Long parcelId, User user);
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
