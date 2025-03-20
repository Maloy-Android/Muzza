package com.maloy.muzza.models

import com.maloy.innertube.models.Continuation
import com.maloy.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: List<Continuation>,
)
