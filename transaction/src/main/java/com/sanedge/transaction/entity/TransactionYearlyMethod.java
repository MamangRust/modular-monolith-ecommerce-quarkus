package com.sanedge.transaction.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyMethod {
    private String year;
    private String paymentMethod;
    private Long totalTransactions;
    private Long totalAmount;
}
