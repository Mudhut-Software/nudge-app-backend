package com.mudhut.nudge.businesses.publicapi.controllers

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
        @RequestParam category: Long,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<PublicBusinessSummary> = publicBrowseService.byCategory(category, pageable)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): PublicBusinessDetail = publicBrowseService.detail(id)
}
