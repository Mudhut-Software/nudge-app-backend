package com.mudhut.nudge.businesses.publicapi.controllers

import com.mudhut.nudge.businesses.publicapi.models.BusinessSort
import com.mudhut.nudge.businesses.publicapi.models.ExploreLane
import com.mudhut.nudge.businesses.publicapi.models.PublicBusinessDetail
import com.mudhut.nudge.businesses.publicapi.models.PublicBusinessSummary
import com.mudhut.nudge.businesses.publicapi.services.PublicBrowseService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/businesses/public")
class PublicBusinessController(
    private val publicBrowseService: PublicBrowseService,
) {

    @GetMapping("/explore/lanes")
    fun lanes(): List<ExploreLane> = publicBrowseService.lanes()

    @GetMapping
    fun list(
        @RequestParam(required = false) category: Long?,
        @RequestParam(required = false) sort: BusinessSort?,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<PublicBusinessSummary> {
        val effectiveSort = sort ?: BusinessSort.POPULAR
        if (effectiveSort == BusinessSort.NEAREST) {
            require(lat != null && lng != null) { "sort=nearest requires lat and lng" }
        }
        return publicBrowseService.list(category, effectiveSort, lat, lng, pageable)
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): PublicBusinessDetail = publicBrowseService.detail(id)
}
