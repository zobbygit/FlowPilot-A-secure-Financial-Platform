package com.flowpilot.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.common.ApiException;
import org.junit.jupiter.api.Test;

class SettingsValidationTest {
    @Test
    void currencyListMustBeRealUniqueIsoCodes() {
        assertDoesNotThrow(() -> SettingsService.validate("currencies.supported", "USD,EUR,INR"));
        assertThrows(ApiException.class, () -> SettingsService.validate("currencies.supported", "USD,XXQ"));
        assertThrows(ApiException.class, () -> SettingsService.validate("currencies.supported", "usd"));
        assertThrows(ApiException.class, () -> SettingsService.validate("currencies.supported", "USD,USD"));
        assertThrows(ApiException.class, () -> SettingsService.validate("currencies.supported", ""));
    }

    @Test
    void numericSettingsHaveSafeBounds() {
        assertDoesNotThrow(() -> SettingsService.validate("payment.max_amount", "250000.50"));
        assertThrows(ApiException.class, () -> SettingsService.validate("payment.max_amount", "0"));
        assertThrows(ApiException.class, () -> SettingsService.validate("payment.max_amount", "99999999999"));
        assertThrows(ApiException.class, () -> SettingsService.validate("payment.max_amount", "1.234"));
        assertDoesNotThrow(() -> SettingsService.validate("payment.pending_ttl_minutes", "30"));
        assertThrows(ApiException.class, () -> SettingsService.validate("payment.pending_ttl_minutes", "0"));
        assertThrows(ApiException.class, () -> SettingsService.validate("support.reopen_days", "91"));
    }

    @Test
    void unknownOrProtectedKeysAreRejected() {
        assertThrows(ApiException.class, () -> SettingsService.validate("session.idle_timeout", "PT99H"));
        assertThrows(ApiException.class, () -> SettingsService.validate("cookie.secure", "false"));
    }
}
