package com.dreamify.app.models

import com.dreamify.innertube.models.YTItem
import com.dreamify.app.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
