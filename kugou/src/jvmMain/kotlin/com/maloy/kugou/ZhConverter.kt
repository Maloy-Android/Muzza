package com.maloy.kugou

import com.github.houbb.opencc4j.util.ZhConverterUtil

actual fun toTraditionalChinese(text: String): String =
    ZhConverterUtil.toTraditional(text)
