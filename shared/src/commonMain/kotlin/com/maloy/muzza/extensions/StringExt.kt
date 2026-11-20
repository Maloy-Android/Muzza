package com.maloy.muzza.extensions

// Pure KMP — no java.net, no androidx.sqlite

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) defaultValue
    else try {
        enumValueOf(this)
    } catch (e: IllegalArgumentException) {
        defaultValue
    }

/** Parse "host:port" into a Pair — used by proxy settings. */
fun String.toHostPort(): Pair<String, Int> {
    val parts = split(":")
    return parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 8080)
}
