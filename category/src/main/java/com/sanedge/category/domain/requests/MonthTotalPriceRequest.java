package com.sanedge.category.domain.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MonthTotalPriceRequest {
    @NotNull(message = "Year cannot be null")
    private Integer year;

    @NotNull(message = "Month cannot be null")
    private Integer month;
}
