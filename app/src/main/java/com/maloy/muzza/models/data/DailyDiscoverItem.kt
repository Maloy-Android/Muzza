package com.maloy.muzza.models.data

import com.maloy.innertube.models.BrowseEndpoint
import com.maloy.innertube.models.YTItem
import com.maloy.muzza.db.entities.Song

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)