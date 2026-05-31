package com.sanedge.merchant_detail.repository;

import java.util.Optional;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.merchant_detail.entity.MerchantDetailsRelation;
import io.smallrye.mutiny.Uni;

public interface MerchantDetailQueryRepositoryCustom {
    Uni<PagedResult<MerchantDetailsRelation>> findAllWithSocialLinks(String keyword, int page, int size);

    Uni<PagedResult<MerchantDetailsRelation>> findActiveWithSocialLinks(String keyword, int page, int size);

    Uni<PagedResult<MerchantDetailsRelation>> findTrashedWithSocialLinks(String keyword, int page, int size);

    Uni<Optional<MerchantDetailsRelation>> findByIdWithSocialLinks(Long merchantDetailId);
}