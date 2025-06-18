package com.project.tracking_system.mapper;

import com.project.tracking_system.dto.UserListAdminInfoDTO;
import com.project.tracking_system.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Маппер пользователей для административного интерфейса.
 */
@Mapper(componentModel = "spring")
public interface UserAdminMapper {

    /**
     * Преобразует пользователя в DTO для списка администрирования.
     *
     * @param user сущность пользователя
     * @return DTO с краткой информацией
     */
    @Mapping(target = "subscriptionPlanName",
            expression = "java(user.getSubscription() != null ? user.getSubscription().getSubscriptionPlan().getName() : \"NONE\")")
    UserListAdminInfoDTO toAdminListDto(User user);
}
