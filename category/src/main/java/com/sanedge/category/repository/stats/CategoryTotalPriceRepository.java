package com.sanedge.category.repository.stats;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.category.entity.CategoriesMonthlyTotalPrice;
import com.sanedge.category.entity.CategoriesYearlyTotalPrice;
import com.sanedge.category.entity.Category;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryTotalPriceRepository implements PanacheRepository<Category> {

    public Uni<List<CategoriesMonthlyTotalPrice>> findMonthlyTotalPrice(Integer year, Integer month) {
        java.time.LocalDate currentMonth = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate nextMonth = currentMonth.plusMonths(1);

        Integer startYear = year;
        Integer startMonth = month;
        Integer endYear = nextMonth.getYear();
        Integer endMonth = nextMonth.getMonthValue();

        String sql = """
            WITH monthly_totals AS (
                    SELECT
                        EXTRACT(YEAR FROM o.created_at)::INTEGER AS "year",
                        EXTRACT(MONTH FROM o.created_at)::INTEGER AS month_num,
                        TO_CHAR(o.created_at, 'FMMonth') AS month_name,
                        CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue
                    FROM orders o
                    JOIN order_items oi ON o.id = oi.order_id
                    JOIN products p ON oi.product_id = p.id
                    JOIN categories c ON p.category_id = c.id
                    WHERE
                        o.deleted_at IS NULL
                        AND oi.deleted_at IS NULL
                        AND (
                            (EXTRACT(YEAR FROM o.created_at) = :startYear AND EXTRACT(MONTH FROM o.created_at) = :startMonth)
                            OR
                            (EXTRACT(YEAR FROM o.created_at) = :endYear AND EXTRACT(MONTH FROM o.created_at) = :endMonth)
                        )
                    GROUP BY EXTRACT(YEAR FROM o.created_at),
                             EXTRACT(MONTH FROM o.created_at),
                             TO_CHAR(o.created_at, 'FMMonth')
                ),
                all_months AS (
                    SELECT CAST(:startYear AS INTEGER) AS "year",
                           CAST(:startMonth AS INTEGER) AS month_num,
                           TO_CHAR(MAKE_DATE(:startYear, :startMonth, 1), 'FMMonth') AS month_name
                    UNION
                    SELECT CAST(:endYear AS INTEGER) AS "year",
                           CAST(:endMonth AS INTEGER) AS month_num,
                           TO_CHAR(MAKE_DATE(:endYear, :endMonth, 1), 'FMMonth') AS month_name
                )
               SELECT
                    CAST(am."year" AS VARCHAR) AS "year",
                    am.month_name AS "month",
                    COALESCE(mt.totalRevenue, 0) AS totalRevenue
                FROM all_months am
                LEFT JOIN monthly_totals mt
                    ON am."year" = mt."year" AND am.month_num = mt.month_num
                ORDER BY am."year" DESC, am.month_num DESC
            """;

        return Panache.getSession().chain(session -> session.createNativeQuery(sql)
                .setParameter("startYear", startYear)
                .setParameter("startMonth", startMonth)
                .setParameter("endYear", endYear)
                .setParameter("endMonth", endMonth)
                .getResultList()
                .map(list -> {
                    List<CategoriesMonthlyTotalPrice> resultList = new ArrayList<>();
                    for (Object item : list) {
                        Object[] row = (Object[]) item;
                        resultList.add(new CategoriesMonthlyTotalPrice(
                            (String) row[0],
                            (String) row[1],
                            row[2] != null ? ((Number) row[2]).longValue() : null
                        ));
                    }
                    return resultList;
                })
        );
    }

    public Uni<List<CategoriesYearlyTotalPrice>> findYearlyTotalPrice(Integer year) {
        String sql = """
            WITH yearly_data AS (
                SELECT
                    EXTRACT(YEAR FROM o.created_at)::INTEGER AS "year",
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue
                FROM
                    orders o
                    JOIN
                    order_items oi ON o.id = oi.order_id
                    JOIN
                    products p ON oi.product_id = p.id
                    JOIN
                    categories c ON p.category_id = c.id
                WHERE
                    o.deleted_at IS NULL
                    AND oi.deleted_at IS NULL
                    AND p.deleted_at IS NULL
                    AND c.deleted_at IS NULL
                    AND (
                        EXTRACT(YEAR FROM o.created_at) = :year
                        OR EXTRACT(YEAR FROM o.created_at) = :year - 1
                    )
                GROUP BY
                    EXTRACT(YEAR FROM o.created_at)
            ),
            all_years AS (
                SELECT CAST(:year AS INTEGER) AS "year"
                UNION
                SELECT CAST(:year - 1 AS INTEGER) AS "year"
            )
            SELECT
                CAST(a."year" AS VARCHAR) AS "year",
                CAST(COALESCE(yd.totalRevenue, 0) AS BIGINT) AS totalRevenue
            FROM
                all_years a
            LEFT JOIN
                yearly_data yd ON a.\"year\" = yd.\"year\"
            ORDER BY
                a.\"year\" DESC
            """;

        return Panache.getSession().chain(session -> session.createNativeQuery(sql)
                .setParameter("year", year)
                .getResultList()
                .map(list -> {
                    List<CategoriesYearlyTotalPrice> resultList = new ArrayList<>();
                    for (Object item : list) {
                        Object[] row = (Object[]) item;
                        resultList.add(new CategoriesYearlyTotalPrice(
                            (String) row[0],
                            row[1] != null ? ((Number) row[1]).longValue() : null
                        ));
                    }
                    return resultList;
                })
        );
    }
}
