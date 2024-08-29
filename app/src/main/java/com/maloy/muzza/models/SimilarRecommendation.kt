package com.maloy.muzza.models

import com.maloy.innertube.models.YTItem
import com.maloy.muzza.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
