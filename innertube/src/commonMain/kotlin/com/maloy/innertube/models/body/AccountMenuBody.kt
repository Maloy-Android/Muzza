package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class AccountMenuBody(
    val context: com.metrolist.innertubex.models.Context,
    val deviceTheme: String = "DEVICE_THEME_SELECTED",
    val userInterfaceTheme: String = "USER_INTERFACE_THEME_DARK",
)
