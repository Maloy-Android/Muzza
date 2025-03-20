package com.maloy.innertube.pages

import com.maloy.innertube.models.Continuation
import com.maloy.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: List<Continuation>,
)