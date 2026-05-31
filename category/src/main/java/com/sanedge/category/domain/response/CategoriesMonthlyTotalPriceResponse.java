package com.sanedge.category.domain.response;

import java.util.List;

import com.sanedge.category.entity.CategoriesMonthlyTotalPrice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriesMonthlyTotalPriceResponse {
    private String year;
    private String month;
    private Long totalRevenue;

    public static CategoriesMonthlyTotalPriceResponse from(CategoriesMonthlyTotalPrice response) {
        return CategoriesMonthlyTotalPriceResponse.builder()
                .year(response.getYear())
                .month(response.getMonth())
                .totalRevenue(response.getTotalRevenue())
                .build();
    }

    public static List<CategoriesMonthlyTotalPriceResponse> fromList(
            List<CategoriesMonthlyTotalPrice> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(CategoriesMonthlyTotalPriceResponse::from).toList();
    }
}
