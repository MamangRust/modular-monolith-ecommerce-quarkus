package com.sanedge.category.service.stats;

import java.util.List;

import com.sanedge.category.domain.requests.FindMonthlyCategoryTotalPriceByIdRequest;
import com.sanedge.category.domain.requests.YearTotalPriceIdRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryTotalPriceByIdService {
    Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceById(FindMonthlyCategoryTotalPriceByIdRequest req);
    Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceById(YearTotalPriceIdRequest req);
}
