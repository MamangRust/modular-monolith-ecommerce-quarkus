package com.sanedge.order.domain.response;

import java.util.List;

import com.sanedge.order.entity.OrderYearlyTotalRevenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderYearlyTotalRevenueResponse {
    private String year;
    private Long totalRevenue;

    public static OrderYearlyTotalRevenueResponse from(OrderYearlyTotalRevenue response) {
        if (response == null) {
            return null;
        }
        return OrderYearlyTotalRevenueResponse.builder()
                .year(response.getYear())
                .totalRevenue(response.getTotalRevenue())
                .build();
    }

    public static List<OrderYearlyTotalRevenueResponse> fromList(List<OrderYearlyTotalRevenue> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(OrderYearlyTotalRevenueResponse::from).toList();
    }
}
