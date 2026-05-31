package com.sanedge.transaction.domain.response;

import java.util.List;

import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonthlyAmountSuccessResponse {
    private String year;
    private String month;
    private Long totalSuccess;
    private Long totalAmount;

    public static TransactionMonthlyAmountSuccessResponse from(TransactionMonthlyAmountSuccess response) {
        if (response == null) {
            return null;
        }
        return TransactionMonthlyAmountSuccessResponse.builder()
                .year(response.getYear())
                .month(response.getMonth())
                .totalSuccess(response.getTotalSuccess())
                .totalAmount(response.getTotalAmount())
                .build();
    }

    public static List<TransactionMonthlyAmountSuccessResponse> fromList(
            List<TransactionMonthlyAmountSuccess> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(TransactionMonthlyAmountSuccessResponse::from).toList();
    }
}
