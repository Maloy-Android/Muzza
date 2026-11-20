package com.maloy.muzza.util

import kotlin.random.Random

private val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** Replacement for Apache Commons Lang3 RandomStringUtils.random(n, true, false) */
fun randomAlphabetic(length: Int): String =
    (1..length).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
