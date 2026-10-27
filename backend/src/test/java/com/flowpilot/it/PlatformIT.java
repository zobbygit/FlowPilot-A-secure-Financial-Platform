package com.flowpilot.it;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.common.ApiException;
import com.flowpilot.domain.Role;
import com.flowpilot.finance.AccountService;
import com.flowpilot.finance.AccountType;
import com.flowpilot.finance.FinanceDtos.CreateAccountRequest;
import com.flowpilot.finance.FinanceDtos.PostTransactionRequest;
import com.flowpilot.finance.LedgerService;
import com.flowpilot.finance.TransactionType;
import com.flowpilot.households.HouseholdDtos.CreateHousehold;
import com.flowpilot.households.HouseholdService;
import com.flowpilot.payments.PaymentDtos.InitiateRequest;
import com.flowpilot.payments.PaymentService;
import com.flowpilot.security.AuthPrincipal;
import com.flowpilot.support.SupportDtos.CreateTicket;
import com.flowpilot.support.SupportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration tests against a real PostgreSQL 17 (Docker required). They check what unit tests with mocks cannot:
 * that the Flyway schema applies, that database-level guarantees hold (immutability triggers, CHECK constraints), that money
 * moves atomically between two users, and that one user can never reach another user's data.
 */
@SpringBootTest
@Testcontainers
class PlatformIT {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;
    @Autowired LedgerService ledger;
    @Autowired PaymentService payments;
    @Autowired SupportService support;
    @Autowired HouseholdService households;

    private UUID newUser(String name) {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
        UUID id = jdbc.queryForObject("insert into users (email, password_hash, role, status, email_verified_at) values (?, 'x', 'USER', 'ACTIVE', now()) returning id", UUID.class, email);
        jdbc.update("insert into user_profiles (user_id, full_name) values (?, ?)", id, name);
        return id;
    }

    private UUID account(UUID user, String opening) {
        return accounts.create(user, new CreateAccountRequest("Main", AccountType.BANK, "USD", new BigDecimal(opening), false)).id();
    }

    private BigDecimal balance(UUID account) { return jdbc.queryForObject("select current_balance from accounts where id = ?", BigDecimal.class, account); }
    private BigDecimal ledgerSum(UUID account) { return jdbc.queryForObject("select coalesce(sum(amount), 0) from ledger_entries where account_id = ?", BigDecimal.class, account); }
    private static AuthPrincipal principal(UUID user) { return new AuthPrincipal(user, UUID.randomUUID(), Role.USER, "x@example.test"); }

    @Test
    void everyMigrationAppliesCleanly() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertEquals(14, applied);
    }

    @Test
    void balanceAlwaysEqualsTheSumOfItsLedgerEntriesAndEntriesCannotBeEdited() {
        UUID u = newUser("Ana"); UUID acct = account(u, "100");
        ledger.post(u, new PostTransactionRequest(TransactionType.EXPENSE, new BigDecimal("30.25"), LocalDate.now(ZoneOffset.UTC), acct, null, null, "t", null, null, null), null);
        assertEquals(0, new BigDecimal("69.75").compareTo(balance(acct)));
        assertEquals(0, balance(acct).compareTo(ledgerSum(acct)));
        assertThrows(DataAccessException.class, () -> jdbc.update("update ledger_entries set amount = 1 where account_id = ?", acct));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from ledger_entries where account_id = ?", acct));
    }

    @Test
    void theDatabaseItselfRefusesAnOverdraftOnANormalAccount() {
        UUID acct = account(newUser("Bo"), "10");
        assertThrows(DataAccessException.class, () -> jdbc.update("update accounts set current_balance = -5 where id = ?", acct));
        UUID u = jdbc.queryForObject("select user_id from accounts where id = ?", UUID.class, acct);
        ApiException ex = assertThrows(ApiException.class, () -> ledger.post(u, new PostTransactionRequest(TransactionType.EXPENSE, new BigDecimal("10.01"), LocalDate.now(ZoneOffset.UTC), acct, null, null, "x", null, null, null), null));
        assertEquals("INSUFFICIENT_FUNDS", ex.code());
        assertEquals(0, new BigDecimal("10").compareTo(balance(acct)));
    }

    @Test
    void aRetriedPostingWithTheSameIdempotencyKeyMovesMoneyOnce() {
        UUID u = newUser("Cy"); UUID acct = account(u, "100");
        var req = new PostTransactionRequest(TransactionType.EXPENSE, new BigDecimal("40"), LocalDate.now(ZoneOffset.UTC), acct, null, null, "once", null, null, null);
        var first = ledger.post(u, req, "key-123");
        var second = ledger.post(u, req, "key-123");
        assertEquals(first.id(), second.id());
        assertEquals(0, new BigDecimal("60").compareTo(balance(acct)));
    }

    @Test
    void aSimulatedPaymentMovesMoneyBetweenTwoUsersAtomicallyAndOnlyOnce() {
        UUID a = newUser("Dee"), b = newUser("Eli"); UUID accA = account(a, "100"), accB = account(b, "0");
        String handleB = payments.profile(b).handle(); payments.profile(a);
        var pending = payments.initiate(a, new InitiateRequest(handleB, null, accA, new BigDecimal("25"), "lunch"), "k1");
        assertEquals(0, new BigDecimal("100").compareTo(balance(accA)), "nothing moves before confirmation");
        payments.confirm(a, pending.id());
        payments.confirm(a, pending.id());   // repeating confirmation must not move money twice
        assertEquals(0, new BigDecimal("75").compareTo(balance(accA)));
        assertEquals(0, new BigDecimal("25").compareTo(balance(accB)));
        assertEquals(0, new BigDecimal("100").compareTo(balance(accA).add(balance(accB))), "total money is conserved");
        payments.refund(b, pending.id(), "changed my mind");
        assertEquals(0, new BigDecimal("100").compareTo(balance(accA)));
        assertEquals(0, BigDecimal.ZERO.compareTo(balance(accB)));
        assertThrows(ApiException.class, () -> payments.refund(b, pending.id(), "again"), "a payment can be refunded only once");
    }

    @Test
    void oneUserCanNeverReadAnotherUsersSupportTicketOrHousehold() {
        UUID a = newUser("Fay"), b = newUser("Gus");
        UUID ticket = support.create(a, new CreateTicket("Help", "OTHER", "LOW", "details", null)).id();
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> support.detail(principal(b), ticket)).code());
        UUID house = households.create(a, new CreateHousehold("Flat", "USD")).id();
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> households.detail(b, house)).code());
        assertDoesNotThrow(() -> support.detail(principal(a), ticket));
        assertDoesNotThrow(() -> households.detail(a, house));
    }

    @Test
    void auditTablesRefuseEditsAndDeletes() {
        UUID u = newUser("Hal");
        ledger.getClass();   // ensure the context is up
        jdbc.update("insert into audit_events (scope, event_type, outcome, actor_user_id) values ('USER', 'TEST_EVENT', 'SUCCESS', ?)", u);
        assertThrows(DataAccessException.class, () -> jdbc.update("update audit_events set outcome = 'FAILURE' where actor_user_id = ?", u));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from audit_events where actor_user_id = ?", u));
    }
}
