package com.maloy.innertube

import io.ktor.client.HttpClient

/**
 * Platform-specific HttpClient creation with optional proxy support.
 * On iOS, proxy is not supported and parameters are ignored.
 */
expect fun createInnerTubeHttpClient(
    proxyHost: String? = null,
    proxyPort: Int = 8080,
    proxyAuth: String? = null,
): HttpClient
