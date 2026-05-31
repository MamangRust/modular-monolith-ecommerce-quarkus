package com.sanedge.category.service.stats;

import java.util.List;

import com.sanedge.category.domain.requests.MonthPriceMerchantRequest;
import com.sanedge.category.domain.requests.YearPriceMerchantRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryPriceByMerchantService {
    Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceByMerchant(MonthPriceMerchantRequest req);
    Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceByMerchant(YearPriceMerchantRequest req);
}
