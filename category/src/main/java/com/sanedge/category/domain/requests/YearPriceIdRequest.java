package com.sanedge.category.domain.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class YearPriceIdRequest {
    @NotNull(message = "Category ID cannot be null")
    private Integer categoryId;

    @NotNull(message = "Year cannot be null")
    private Integer year;
}
