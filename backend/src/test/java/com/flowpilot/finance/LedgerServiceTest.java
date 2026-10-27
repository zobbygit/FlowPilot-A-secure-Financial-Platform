package com.flowpilot.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flowpilot.common.ApiException;
import com.flowpilot.finance.FinanceDtos.PostTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {
    @Mock AccountRepository accounts;
    @Mock CategoryRepository categories;
    @Mock FinanceTransactionRepository transactions;
    @Mock LedgerEntryRepository ledger;
    @Mock JdbcTemplate jdbc;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock com.flowpilot.audit.FinancialAudit fin;

    private LedgerService service;
    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new LedgerService(accounts, categories, transactions, ledger, jdbc, events, fin);
    }

    private Account account(UUID id, String currency, String balance, boolean allowNegative) {
        Account a = new Account(userId, "Main", AccountType.BANK, currency, allowNegative);
        a.setCurrentBalance(new BigDecimal(balance));
        when(accounts.findActiveForUpdate(id, userId)).thenReturn(Optional.of(a));
        return a;
    }

    private PostTransactionRequest req(TransactionType type, String amount, UUID account, UUID to) {
        return new PostTransactionRequest(type, new BigDecimal(amount), today, account, to, null, "test", null, null, null);
    }

    @Test
    void expenseDecreasesTheBalanceAndWritesOneNegativeLedgerEntry() {
        UUID id = UUID.randomUUID();
        Account a = account(id, "USD", "100.0000", false);
        when(transactions.save(any(FinanceTransaction.class))).thenAnswer(i -> i.getArgument(0));

        service.post(userId, req(TransactionType.EXPENSE, "40.25", id, null), null);

        assertEquals(0, new BigDecimal("59.75").compareTo(a.getCurrentBalance()));
        ArgumentCaptor<LedgerEntry> entry = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger).save(entry.capture());
        assertEquals(0, new BigDecimal("-40.25").compareTo(entry.getValue().getAmount()));
    }

    @Test
    void anOverdrawingExpenseIsRejectedAndNothingIsWritten() {
        UUID id = UUID.randomUUID();
        Account a = account(id, "USD", "10.0000", false);
        when(transactions.save(any(FinanceTransaction.class))).thenAnswer(i -> i.getArgument(0));

        ApiException ex = assertThrows(ApiException.class, () -> service.post(userId, req(TransactionType.EXPENSE, "10.01", id, null), null));

        assertEquals("INSUFFICIENT_FUNDS", ex.code());
        assertEquals(0, new BigDecimal("10").compareTo(a.getCurrentBalance()));
        verify(ledger, never()).save(any());
    }

    @Test
    void accountsOwnedBySomeoneElseLookLikeTheyDoNotExist() {
        UUID id = UUID.randomUUID();
        when(accounts.findActiveForUpdate(id, userId)).thenReturn(Optional.empty());
        when(transactions.save(any(FinanceTransaction.class))).thenAnswer(i -> i.getArgument(0));

        ApiException ex = assertThrows(ApiException.class, () -> service.post(userId, req(TransactionType.INCOME, "5", id, null), null));
        assertEquals("NOT_FOUND", ex.code());
    }

    @Test
    void transferMovesMoneyBetweenAccountsAndKeepsTheTotalConstant() {
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID();
        Account from = account(a1, "USD", "100.0000", false);
        Account to = account(a2, "USD", "5.0000", false);
        when(transactions.save(any(FinanceTransaction.class))).thenAnswer(i -> i.getArgument(0));

        service.post(userId, req(TransactionType.TRANSFER, "30", a1, a2), null);

        assertEquals(0, new BigDecimal("70").compareTo(from.getCurrentBalance()));
        assertEquals(0, new BigDecimal("35").compareTo(to.getCurrentBalance()));
        verify(ledger, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void transferValidationRejectsSameAccountAndMixedCurrencies() {
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID();
        when(transactions.save(any(FinanceTransaction.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals("INVALID_TRANSFER", assertThrows(ApiException.class,
                () -> service.post(userId, req(TransactionType.TRANSFER, "1", a1, a1), null)).code());

        account(a1, "USD", "100", false);
        account(a2, "EUR", "0", false);
        assertEquals("CURRENCY_MISMATCH", assertThrows(ApiException.class,
                () -> service.post(userId, req(TransactionType.TRANSFER, "1", a1, a2), null)).code());
    }

    @Test
    void futureDatedAndUnsupportedTypesAreRefused() {
        UUID id = UUID.randomUUID();
        var future = new PostTransactionRequest(TransactionType.EXPENSE, BigDecimal.TEN, today.plusDays(5), id, null, null, null, null, null, null);
        assertEquals("FUTURE_DATE", assertThrows(ApiException.class, () -> service.post(userId, future, null)).code());
        assertEquals("UNSUPPORTED_TYPE", assertThrows(ApiException.class,
                () -> service.post(userId, req(TransactionType.OPENING_BALANCE, "1", id, null), null)).code());
    }

    @Test
    void reversingRestoresTheBalanceAndMarksTheOriginalReversed() {
        UUID txId = UUID.randomUUID(), accId = UUID.randomUUID();
        FinanceTransaction original = new FinanceTransaction(userId, TransactionType.EXPENSE, today, "Coffee", null, null, null, null, null);
        Account a = account(accId, "USD", "60.0000", false);
        when(transactions.findByIdAndUserId(txId, userId)).thenReturn(Optional.of(original));
        when(ledger.findByTransactionId(any())).thenReturn(List.of(new LedgerEntry(null, accId, userId, new BigDecimal("-40.0000"), "USD")));
        when(transactions.save(any(FinanceTransaction.class))).thenAnswer(i -> i.getArgument(0));

        service.reverse(userId, txId);

        assertEquals(0, new BigDecimal("100").compareTo(a.getCurrentBalance()));
        assertEquals(TxStatus.REVERSED, original.getStatus());
    }

    @Test
    void aReversalCannotBeReversedAgain() {
        UUID txId = UUID.randomUUID();
        FinanceTransaction reversal = new FinanceTransaction(userId, TransactionType.EXPENSE, today, "r", null, null, null, null, UUID.randomUUID());
        when(transactions.findByIdAndUserId(txId, userId)).thenReturn(Optional.of(reversal));
        assertEquals("NOT_REVERSIBLE", assertThrows(ApiException.class, () -> service.reverse(userId, txId)).code());
    }
}
