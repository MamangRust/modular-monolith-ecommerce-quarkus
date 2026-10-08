package com.sanedge.order.repository.statsbymerchant;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.order.entity.Order;
import com.sanedge.order.entity.OrderMonthlyTotalRevenue;
import com.sanedge.order.entity.OrderYearlyTotalRevenue;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderTotalRevenueByMerchantRepository implements PanacheRepository<Order> {

    public Uni<List<OrderMonthlyTotalRevenue>> findMonthlyTotalRevenueByMerchant(
            com.sanedge.order.domain.requests.MonthTotalRevenueMerchantRequest req) {
        Long merchantId = req.getMerchantId() != null ? req.getMerchantId().longValue() : null;
        Integer year = req.getYear();
        Integer month = req.getMonth();

        java.time.LocalDate currentMonth = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate nextMonth = currentMonth.plusMonths(1);

        Integer year1 = year;
        Integer month1 = month;
        Integer year2 = nextMonth.getYear();
        Integer month2 = nextMonth.getMonthValue();

        String sql = """
                WITH
                    monthly_revenue AS (
                        SELECT
                            CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER) AS "year",
                            CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER) AS month_num,
                            CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS total_revenue
                        FROM orders o
                        JOIN order_items oi ON o.id = oi.order_id
                        WHERE
                            o.deleted_at IS NULL
                            AND oi.deleted_at IS NULL
                            AND o.merchant_id = :merchantId
                            AND (
                                (EXTRACT(YEAR FROM o.created_at) = :year1 AND EXTRACT(MONTH FROM o.created_at) = :month1)
                                OR (EXTRACT(YEAR FROM o.created_at) = :year2 AND EXTRACT(MONTH FROM o.created_at) = :month2)
                            )
                        GROUP BY
                            CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER),
                            CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER)
                    ),
                    all_months AS (
                        SELECT
                            CAST(:year1 AS INTEGER) AS "year",
                            CAST(:month1 AS INTEGER) AS month_num,
                            TO_CHAR(MAKE_DATE(2000, :month1, 1), 'FMMonth') AS month_name
                        UNION
                        SELECT
                            CAST(:year2 AS INTEGER) AS "year",
                            CAST(:month2 AS INTEGER) AS month_num,
                            TO_CHAR(MAKE_DATE(2000, :month2, 1), 'FMMonth') AS month_name
                    )
                SELECT
                    am."year"::VARCHAR AS "year",
                    am.month_name AS "month",
                    COALESCE(mr.total_revenue, 0) AS totalRevenue
                FROM all_months am
                LEFT JOIN monthly_revenue mr
                       ON am."year" = mr."year"
                      AND am.month_num = mr.month_num
                ORDER BY am."year" DESC, am.month_num DESC
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("merchantId", merchantId);
            dataQuery.setParameter("year1", year1);
            dataQuery.setParameter("month1", month1);
            dataQuery.setParameter("year2", year2);
            dataQuery.setParameter("month2", month2);

            return dataQuery.getResultList().map(results -> {
                List<OrderMonthlyTotalRevenue> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    OrderMonthlyTotalRevenue om = new OrderMonthlyTotalRevenue();
                    om.setYear((String) row[0]);
                    om.setMonth((String) row[1]);
                    om.setTotalRevenue(((Number) row[2]).longValue());
                    list.add(om);
                }
                return list;
            });
        });
    }

    public Uni<List<OrderYearlyTotalRevenue>> findYearlyTotalRevenueByMerchant(Long merchantId, Integer year) {
        String sql = """
                WITH
                    yearly_revenue AS (
                        SELECT
                            CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER) AS "year",
                            CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS total_revenue
                        FROM orders o
                        JOIN order_items oi ON o.id = oi.order_id
                        WHERE
                            o.deleted_at IS NULL
                            AND oi.deleted_at IS NULL
                            AND o.merchant_id = :merchantId
                            AND (
                                EXTRACT(YEAR FROM o.created_at) = :year
                                OR EXTRACT(YEAR FROM o.created_at) = :year - 1
                            )
                        GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER)
                    ),
                    all_years AS (
                        SELECT CAST(:year AS INTEGER) AS "year"
                        UNION
                        SELECT CAST(:year - 1 AS INTEGER) AS "year"
                    )
                SELECT
                    CAST(ay."year" AS VARCHAR) AS "year",
                    CAST(COALESCE(yr.total_revenue, 0) AS BIGINT) AS totalRevenue
                FROM all_years ay
                LEFT JOIN yearly_revenue yr ON ay."year" = yr."year"
                ORDER BY ay."year" DESC
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("merchantId", merchantId);
            dataQuery.setParameter("year", year);

            return dataQuery.getResultList().map(results -> {
                List<OrderYearlyTotalRevenue> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    OrderYearlyTotalRevenue oy = new OrderYearlyTotalRevenue();
                    oy.setYear((String) row[0]);
                    oy.setTotalRevenue(((Number) row[1]).longValue());
                    list.add(oy);
                }
                return list;
            });
        });
    }
}
