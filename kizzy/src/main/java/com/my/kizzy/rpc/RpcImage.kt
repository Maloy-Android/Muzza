package com.my.kizzy.rpc

sealed class RpcImage {
    class DiscordImage(val image: String) : RpcImage()

    class ExternalImage(val image: String) : RpcImage()
}
