package com.sanedge.category.domain.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MonthTotalPriceMerchantRequest {
    @NotNull(message = "Merchant ID cannot be null")
    private Long merchantId;

    @NotNull(message = "Year cannot be null")
    private Integer year;

    @NotNull(message = "Month cannot be null")
    private Integer month;
}
