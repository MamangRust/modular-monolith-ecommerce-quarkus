package com.sanedge.transaction.repository.statsbymerchant;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;
import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;
import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;
import com.sanedge.transaction.entity.TransactionYearlyAmountSuccess;
import com.sanedge.transaction.domain.requests.FindMonthlyAmountByMerchantRequest;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionAmountByMerchantRepository implements PanacheRepository<Transaction> {

    public Uni<List<TransactionMonthlyAmountSuccess>> findMonthlySuccessByMerchant(FindMonthlyAmountByMerchantRequest req) {
        Long merchantId = req.getMerchantId();
        Integer year = req.getYear();
        Integer month = req.getMonth();

        java.time.LocalDate currentMonth = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate prevMonthDate = currentMonth.minusMonths(1);

        Integer prevYear = prevMonthDate.getYear();
        Integer prevMonth = prevMonthDate.getMonthValue();

        String sql = """
            WITH
                monthly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                        CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER) AS "month",
                        CAST(COUNT(*) AS BIGINT) AS total_success,
                        CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                    FROM transactions t
                    WHERE
                        t.deleted_at IS NULL
                        AND t.payment_status = 'SUCCESS'
                        AND t.merchant_id = ?1
                        AND (
                            (t.created_at >= MAKE_DATE(?2, ?3, 1)
                             AND t.created_at < (MAKE_DATE(?2, ?3, 1) + INTERVAL '1 month'))
                            OR
                            (t.created_at >= MAKE_DATE(?4, ?5, 1)
                             AND t.created_at < (MAKE_DATE(?4, ?5, 1) + INTERVAL '1 month'))
                        )
                    GROUP BY 1, 2
                ),
                formatted_data AS (
                    SELECT
                        CAST("year" AS VARCHAR) AS "year",
                        TO_CHAR(MAKE_DATE(2000, "month", 1), 'Mon') AS "month",
                        total_success AS totalSuccess,
                        total_amount AS totalAmount,
                        "year" AS "sort_year",
                        "month" AS "sort_month"
                    FROM monthly_data
                    UNION ALL
                    SELECT
                        ?6,
                        TO_CHAR(MAKE_DATE(2000, ?3, 1), 'Mon'),
                        0, 0, ?2, ?3
                    WHERE NOT EXISTS (
                        SELECT 1 FROM monthly_data
                        WHERE CAST("year" AS VARCHAR) = ?6 AND CAST("month" AS VARCHAR) = ?7
                    )
                    UNION ALL
                    SELECT
                        ?8,
                        TO_CHAR(MAKE_DATE(2000, ?5, 1), 'Mon'),
                        0, 0, ?4, ?5
                    WHERE NOT EXISTS (
                        SELECT 1 FROM monthly_data
                        WHERE CAST("year" AS VARCHAR) = ?8 AND CAST("month" AS VARCHAR) = ?9
                    )
                )
            SELECT * FROM formatted_data
            ORDER BY "sort_year" DESC, "sort_month" DESC
            """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter(1, merchantId);
            dataQuery.setParameter(2, year);
            dataQuery.setParameter(3, month);
            dataQuery.setParameter(4, prevYear);
            dataQuery.setParameter(5, prevMonth);
            dataQuery.setParameter(6, year.toString());
            dataQuery.setParameter(7, month.toString());
            dataQuery.setParameter(8, prevYear.toString());
            dataQuery.setParameter(9, prevMonth.toString());

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

    public Uni<List<TransactionYearlyAmountSuccess>> findYearlySuccessByMerchant(
            Long merchantId,
            Integer year) {
        Integer prevYear = year - 1;

        String sql = """
            WITH
                yearly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                        CAST(COUNT(*) AS BIGINT) AS total_success,
                        CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                    FROM transactions t
                    WHERE
                        t.deleted_at IS NULL
                        AND t.payment_status = 'SUCCESS'
                        AND t.merchant_id = ?1
                        AND (EXTRACT(YEAR FROM t.created_at) = ?2
                             OR EXTRACT(YEAR FROM t.created_at) = ?2 - 1)
                    GROUP BY 1
                ),
                formatted_data AS (
                    SELECT
                        CAST("year" AS VARCHAR) AS "year",
                        total_success AS totalSuccess,
                        total_amount AS totalAmount
                    FROM yearly_data
                    UNION ALL
                    SELECT ?3, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE CAST("year" AS VARCHAR) = ?3)
                    UNION ALL
                    SELECT ?4, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE CAST("year" AS VARCHAR) = ?4)
                )
            SELECT * FROM formatted_data
            ORDER BY "year" DESC
            """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter(1, merchantId);
            dataQuery.setParameter(2, year);
            dataQuery.setParameter(3, year.toString());
            dataQuery.setParameter(4, prevYear.toString());

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

    public Uni<List<TransactionMonthlyAmountFailed>> findMonthlyFailedByMerchant(FindMonthlyAmountByMerchantRequest req) {
        Long merchantId = req.getMerchantId();
        Integer year = req.getYear();
        Integer month = req.getMonth();

        java.time.LocalDate currentMonth = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate prevMonthDate = currentMonth.minusMonths(1);

        Integer prevYear = prevMonthDate.getYear();
        Integer prevMonth = prevMonthDate.getMonthValue();

        String sql = """
            WITH
                monthly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                        CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER) AS "month",
                        CAST(COUNT(*) AS BIGINT) AS total_failed,
                        CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                    FROM transactions t
                    WHERE
                        t.deleted_at IS NULL
                        AND t.payment_status = 'FAILED'
                        AND t.merchant_id = ?1
                        AND (
                            (t.created_at >= MAKE_DATE(?2, ?3, 1)
                             AND t.created_at < (MAKE_DATE(?2, ?3, 1) + INTERVAL '1 month'))
                            OR
                            (t.created_at >= MAKE_DATE(?4, ?5, 1)
                             AND t.created_at < (MAKE_DATE(?4, ?5, 1) + INTERVAL '1 month'))
                        )
                    GROUP BY 1, 2
                ),
                formatted_data AS (
                    SELECT
                        CAST("year" AS VARCHAR) AS "year",
                        TO_CHAR(MAKE_DATE(2000, "month", 1), 'Mon') AS "month",
                        total_failed AS totalFailed,
                        total_amount AS totalAmount,
                        "year" AS "sort_year",
                        "month" AS "sort_month"
                    FROM monthly_data
                    UNION ALL
                    SELECT
                        ?6,
                        TO_CHAR(MAKE_DATE(2000, ?3, 1), 'Mon'),
                        0, 0, ?2, ?3
                    WHERE NOT EXISTS (
                        SELECT 1 FROM monthly_data
                        WHERE CAST("year" AS VARCHAR) = ?6 AND CAST("month" AS VARCHAR) = ?7
                    )
                    UNION ALL
                    SELECT
                        ?8,
                        TO_CHAR(MAKE_DATE(2000, ?5, 1), 'Mon'),
                        0, 0, ?4, ?5
                    WHERE NOT EXISTS (
                        SELECT 1 FROM monthly_data
                        WHERE CAST("year" AS VARCHAR) = ?8 AND CAST("month" AS VARCHAR) = ?9
                    )
                )
            SELECT * FROM formatted_data
            ORDER BY "sort_year" DESC, "sort_month" DESC
            """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter(1, merchantId);
            dataQuery.setParameter(2, year);
            dataQuery.setParameter(3, month);
            dataQuery.setParameter(4, prevYear);
            dataQuery.setParameter(5, prevMonth);
            dataQuery.setParameter(6, year.toString());
            dataQuery.setParameter(7, month.toString());
            dataQuery.setParameter(8, prevYear.toString());
            dataQuery.setParameter(9, prevMonth.toString());

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

    public Uni<List<TransactionYearlyAmountFailed>> findYearlyFailedByMerchant(
            Long merchantId,
            Integer year) {
        Integer prevYear = year - 1;

        String sql = """
            WITH
                yearly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                        CAST(COUNT(*) AS BIGINT) AS total_failed,
                        CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                    FROM transactions t
                    WHERE
                        t.deleted_at IS NULL
                        AND t.payment_status = 'FAILED'
                        AND t.merchant_id = ?1
                        AND (EXTRACT(YEAR FROM t.created_at) = ?2
                             OR EXTRACT(YEAR FROM t.created_at) = ?2 - 1)
                    GROUP BY 1
                ),
                formatted_data AS (
                    SELECT
                        CAST("year" AS VARCHAR) AS "year",
                        total_failed AS totalFailed,
                        total_amount AS totalAmount
                    FROM yearly_data
                    UNION ALL
                    SELECT ?3, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE CAST("year" AS VARCHAR) = ?3)
                    UNION ALL
                    SELECT ?4, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE CAST("year" AS VARCHAR) = ?4)
                )
            SELECT * FROM formatted_data
            ORDER BY "year" DESC
            """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter(1, merchantId);
            dataQuery.setParameter(2, year);
            dataQuery.setParameter(3, year.toString());
            dataQuery.setParameter(4, prevYear.toString());

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
