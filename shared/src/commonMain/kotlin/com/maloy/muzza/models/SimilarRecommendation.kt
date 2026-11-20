package com.maloy.muzza.models

import com.maloy.innertube.models.YTItem
import com.maloy.muzza.domain.model.SongEntity

data class SimilarRecommendation(
    val title: SongEntity,
    val items: List<YTItem>,
)
