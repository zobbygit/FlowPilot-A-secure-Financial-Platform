package com.flowpilot.households;

import com.flowpilot.audit.FinancialAudit;
import com.flowpilot.common.ApiException;
import com.flowpilot.households.HouseholdDtos.*;
import com.flowpilot.notify.NotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared household finance. Membership is a domain relation (no extra application role). EVERY read and write first proves
 * the caller is a member of that household in SQL, so data is isolated from unrelated users; strangers get a plain 404.
 */
@Service
public class HouseholdService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final FinancialAudit fin;

    public HouseholdService(JdbcTemplate jdbc, NotificationService notifications, FinancialAudit fin) { this.jdbc = jdbc; this.notifications = notifications; this.fin = fin; }

    // ------------------------------------------------------------------ households

    public List<HouseholdSummary> mine(UUID me) {
        return jdbc.query("select h.id, h.name, h.currency, h.owner_id, (select count(*) from household_members x where x.household_id = h.id) as n from households h join household_members m on m.household_id = h.id where m.user_id = ? and h.archived_at is null order by h.created_at",
                (rs, i) -> { UUID id = rs.getObject(1, UUID.class); return new HouseholdSummary(id, rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class).equals(me), rs.getInt(5), balances(id).getOrDefault(me, BigDecimal.ZERO).toPlainString()); }, me);
    }

    @Transactional
    public HouseholdSummary create(UUID me, CreateHousehold r) {
        if (jdbc.queryForObject("select count(*) from household_members m join households h on h.id = m.household_id where m.user_id = ? and h.archived_at is null", Integer.class, me) >= 10) throw ApiException.badRequest("TOO_MANY_HOUSEHOLDS", "You can belong to at most 10 households.");
        UUID id = jdbc.queryForObject("insert into households (name, owner_id, currency) values (?, ?, ?) returning id", UUID.class, r.name().trim(), me, r.currency());
        jdbc.update("insert into household_members (household_id, user_id, member_role) values (?, ?, 'OWNER')", id, me);
        fin.record(me, me, "HOUSEHOLD_CREATED", "HOUSEHOLD", id, r.name().trim(), null, r.currency());
        return new HouseholdSummary(id, r.name().trim(), r.currency(), true, 1, "0");
    }

    public Detail detail(UUID me, UUID id) {
        var h = household(me, id);
        Map<UUID, BigDecimal> bal = balances(id);
        List<Member> members = jdbc.query("select m.user_id, p.full_name, m.member_role from household_members m join user_profiles p on p.user_id = m.user_id where m.household_id = ? order by m.joined_at", (rs, i) -> new Member(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), bal.getOrDefault(rs.getObject(1, UUID.class), BigDecimal.ZERO).toPlainString()), id);
        Map<UUID, String> names = members.stream().collect(Collectors.toMap(Member::userId, Member::name));
        List<Suggestion> suggestions = SplitCalculator.settleUp(bal).stream().map(t -> new Suggestion(names.get(t.from()), names.get(t.to()), t.amount().toPlainString(), t.from(), t.to())).toList();
        List<Invitation> pending = jdbc.query("select i.id, i.expires_at, p.full_name, i.status from household_invitations i join user_profiles p on p.user_id = i.invitee_user_id where i.household_id = ? and i.status = 'PENDING' and i.expires_at > now()",
                (rs, i) -> new Invitation(rs.getObject(1, UUID.class), id, (String) h.get("name"), null, rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getString(4)), id);
        return new Detail(id, (String) h.get("name"), (String) h.get("currency"), h.get("owner_id").equals(me), members, pending, suggestions, budgets(id, (String) h.get("currency")));
    }

    @Transactional
    public void archive(UUID me, UUID id) {
        requireOwner(me, id);
        jdbc.update("update households set archived_at = now() where id = ?", id);
        fin.record(me, me, "HOUSEHOLD_ARCHIVED", "HOUSEHOLD", id, null, "ACTIVE", "ARCHIVED");
    }

    // ------------------------------------------------------------------ invitations & members

    @Transactional
    public void invite(UUID me, UUID id, String handle) {
        requireOwner(me, id);
        UUID invitee = jdbc.query("select ph.user_id from payment_profiles ph join users u on u.id = ph.user_id where ph.handle = ? and u.status = 'ACTIVE' and u.role = 'USER'", (rs, i) -> rs.getObject(1, UUID.class), handle).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("No FlowPilot user has that handle."));
        if (invitee.equals(me) || jdbc.queryForObject("select count(*) from household_members where household_id = ? and user_id = ?", Integer.class, id, invitee) > 0) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "That person is already in this household.");
        if (jdbc.queryForObject("select count(*) from household_members where household_id = ?", Integer.class, id) >= 20) throw ApiException.badRequest("HOUSEHOLD_FULL", "A household can have at most 20 members.");
        try { jdbc.update("insert into household_invitations (household_id, invited_by, invitee_user_id, expires_at) values (?, ?, ?, ?)", id, me, invitee, java.sql.Timestamp.from(Instant.now().plus(14, ChronoUnit.DAYS))); }
        catch (org.springframework.dao.DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT, "ALREADY_INVITED", "That person already has a pending invitation."); }
        String hn = (String) household(me, id).get("name"), who = nameOf(me);
        notifications.notify(invitee, "SYSTEM", "Household invitation", who + " invited you to join “" + hn + "”.", "/app/households", "hinvite:" + id + ":" + invitee + ":" + System.currentTimeMillis());
    }

    public List<Invitation> incoming(UUID me) {
        return jdbc.query("select i.id, i.household_id, h.name, p.full_name, i.expires_at, i.status from household_invitations i join households h on h.id = i.household_id join user_profiles p on p.user_id = i.invited_by where i.invitee_user_id = ? and i.status = 'PENDING' and i.expires_at > now() and h.archived_at is null",
                (rs, i) -> new Invitation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant(), null, rs.getString(6)), me);
    }

    @Transactional
    public void respond(UUID me, UUID invitationId, boolean accept) {
        var rows = jdbc.queryForList("select household_id from household_invitations where id = ? and invitee_user_id = ? and status = 'PENDING' and expires_at > now() for update", invitationId, me);
        if (rows.isEmpty()) throw ApiException.notFound("Invitation not found or expired.");
        UUID hid = (UUID) rows.get(0).get("household_id");
        jdbc.update("update household_invitations set status = ?, responded_at = now() where id = ?", accept ? "ACCEPTED" : "DECLINED", invitationId);
        if (accept) { jdbc.update("insert into household_members (household_id, user_id) values (?, ?) on conflict do nothing", hid, me); fin.record(me, me, "HOUSEHOLD_JOINED", "HOUSEHOLD", hid, null, null, "MEMBER"); }
    }

    @Transactional
    public void cancelInvitation(UUID me, UUID id, UUID invitationId) {
        requireOwner(me, id);
        if (jdbc.update("update household_invitations set status = 'CANCELLED', responded_at = now() where id = ? and household_id = ? and status = 'PENDING'", invitationId, id) == 0) throw ApiException.notFound("Invitation not found.");
    }

    /** The owner removes a member, or a member leaves. Anyone with an unsettled balance must settle first. */
    @Transactional
    public void removeMember(UUID me, UUID id, UUID userId) {
        var h = household(me, id);
        boolean self = me.equals(userId);
        if (!self && !h.get("owner_id").equals(me)) throw ApiException.forbidden("NOT_OWNER", "Only the household owner can remove other members.");
        if (h.get("owner_id").equals(userId)) throw new ApiException(HttpStatus.CONFLICT, "OWNER_CANNOT_LEAVE", "The owner can't leave. Archive the household instead.");
        if (balances(id).getOrDefault(userId, BigDecimal.ZERO).signum() != 0) throw new ApiException(HttpStatus.CONFLICT, "UNSETTLED_BALANCE", "Settle up before leaving or removing this member.");
        if (jdbc.update("delete from household_members where household_id = ? and user_id = ?", id, userId) == 0) throw ApiException.notFound("Member not found.");
        fin.record(me, me, self ? "HOUSEHOLD_LEFT" : "HOUSEHOLD_MEMBER_REMOVED", "HOUSEHOLD", id, null, userId.toString(), null);
    }

    // ------------------------------------------------------------------ expenses

    @Transactional
    public ExpenseView addExpense(UUID me, UUID id, ExpenseRequest r) {
        var h = household(me, id);
        Set<UUID> members = memberIds(id);
        if (!members.contains(r.payerId())) throw ApiException.badRequest("INVALID_PAYER", "The payer must be a household member.");
        if (r.participants().stream().anyMatch(p -> !members.contains(p.userId())) || r.participants().stream().map(ShareInput::userId).distinct().count() != r.participants().size()) throw ApiException.badRequest("INVALID_PARTICIPANTS", "Split between distinct household members only.");
        if (r.occurredOn().isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1))) throw ApiException.badRequest("FUTURE_DATE", "Expenses can't be dated in the future.");
        if (r.categoryId() != null && jdbc.queryForObject("select count(*) from categories where id = ? and kind = 'EXPENSE' and (user_id is null or user_id = ?)", Integer.class, r.categoryId(), me) == 0) throw ApiException.badRequest("INVALID_CATEGORY", "Choose an expense category.");
        Map<UUID, BigDecimal> shares = switch (r.splitType()) {
            case "EQUAL" -> SplitCalculator.equal(r.amount(), r.participants().stream().map(ShareInput::userId).toList());
            case "PERCENT" -> SplitCalculator.percent(r.amount(), r.participants().stream().collect(Collectors.toMap(ShareInput::userId, ShareInput::value)));
            default -> SplitCalculator.exact(r.amount(), r.participants().stream().collect(Collectors.toMap(ShareInput::userId, ShareInput::value)));
        };
        UUID eid = jdbc.queryForObject("insert into household_expenses (household_id, payer_id, amount, description, category_id, occurred_on, split_type, created_by) values (?, ?, ?, ?, ?, ?, ?, ?) returning id", UUID.class,
                id, r.payerId(), r.amount(), r.description().trim(), r.categoryId(), r.occurredOn(), r.splitType(), me);
        shares.forEach((u, s) -> jdbc.update("insert into household_splits (expense_id, user_id, share_amount) values (?, ?, ?)", eid, u, s));
        fin.record(me, me, "HOUSEHOLD_EXPENSE_ADDED", "HOUSEHOLD_EXPENSE", eid, r.description().trim() + " " + r.amount().toPlainString() + " " + h.get("currency") + " (" + r.splitType().toLowerCase() + ")", null, "paid by " + nameOf(r.payerId()));
        return expenses(me, id, 0, 50).items().stream().filter(e -> e.id().equals(eid)).findFirst().orElseThrow();
    }

    public PageOf<ExpenseView> expenses(UUID me, UUID id, int page, int size) {
        household(me, id);
        int sz = Math.min(Math.max(size, 1), 100), pg = Math.max(page, 0);
        UUID owner = (UUID) household(me, id).get("owner_id");
        Long total = jdbc.queryForObject("select count(*) from household_expenses where household_id = ? and deleted_at is null", Long.class, id);
        List<ExpenseView> items = jdbc.query("select e.*, p.full_name as payer_name, c.name as cat from household_expenses e join user_profiles p on p.user_id = e.payer_id left join categories c on c.id = e.category_id where e.household_id = ? and e.deleted_at is null order by e.occurred_on desc, e.created_at desc limit ? offset ?",
                (rs, i) -> {
                    UUID eid = rs.getObject("id", UUID.class);
                    List<ShareView> shares = jdbc.query("select s.user_id, p.full_name, s.share_amount from household_splits s join user_profiles p on p.user_id = s.user_id where s.expense_id = ? order by p.full_name", (r2, j) -> new ShareView(r2.getObject(1, UUID.class), r2.getString(2), r2.getBigDecimal(3).toPlainString()), eid);
                    return new ExpenseView(eid, rs.getString("description"), rs.getBigDecimal("amount").toPlainString(), rs.getObject("occurred_on", LocalDate.class), rs.getString("payer_name"), rs.getObject("payer_id", UUID.class), rs.getString("cat"), rs.getString("split_type"), shares,
                            me.equals(owner) || me.equals(rs.getObject("created_by", UUID.class)));
                }, id, sz, (long) pg * sz);
        return new PageOf<>(items, pg, sz, total == null ? 0 : total);
    }

    /** Soft delete (history is kept). Owner can remove any expense; members only their own. */
    @Transactional
    public void deleteExpense(UUID me, UUID id, UUID expenseId) {
        UUID owner = (UUID) household(me, id).get("owner_id");
        var rows = jdbc.queryForList("select created_by, description, amount from household_expenses where id = ? and household_id = ? and deleted_at is null for update", expenseId, id);
        if (rows.isEmpty()) throw ApiException.notFound("Expense not found.");
        if (!me.equals(owner) && !me.equals(rows.get(0).get("created_by"))) throw ApiException.forbidden("NOT_ALLOWED", "Only the person who added this expense or the household owner can remove it.");
        jdbc.update("update household_expenses set deleted_at = now() where id = ?", expenseId);
        fin.record(me, me, "HOUSEHOLD_EXPENSE_REMOVED", "HOUSEHOLD_EXPENSE", expenseId, rows.get(0).get("description") + " " + rows.get(0).get("amount"), "ACTIVE", "REMOVED");
    }

    @Transactional
    public void settle(UUID me, UUID id, SettlementRequest r) {
        household(me, id);
        Set<UUID> members = memberIds(id);
        if (!members.contains(r.fromUser()) || !members.contains(r.toUser()) || r.fromUser().equals(r.toUser())) throw ApiException.badRequest("INVALID_SETTLEMENT", "Choose two different household members.");
        if (!me.equals(r.fromUser()) && !me.equals(r.toUser())) throw ApiException.forbidden("NOT_ALLOWED", "You can only record settlements you are part of.");
        jdbc.update("insert into household_settlements (household_id, from_user, to_user, amount, note) values (?, ?, ?, ?, ?)", id, r.fromUser(), r.toUser(), r.amount(), r.note() == null || r.note().isBlank() ? null : r.note().trim());
        fin.record(me, me, "HOUSEHOLD_SETTLEMENT", "HOUSEHOLD", id, nameOf(r.fromUser()) + " paid " + nameOf(r.toUser()) + " " + r.amount().toPlainString(), null, r.amount().toPlainString());
        UUID other = me.equals(r.fromUser()) ? r.toUser() : r.fromUser();
        notifications.notify(other, "SYSTEM", "Household settlement recorded", nameOf(me) + " recorded a payment of " + r.amount().toPlainString() + ".", "/app/households", "hsettle:" + UUID.randomUUID());
    }

    // ------------------------------------------------------------------ budgets

    @Transactional
    public void addBudget(UUID me, UUID id, BudgetRequest r) {
        requireOwner(me, id);
        if (r.categoryId() != null && jdbc.queryForObject("select count(*) from categories where id = ? and kind = 'EXPENSE' and (user_id is null or user_id = ?)", Integer.class, r.categoryId(), me) == 0) throw ApiException.badRequest("INVALID_CATEGORY", "Choose an expense category.");
        jdbc.update("insert into household_budgets (household_id, name, category_id, limit_amount, created_by) values (?, ?, ?, ?, ?)", id, r.name().trim(), r.categoryId(), r.limitAmount(), me);
        fin.record(me, me, "HOUSEHOLD_BUDGET_CREATED", "HOUSEHOLD", id, r.name().trim() + " limit " + r.limitAmount().toPlainString(), null, "ACTIVE");
    }

    @Transactional
    public void archiveBudget(UUID me, UUID id, UUID budgetId) {
        requireOwner(me, id);
        if (jdbc.update("update household_budgets set archived_at = now() where id = ? and household_id = ? and archived_at is null", budgetId, id) == 0) throw ApiException.notFound("Budget not found.");
    }

    // ------------------------------------------------------------------ internals

    /** Net balance per member: positive = is owed money, negative = owes. Always sums to zero. */
    Map<UUID, BigDecimal> balances(UUID id) {
        Map<UUID, BigDecimal> bal = new LinkedHashMap<>();
        memberIds(id).forEach(u -> bal.put(u, BigDecimal.ZERO));
        jdbc.query("select payer_id, sum(amount) from household_expenses where household_id = ? and deleted_at is null group by payer_id", rs -> { bal.merge(rs.getObject(1, UUID.class), rs.getBigDecimal(2), BigDecimal::add); }, id);
        jdbc.query("select s.user_id, sum(s.share_amount) from household_splits s join household_expenses e on e.id = s.expense_id where e.household_id = ? and e.deleted_at is null group by s.user_id", rs -> { bal.merge(rs.getObject(1, UUID.class), rs.getBigDecimal(2).negate(), BigDecimal::add); }, id);
        jdbc.query("select from_user, to_user, amount from household_settlements where household_id = ?", rs -> {   // A paid B: A owes less, B is owed less
            bal.merge(rs.getObject(1, UUID.class), rs.getBigDecimal(3), BigDecimal::add); bal.merge(rs.getObject(2, UUID.class), rs.getBigDecimal(3).negate(), BigDecimal::add); }, id);
        return bal;
    }

    private List<BudgetView> budgets(UUID id, String currency) {
        LocalDate first = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        return jdbc.query("select b.id, b.name, c.name, b.limit_amount, b.category_id from household_budgets b left join categories c on c.id = b.category_id where b.household_id = ? and b.archived_at is null order by b.created_at", (rs, i) -> {
            UUID cat = rs.getObject(5, UUID.class); BigDecimal limit = rs.getBigDecimal(4);
            BigDecimal spent = cat == null ? jdbc.queryForObject("select coalesce(sum(amount), 0) from household_expenses where household_id = ? and deleted_at is null and occurred_on >= ?", BigDecimal.class, id, first)
                    : jdbc.queryForObject("select coalesce(sum(amount), 0) from household_expenses where household_id = ? and deleted_at is null and occurred_on >= ? and category_id = ?", BigDecimal.class, id, first, cat);
            return new BudgetView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), limit.toPlainString(), spent.toPlainString(), spent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP).doubleValue());
        }, id);
    }

    private Map<String, Object> household(UUID me, UUID id) {
        var rows = jdbc.queryForList("select h.* from households h join household_members m on m.household_id = h.id where h.id = ? and m.user_id = ? and h.archived_at is null", id, me);
        if (rows.isEmpty()) throw ApiException.notFound("Household not found.");
        return rows.get(0);
    }

    private void requireOwner(UUID me, UUID id) {
        if (!household(me, id).get("owner_id").equals(me)) throw ApiException.forbidden("NOT_OWNER", "Only the household owner can do that.");
    }

    private Set<UUID> memberIds(UUID id) { return new LinkedHashSet<>(jdbc.query("select user_id from household_members where household_id = ? order by joined_at", (rs, i) -> rs.getObject(1, UUID.class), id)); }

    private String nameOf(UUID user) { return jdbc.queryForObject("select full_name from user_profiles where user_id = ?", String.class, user); }
}
