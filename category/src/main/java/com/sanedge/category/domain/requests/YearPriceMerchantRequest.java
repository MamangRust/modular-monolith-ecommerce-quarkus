package com.sanedge.category.domain.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class YearPriceMerchantRequest {
    @NotNull(message = "Merchant ID cannot be null")
    private Integer merchantId;

    @NotNull(message = "Year cannot be null")
    private Integer year;
}
