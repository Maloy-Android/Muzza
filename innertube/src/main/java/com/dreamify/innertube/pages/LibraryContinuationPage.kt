package com.dreamify.innertube.pages

import com.dreamify.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)