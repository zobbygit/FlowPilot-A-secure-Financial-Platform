package com.flowpilot.templates;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.common.ApiException;
import com.flowpilot.templates.TemplateDtos.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateValidationTest {
    private final TemplateService service = new TemplateService(null, null, null, null, null);

    private static FieldDef f(String key, String type, String binding, boolean required) {
        return new FieldDef(key, key, type, binding, required, null, null, null, null);
    }

    @Test
    void fieldValuesAreValidatedByType() {
        FieldDef amount = new FieldDef("amount", "Amount", "AMOUNT", "amount", true, null, null, new BigDecimal("0.01"), new BigDecimal("1000"));
        assertEquals("12.50", service.clean(amount, " 12.50 ", null));
        assertThrows(ApiException.class, () -> service.clean(amount, "-3", null));
        assertThrows(ApiException.class, () -> service.clean(amount, "1.23456", null));
        assertThrows(ApiException.class, () -> service.clean(amount, "5000", null));
        assertThrows(ApiException.class, () -> service.clean(amount, "", null));
    }

    @Test
    void dropdownAcceptsOnlyListedValuesNotLabels() {
        FieldDef d = new FieldDef("dir", "Cash", "DROPDOWN", "type", true, "INCOME|Cash received,EXPENSE|Cash spent", null, null, null);
        assertEquals("INCOME", service.clean(d, "INCOME", null));
        assertThrows(ApiException.class, () -> service.clean(d, "Cash received", null));
        assertThrows(ApiException.class, () -> service.clean(d, "TRANSFER", null));
    }

    @Test
    void optionalEmptyIsNullAndCheckboxDefaultsToFalse() {
        assertNull(service.clean(f("note", "TEXT", "notes", false), "  ", null));
        assertEquals("false", service.clean(f("c", "CHECKBOX", "notes", false), null, null));
        assertThrows(ApiException.class, () -> service.clean(f("d", "DATE", "occurredOn", true), "31/12/2026", null));
    }

    @Test
    void definitionsMustBindToRealTargetsAndIncludeRequiredOnes() {
        var ok = new TemplateRequest("T", null, "TRANSACTION", "EXPENSE", List.of(f("amount", "AMOUNT", "amount", true), f("acct", "ACCOUNT", "accountId", true)));
        assertDoesNotThrow(() -> service.validateDefinition(ok));

        var missing = new TemplateRequest("T", null, "TRANSACTION", "EXPENSE", List.of(f("amount", "AMOUNT", "amount", true)));
        assertEquals("MISSING_FIELD", assertThrows(ApiException.class, () -> service.validateDefinition(missing)).code());

        var badBinding = new TemplateRequest("T", null, "MEMO", null, List.of(f("title", "TEXT", "title", true), f("x", "AMOUNT", "amount", false)));
        assertEquals("INVALID_BINDING", assertThrows(ApiException.class, () -> service.validateDefinition(badBinding)).code());

        var dup = new TemplateRequest("T", null, "MEMO", null, List.of(f("title", "TEXT", "title", true), f("title", "TEXT", "body", false)));
        assertEquals("DUPLICATE_FIELD", assertThrows(ApiException.class, () -> service.validateDefinition(dup)).code());

        var notRequired = new TemplateRequest("T", null, "TRANSACTION", "EXPENSE", List.of(f("amount", "AMOUNT", "amount", false), f("acct", "ACCOUNT", "accountId", true)));
        assertEquals("MUST_BE_REQUIRED", assertThrows(ApiException.class, () -> service.validateDefinition(notRequired)).code());
    }

    @Test
    void cannotBindTransactionTypeToArbitraryOptions() {
        var bad = new TemplateRequest("T", null, "TRANSACTION", null, List.of(
                new FieldDef("dir", "Dir", "DROPDOWN", "type", true, "TRANSFER|Move", null, null, null), f("amount", "AMOUNT", "amount", true), f("acct", "ACCOUNT", "accountId", true)));
        assertEquals("INVALID_OPTIONS", assertThrows(ApiException.class, () -> service.validateDefinition(bad)).code());
    }
}
