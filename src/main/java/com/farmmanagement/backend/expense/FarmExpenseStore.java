package com.farmmanagement.backend.expense;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FarmExpenseStore {
    private final JdbcTemplate db;
    public FarmExpenseStore(JdbcTemplate db) { this.db = db; }

    public record FarmOption(int id, String name) {}
    public record SeasonOption(int id, int farmId, String plotName, String cropName, String status) {}

    public List<FarmOption> farms() {
        return db.query("SELECT farm_id,farm_name FROM public.farm ORDER BY farm_name,farm_id",
            (r,n) -> new FarmOption(r.getInt("farm_id"), r.getString("farm_name")));
    }

    public List<SeasonOption> seasons() {
        return db.query("""
            SELECT s.season_id,p.farm_id,p.plot_name,c.crop_name,s.status
            FROM public.crop_season s JOIN public.plot p USING(plot_id)
            JOIN public.crop c USING(crop_id) ORDER BY s.season_id DESC
            """, (r,n) -> new SeasonOption(r.getInt("season_id"), r.getInt("farm_id"),
                r.getString("plot_name"), r.getString("crop_name"), r.getString("status")));
    }

    private static final String FROM = """
        FROM public.farm_expense e JOIN public.farm f ON f.farm_id=e.farm_id
        LEFT JOIN public.crop_season s ON s.season_id=e.season_id
        LEFT JOIN public.crop c ON c.crop_id=s.crop_id
        LEFT JOIN public.plot p ON p.plot_id=s.plot_id
        """;
    private static final String SELECT = "SELECT e.*, f.farm_name, c.crop_name, p.plot_name " + FROM;

    private FarmExpense row(ResultSet r, int n) throws SQLException {
        return new FarmExpense(r.getInt("expense_id"), r.getInt("farm_id"), r.getString("farm_name"),
            r.getObject("season_id", Integer.class), r.getString("crop_name"), r.getString("plot_name"),
            ExpenseCategory.valueOf(r.getString("category")), r.getBigDecimal("amount"),
            r.getDate("expense_date").toLocalDate(), r.getString("description"),
            r.getObject("created_by", Integer.class), r.getTimestamp("created_at").toInstant());
    }

    public int create(int farmId, Integer seasonId, ExpenseCategory category, BigDecimal amount,
                      LocalDate date, String description, int actor) {
        return db.queryForObject("""
            INSERT INTO public.farm_expense(farm_id,season_id,category,amount,expense_date,description,created_by)
            VALUES (?,?,?,?,?,?,?) RETURNING expense_id
            """, Integer.class, farmId, seasonId, category.name(), amount, java.sql.Date.valueOf(date), description, actor);
    }

    public Optional<FarmExpense> byId(int id) {
        return db.query(SELECT + " WHERE e.expense_id=?", this::row, id).stream().findFirst();
    }

    // Lock the relationship while recording an expense so concurrent edits cannot move its season.
    public boolean seasonBelongsToFarm(int seasonId, int farmId) {
        return !db.queryForList("""
            SELECT s.season_id FROM public.crop_season s JOIN public.plot p ON p.plot_id=s.plot_id
            WHERE s.season_id=? AND p.farm_id=? FOR SHARE OF s,p
            """, Integer.class, seasonId, farmId).isEmpty();
    }

    private record Query(String where, List<Object> args) {}

    private Query filter(ExpenseFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (filter.farmId() != null) { where.append(" AND e.farm_id=?"); args.add(filter.farmId()); }
        if (filter.seasonId() != null) { where.append(" AND e.season_id=?"); args.add(filter.seasonId()); }
        if (filter.category() != null) { where.append(" AND e.category=?"); args.add(filter.category().name()); }
        if (filter.fromDate() != null) { where.append(" AND e.expense_date>=?"); args.add(java.sql.Date.valueOf(filter.fromDate())); }
        if (filter.toDate() != null) { where.append(" AND e.expense_date<=?"); args.add(java.sql.Date.valueOf(filter.toDate())); }
        return new Query(where.toString(), args);
    }

    public List<FarmExpense> history(ExpenseFilter filter, int offset, int limit) {
        Query q = filter(filter);
        List<Object> args = new ArrayList<>(q.args());
        args.add(limit); args.add(offset);
        return db.query(SELECT + q.where() + " ORDER BY e.expense_date DESC,e.expense_id DESC LIMIT ? OFFSET ?",
            this::row, args.toArray());
    }

    public record Totals(long count, BigDecimal totalAmount) {}
    public record CategoryTotal(ExpenseCategory category, long count,
                                @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount) {}

    public Totals totals(ExpenseFilter filter) {
        Query q = filter(filter);
        return db.queryForObject("SELECT count(*) AS count,COALESCE(sum(e.amount),0) AS total_amount "
            + FROM + q.where(), (r, n) -> new Totals(r.getLong("count"), r.getBigDecimal("total_amount")), q.args().toArray());
    }

    public List<CategoryTotal> totalsByCategory(ExpenseFilter filter) {
        Query q = filter(filter);
        return db.query("SELECT e.category,count(*) AS count,sum(e.amount) AS total_amount "
            + FROM + q.where() + " GROUP BY e.category ORDER BY e.category",
            (r, n) -> new CategoryTotal(ExpenseCategory.valueOf(r.getString("category")),
                r.getLong("count"), r.getBigDecimal("total_amount")), q.args().toArray());
    }
}
