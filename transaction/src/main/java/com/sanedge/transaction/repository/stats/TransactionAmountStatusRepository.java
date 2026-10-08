package com.sanedge.transaction.repository.stats;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;
import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;
import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;
import com.sanedge.transaction.entity.TransactionYearlyAmountSuccess;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionAmountStatusRepository implements PanacheRepository<Transaction> {

    public Uni<List<TransactionMonthlyAmountSuccess>> findMonthlyTransactionSuccess(
            Integer year,
            Integer month) {
        java.time.LocalDate currentMonth = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate prevMonthDate = currentMonth.minusMonths(1);

        Integer prevYear = prevMonthDate.getYear();
        Integer prevMonth = prevMonthDate.getMonthValue();

        String sql = """
            WITH monthly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                    CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER) AS "month",
                    COUNT(*) AS total_success,
                    CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                FROM transactions t
                WHERE
                    t.deleted_at IS NULL
                    AND t.payment_status = 'SUCCESS'
                    AND (
                        (t.created_at >= MAKE_DATE(?1, ?2, 1)
                         AND t.created_at < (MAKE_DATE(?1, ?2, 1) + INTERVAL '1 month'))
                        OR
                        (t.created_at >= MAKE_DATE(?3, ?4, 1)
                         AND t.created_at < (MAKE_DATE(?3, ?4, 1) + INTERVAL '1 month'))
                    )
                GROUP BY 1, 2
            ),
            formatted_data AS (
                SELECT
                    CAST("year" AS VARCHAR) AS "year",
                    TO_CHAR(MAKE_DATE(2000, "month", 1), 'Mon') AS "month",
                    CAST(total_success AS BIGINT) AS totalSuccess,
                    CAST(total_amount AS BIGINT) AS totalAmount,
                    "year" AS "sort_year",
                    "month" AS "sort_month"
                FROM monthly_data
                UNION ALL
                SELECT ?5,
                       TO_CHAR(MAKE_DATE(2000, ?2, 1), 'Mon'),
                       CAST(0 AS BIGINT), CAST(0 AS BIGINT), ?1, ?2
                WHERE NOT EXISTS (
                    SELECT 1 FROM monthly_data
                    WHERE CAST("year" AS VARCHAR) = ?5 AND CAST("month" AS VARCHAR) = ?6
                )
                UNION ALL
                SELECT ?7,
                       TO_CHAR(MAKE_DATE(2000, ?4, 1), 'Mon'),
                       CAST(0 AS BIGINT), CAST(0 AS BIGINT), ?3, ?4
                WHERE NOT EXISTS (
                    SELECT 1 FROM monthly_data
                    WHERE CAST("year" AS VARCHAR) = ?7 AND CAST("month" AS VARCHAR) = ?8
                )
            )
            SELECT "year", "month", totalSuccess, totalAmount FROM formatted_data
            ORDER BY "sort_year" DESC, "sort_month" DESC
            """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter(1, year);
            dataQuery.setParameter(2, month);
            dataQuery.setParameter(3, prevYear);
            dataQuery.setParameter(4, prevMonth);
            dataQuery.setParameter(5, year.toString());
            dataQuery.setParameter(6, month.toString());
            dataQuery.setParameter(7, prevYear.toString());
            dataQuery.setParameter(8, prevMonth.toString());

            return dataQuery.getResultList().map(results -> {
                List<TransactionMonthlyAmountSuccess> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionMonthlyAmountSuccess obj = new TransactionMonthlyAmountSuccess();
                    obj.setYear((String) row[0]);
                    obj.setMonth((String) row[1]);
                    obj.setTotalSuccess(((Number) row[2]).longValue());
                    obj.setTotalAmount(((Number) row[3]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionYearlyAmountSuccess>> findYearlyTransactionSuccess(Integer year) {
        Integer prevYear = year - 1;

        String sql = """
            WITH yearly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                    COUNT(*) AS total_success,
                    CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                FROM transactions t
                WHERE
                    t.deleted_at IS NULL
                    AND t.payment_status = 'SUCCESS'
                    AND (EXTRACT(YEAR FROM t.created_at) = ?1
                         OR EXTRACT(YEAR FROM t.created_at) = ?1 - 1)
                GROUP BY 1
            ),
            formatted_data AS (
                SELECT
                    CAST("year" AS VARCHAR) AS "year",
                    CAST(total_success AS BIGINT) AS totalSuccess,
                    CAST(total_amount AS BIGINT) AS totalAmount
                FROM yearly_data
                UNION ALL
                SELECT ?2, CAST(0 AS BIGINT), CAST(0 AS BIGINT) WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE CAST("year" AS VARCHAR) = ?2)
                UNION ALL
                SELECT ?3, CAST(0 AS BIGINT), CAST(0 AS BIGINT) WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE CAST("year" AS VARCHAR) = ?3)
            )
            SELECT * FROM formatted_data
            ORDER BY "year" DESC
            """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter(1, year);
            dataQuery.setParameter(2, year.toString());
            dataQuery.setParameter(3, prevYear.toString());

            return dataQuery.getResultList().map(results -> {
                List<TransactionYearlyAmountSuccess> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionYearlyAmountSuccess obj = new TransactionYearlyAmountSuccess();
                    obj.setYear((String) row[0]);
                    obj.setTotalSuccess(((Number) row[1]).longValue());
                    obj.setTotalAmount(((Number) row[2]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionMonthlyAmountFailed>> findMonthlyTransactionFailed(
            Integer year,
            Integer month) {
        java.time.LocalDate currentMonth = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate prevMonthDate = currentMonth.minusMonths(1);

        Integer prevYear = prevMonthDate.getYear();
        Integer prevMonth = prevMonthDate.getMonthValue();

        String sql = """
            WITH monthly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                    CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER) AS "month",
                    COUNT(*) AS total_failed,
                    CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                FROM transactions t
                WHERE
                    t.deleted_at IS NULL
                    AND t.payment_status = 'FAILED'
                    AND (
                        (t.created_at >= MAKE_DATE(?1, ?2, 1)
                         AND t.created_at < (MAKE_DATE(?1, ?2, 1) + INTERVAL '1 month'))
                        OR
                        (t.created_at >= MAKE_DATE(?3, ?4, 1)
                         AND t.created_at < (MAKE_DATE(?3, ?4, 1) + INTERVAL '1 month'))
                    )
                GROUP BY 1, 2
            ),
            formatted_data AS (
                SELECT
                    CAST("year" AS VARCHAR) AS "year",
                    TO_CHAR(MAKE_DATE(2000, "month", 1), 'Mon') AS "month",
                    CAST(total_failed AS BIGINT) AS totalFailed,
                    CAST(total_amount AS BIGINT) AS totalAmount,
                    "year" AS "sort_year",
                    "month" AS "sort_month"
                FROM monthly_data
                UNION ALL
                SELECT ?5,
                       TO_CHAR(MAKE_DATE(2000, ?2, 1), 'Mon'),
                       CAST(0 AS BIGINT), CAST(0 AS BIGINT), ?1, ?2
                WHERE NOT EXISTS (
                    SELECT 1 FROM monthly_data
                    WHERE CAST("year" AS VARCHAR) = ?5 AND CAST("month" AS VARCHAR) = ?6
                )
                UNION ALL
                SELECT ?7,
                       TO_CHAR(MAKE_DATE(2000, ?4, 1), 'Mon'),
                       CAST(0 AS BIGINT), CAST(0 AS BIGINT), ?3, ?4
                WHERE NOT EXISTS (
                    SELECT 1 FROM monthly_data
                    WHERE CAST("year" AS VARCHAR) = ?7 AND CAST("month" AS VARCHAR) = ?8
                )
            )
            SELECT "year", "month", totalFailed, totalAmount FROM formatted_data
            ORDER BY "sort_year" DESC, "sort_month" DESC
            """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter(1, year);
            dataQuery.setParameter(2, month);
            dataQuery.setParameter(3, prevYear);
            dataQuery.setParameter(4, prevMonth);
            dataQuery.setParameter(5, year.toString());
            dataQuery.setParameter(6, month.toString());
            dataQuery.setParameter(7, prevYear.toString());
            dataQuery.setParameter(8, prevMonth.toString());

            return dataQuery.getResultList().map(results -> {
                List<TransactionMonthlyAmountFailed> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionMonthlyAmountFailed obj = new TransactionMonthlyAmountFailed();
                    obj.setYear((String) row[0]);
                    obj.setMonth((String) row[1]);
                    obj.setTotalFailed(((Number) row[2]).longValue());
                    obj.setTotalAmount(((Number) row[3]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionYearlyAmountFailed>> findYearlyTransactionFailed(Integer year) {
        Integer prevYear = year - 1;

        String sql = """
            WITH yearly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                    COUNT(*) AS total_failed,
                    CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                FROM transactions t
                WHERE
                    t.deleted_at IS NULL
                    AND t.payment_status = 'FAILED'
                    AND (EXTRACT(YEAR FROM t.created_at) = ?1
                         OR EXTRACT(YEAR FROM t.created_at) = ?1 - 1)
                GROUP BY 1
            ),
            formatted_data AS (
                SELECT
                    CAST("year" AS VARCHAR) AS "year",
                    CAST(total_failed AS BIGINT) AS totalFailed,
                    CAST(total_amount AS BIGINT) AS totalAmount
                FROM yearly_data
                UNION ALL
                SELECT ?2, CAST(0 AS BIGINT), CAST(0 AS BIGINT) WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE CAST("year" AS VARCHAR) = ?2)
                UNION ALL
                SELECT ?3, CAST(0 AS BIGINT), CAST(0 AS BIGINT) WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE CAST("year" AS VARCHAR) = ?3)
            )
            SELECT * FROM formatted_data
            ORDER BY "year" DESC
            """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter(1, year);
            dataQuery.setParameter(2, year.toString());
            dataQuery.setParameter(3, prevYear.toString());

            return dataQuery.getResultList().map(results -> {
                List<TransactionYearlyAmountFailed> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionYearlyAmountFailed obj = new TransactionYearlyAmountFailed();
                    obj.setYear((String) row[0]);
                    obj.setTotalFailed(((Number) row[1]).longValue());
                    obj.setTotalAmount(((Number) row[2]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }
}
