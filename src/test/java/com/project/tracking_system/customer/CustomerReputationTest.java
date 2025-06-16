package com.project.tracking_system.customer;

import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.Customer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Проверка расчёта репутации покупателя.
 */
class CustomerReputationTest {

    @ParameterizedTest
    @CsvSource({
            "0,0,NEW",
            "2,0,NEW",
            "2,1,NEUTRAL",
            "4,1,RELIABLE",
            "2,2,NEUTRAL",
            "1,3,UNRELIABLE"
    })
    void reputationCalculatedCorrectly(int pickedUp, int returned, BuyerReputation expected) {
        Customer customer = new Customer();
        customer.setPickedUpCount(pickedUp);
        customer.setReturnedCount(returned);
        customer.recalculateReputation();
        assertEquals(expected, customer.getReputation());
    }
}
