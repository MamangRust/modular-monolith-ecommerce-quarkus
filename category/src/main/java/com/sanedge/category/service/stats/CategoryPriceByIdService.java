package com.sanedge.category.service.stats;

import java.util.List;

import com.sanedge.category.domain.requests.MonthPriceIdRequest;
import com.sanedge.category.domain.requests.YearPriceIdRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryPriceByIdService {
    Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceById(MonthPriceIdRequest req);
    Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceById(YearPriceIdRequest req);
}
