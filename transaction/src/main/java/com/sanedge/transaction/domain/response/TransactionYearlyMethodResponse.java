package com.sanedge.transaction.domain.response;

import java.util.List;

import com.sanedge.transaction.entity.TransactionYearlyMethod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyMethodResponse {
    private String year;
    private String paymentMethod;
    private Long totalTransactions;
    private Long totalAmount;

    public static TransactionYearlyMethodResponse from(TransactionYearlyMethod response) {
        if (response == null) {
            return null;
        }
        return TransactionYearlyMethodResponse.builder()
                .year(response.getYear())
                .paymentMethod(response.getPaymentMethod())
                .totalTransactions(response.getTotalTransactions())
                .totalAmount(response.getTotalAmount())
                .build();
    }

    public static List<TransactionYearlyMethodResponse> fromList(List<TransactionYearlyMethod> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(TransactionYearlyMethodResponse::from).toList();
    }
}
