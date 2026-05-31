package com.sanedge.banner.domain.requests;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindAllBannerRequest {

    @QueryParam("search")
    @DefaultValue("")
    private String search = "";

    @QueryParam("page")
    @DefaultValue("1")
    private Integer page = 1;

    @QueryParam("pageSize")
    @DefaultValue("10")
    private Integer pageSize = 10;
}
