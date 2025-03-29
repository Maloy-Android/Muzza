package com.maloy.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val apikey: String,
    val userAgent: String,
    val osVersion: String? = null,
    val referer: String? = null,
    val supportsLogin: Boolean = false,
) {
    fun toContext(locale: YouTubeLocale, visitorData: String?) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            osVersion = osVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData
        )
    )

    companion object {
        private const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"

        private const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.107 Safari/537.36"
        private const val USER_AGENT_ANDROID = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Mobile Safari/537.36"

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "5.01",
            apikey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            userAgent = USER_AGENT_ANDROID,
        )

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20250312.04.00",
            apikey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
            userAgent = USER_AGENT_WEB,
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20250310.01.00",
            apikey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = USER_AGENT_WEB,
            referer = REFERER_YOUTUBE_MUSIC,
            supportsLogin = true,
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            apikey = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            userAgent = "Mozilla/5.0 (PlayStation 4 5.55) AppleWebKit/601.2 (KHTML, like Gecko)"
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "20.10.4",
            apikey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            osVersion = "18.3.2.22D82",
        )
    }
}
