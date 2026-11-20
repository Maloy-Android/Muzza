package com.maloy.kugou

// iOS: no opencc4j equivalent — return text as-is
actual fun toTraditionalChinese(text: String): String = text
