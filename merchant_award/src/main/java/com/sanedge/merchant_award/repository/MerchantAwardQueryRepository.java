package com.sanedge.merchant_award.repository;

import com.sanedge.common.repository.PagedQuery;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.merchant_award.entity.MerchantCertificationAndAward;
import com.sanedge.merchant_award.domain.requests.FindAllMerchantRequest;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantAwardQueryRepository implements PanacheRepository<MerchantCertificationAndAward> {

    public Uni<PagedResult<MerchantCertificationAndAward>> findMerchantAwards(FindAllMerchantRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        var query = """
                    deletedAt IS NULL
                    AND (
LOWER(title) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(issuedBy) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(certificateUrl) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return PagedQuery.fetch(panacheQuery);
    }

    public Uni<PagedResult<MerchantCertificationAndAward>> findActiveMerchantAwards(FindAllMerchantRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        var query = """
                    deletedAt IS NULL
                    AND (
LOWER(title) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(issuedBy) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(certificateUrl) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return PagedQuery.fetch(panacheQuery);
    }

    public Uni<PagedResult<MerchantCertificationAndAward>> findTrashedMerchantAwards(FindAllMerchantRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

        var query = """
                    deletedAt IS NOT NULL
                    AND (
LOWER(title) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(issuedBy) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(certificateUrl) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return PagedQuery.fetch(panacheQuery);
    }

    public Uni<MerchantCertificationAndAward> findMerchantAwardById(Long merchantCertificationId) {
        return find("id = ?1 AND deletedAt IS NULL", merchantCertificationId).firstResult();
    }
}
