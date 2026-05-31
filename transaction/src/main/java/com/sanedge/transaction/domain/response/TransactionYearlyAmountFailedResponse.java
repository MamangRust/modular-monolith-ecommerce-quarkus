package com.sanedge.transaction.domain.response;

import java.util.List;

import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyAmountFailedResponse {
    private String year;
    private Long totalFailed;
    private Long totalAmount;

    public static TransactionYearlyAmountFailedResponse from(TransactionYearlyAmountFailed response) {
        if (response == null) {
            return null;
        }
        return TransactionYearlyAmountFailedResponse.builder()
                .year(response.getYear())
                .totalFailed(response.getTotalFailed())
                .totalAmount(response.getTotalAmount())
                .build();
    }

    public static List<TransactionYearlyAmountFailedResponse> fromList(List<TransactionYearlyAmountFailed> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(TransactionYearlyAmountFailedResponse::from).toList();
    }
}
