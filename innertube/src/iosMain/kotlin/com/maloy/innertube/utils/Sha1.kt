package com.maloy.innertube.utils

import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.CommonCrypto.CC_SHA1
import platform.CommonCrypto.CC_SHA1_DIGEST_LENGTH
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get

@OptIn(ExperimentalForeignApi::class)
actual fun sha1(str: String): String {
    val data = (str as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return ""
    val bytes = ByteArray(CC_SHA1_DIGEST_LENGTH)
    memScoped {
        val digest = allocArray<UByteVar>(CC_SHA1_DIGEST_LENGTH)
        CC_SHA1(data.bytes, data.length.toUInt(), digest)
        for (i in 0 until CC_SHA1_DIGEST_LENGTH) {
            bytes[i] = digest[i].toByte()
        }
    }
    return bytes.toHex()
}
