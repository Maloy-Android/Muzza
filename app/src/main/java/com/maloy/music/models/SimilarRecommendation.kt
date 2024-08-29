package com.maloy.music.models

import com.maloy.innertube.models.YTItem
import com.maloy.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
