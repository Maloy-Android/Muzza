package com.maloy.kugou

/**
 * Platform-specific Traditional/Simplified Chinese conversion.
 * JVM: uses opencc4j. iOS: passthrough (no equivalent library available).
 */
expect fun toTraditionalChinese(text: String): String
