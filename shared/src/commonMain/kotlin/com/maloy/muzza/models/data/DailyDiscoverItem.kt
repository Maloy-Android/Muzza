package com.maloy.muzza.models.data

import com.maloy.innertube.models.BrowseEndpoint
import com.maloy.innertube.models.YTItem
import com.maloy.muzza.domain.model.SongEntity

data class DailyDiscoverItem(
    val seed: SongEntity,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)