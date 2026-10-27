package com.flowpilot.payments;

import com.flowpilot.payments.PaymentDtos.EventView;
import com.flowpilot.payments.PaymentDtos.PaymentView;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Printable confirmation. Prominently marked SIMULATED: it is not a bank receipt and no real money moved. */
final class PaymentReceiptPdf {
    private PaymentReceiptPdf() {}

    static byte[] render(PaymentView p, String you, List<EventView> events) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 56, 56);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20), warn = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, java.awt.Color.RED), body = FontFactory.getFont(FontFactory.HELVETICA, 11), small = FontFactory.getFont(FontFactory.HELVETICA, 9);
            doc.add(new Paragraph("FlowPilot — Simulated payment confirmation", h1));
            doc.add(new Paragraph("SIMULATED PAYMENT — NO REAL MONEY WAS MOVED. This is not a bank receipt.", warn));
            doc.add(new Paragraph(" "));
            String[][] rows = {{"Reference", p.reference()}, {"Status", p.status()}, {"Amount", p.amount() + " " + p.currency()},
                    {p.direction().equals("SENT") ? "From" : "To", you}, {p.direction().equals("SENT") ? "To" : "From", p.counterpartyName() + (p.counterpartyHandle() == null ? "" : " (@" + p.counterpartyHandle() + ")")},
                    {"Note", p.note() == null ? "—" : p.note()}, {"Created", p.createdAt().toString()}, {"Last update", p.updatedAt().toString()}};
            for (String[] r : rows) doc.add(new Paragraph(r[0] + ": " + r[1], body));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("History", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            for (EventView e : events) doc.add(new Paragraph(e.createdAt() + " — " + (e.fromStatus() == null ? "" : e.fromStatus() + " → ") + e.toStatus() + (e.note() == null ? "" : " (" + e.note() + ")"), small));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed", e);
        }
    }
}
