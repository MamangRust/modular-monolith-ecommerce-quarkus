package com.sanedge.category.service.stats;

import java.util.List;

import com.sanedge.category.domain.requests.FindMonthlyCategoryTotalPriceByMerchantRequest;
import com.sanedge.category.domain.requests.YearTotalPriceMerchantRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryTotalPriceByMerchantService {
    Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceByMerchant(FindMonthlyCategoryTotalPriceByMerchantRequest req);
    Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceByMerchant(YearTotalPriceMerchantRequest req);
}
