package com.sanedge.transaction.service.statsbymerchant;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.transaction.domain.requests.FindMonthlyMethodsByMerchantRequest;
import com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionMethodByMerchantService {
    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantSuccess(FindMonthlyMethodsByMerchantRequest req);
    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantFailed(FindMonthlyMethodsByMerchantRequest req);
    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantSuccess(YearMethodTransactionMerchantRequest req);
    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantFailed(YearMethodTransactionMerchantRequest req);
}
