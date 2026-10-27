package com.flowpilot.documents;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DocumentDtos {
    private DocumentDtos() {}

    public record FieldDef(@NotBlank @Size(max = 60) String label,
                           @NotBlank @Pattern(regexp = "MANUAL|USER_NAME|TX_AMOUNT|TX_DATE|TX_MERCHANT|TX_DESCRIPTION|TX_CATEGORY|TX_ACCOUNT|PAY_REFERENCE|PAY_AMOUNT|PAY_PARTIES|PAY_STATUS|TODAY") String source) {}

    public record TemplateRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Pattern(regexp = "EXPENSE_VOUCHER|REIMBURSEMENT|PAYMENT_CONFIRMATION|CUSTOM") String kind,
                                  @NotBlank @Size(max = 80) String title, @Size(max = 80) String brandName, @Size(max = 200) String headerText, @Size(max = 200) String footerText,
                                  @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String accentColor, @NotBlank @Pattern(regexp = "HELVETICA|TIMES|COURIER") String fontFamily,
                                  @Min(8) @Max(16) int fontSize, @NotBlank @Pattern(regexp = "PORTRAIT|LANDSCAPE") String orientation, @NotEmpty @Size(max = 20) List<@Valid FieldDef> fields) {}

    public record TemplateView(UUID id, String name, String kind, String title, String brandName, String headerText, String footerText, String accentColor, String fontFamily, int fontSize,
                               String orientation, boolean hasLogo, boolean builtIn, boolean archived, List<FieldDef> fields) {}

    public record GenerateRequest(UUID transactionId, UUID paymentId, @Size(max = 20) Map<@Size(max = 40) String, @Size(max = 200) String> manual) {}
    public record ResolvedField(String label, String value, String source) {}
    public record Preview(TemplateView template, List<ResolvedField> fields, String disclaimer) {}
}
