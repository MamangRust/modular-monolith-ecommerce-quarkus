package com.sanedge.transaction.domain.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
@Schema(description = "Request untuk mengambil jumlah transaksi tahunan berdasarkan merchant")
public class YearAmountTransactionMerchant {

    @NotNull
    @QueryParam("merchantId")
    @Schema(description = "ID merchant", example = "123", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer merchantId;

    @NotNull
    @QueryParam("year")
    @Schema(description = "Tahun transaksi", example = "2025", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer year;
}
