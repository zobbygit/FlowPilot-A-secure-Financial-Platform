package com.flowpilot.templates;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class TemplateDtos {
    private TemplateDtos() {}

    public record FieldDef(
            @NotBlank @Pattern(regexp = "^[a-z][a-zA-Z0-9]{0,29}$", message = "Field key must start with a lowercase letter (letters/digits only).") String key,
            @NotBlank @Size(max = 60) String label,
            @NotBlank @Pattern(regexp = "TEXT|AMOUNT|NUMBER|DATE|DROPDOWN|CHECKBOX|TAGS|FILE|CATEGORY|ACCOUNT|TRANSACTION") String type,
            @NotBlank @Size(max = 24) String binding,
            boolean required,
            @Size(max = 400) String options,
            @Size(max = 200) String defaultValue,
            BigDecimal min, BigDecimal max) {}

    public record TemplateRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String description,
            @NotBlank @Pattern(regexp = "TRANSACTION|MEMO|BILL") String targetType,
            @Pattern(regexp = "INCOME|EXPENSE") String fixedType,
            @NotEmpty @Size(max = 20) List<@Valid FieldDef> fields) {}

    public record TemplateView(UUID id, String name, String description, String targetType, String fixedType, boolean builtIn, boolean archived, List<FieldDef> fields) {}

    /** Result of creating a record from a template. */
    public record CreatedRecord(String targetType, UUID id) {}
}
