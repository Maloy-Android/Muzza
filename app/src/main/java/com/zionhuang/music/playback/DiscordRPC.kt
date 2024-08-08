package com.zionhuang.music.playback

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.common.Player
import com.my.kizzyrpc.KizzyRPC
import com.my.kizzyrpc.model.Activity
import com.my.kizzyrpc.model.Assets
import com.my.kizzyrpc.model.Metadata
import com.my.kizzyrpc.model.Timestamps
import com.zionhuang.music.constants.DiscordTokenKey
import com.zionhuang.music.constants.DownloadInnerTuneButtonKey
import com.zionhuang.music.constants.EnableDiscordRPCKey
import com.zionhuang.music.constants.ListenAlongButtonKey
import com.zionhuang.music.constants.ShowAppNameRPCKey
import com.zionhuang.music.constants.ShowArtistRPCKey
import com.zionhuang.music.constants.ShowTimestampsRPCKey
import com.zionhuang.music.utils.dataStore
import com.zionhuang.music.utils.get
import com.zionhuang.music.utils.reportException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

val rpc = KizzyRPC("")
var previousArtwork = ""
var previousAvatar = ""
var previousUploader = ""

@SuppressLint("SetJavaScriptEnabled", "CoroutineCreationDuringComposition")
fun setRPC(ctx: Context, title: String, artist: String, album: String, artwork: String,
           artistArtwork: String, uploader: String, songDuration: Long?, elapsedDuration: Long?, mediaID: String?) {
    val showAppName = ctx.dataStore.get(ShowAppNameRPCKey, true)
    val showTimestamps = ctx.dataStore.get(ShowTimestampsRPCKey, true)
    val listenAlong = ctx.dataStore.get(ListenAlongButtonKey, true)
    val downloadIT = ctx.dataStore.get(DownloadInnerTuneButtonKey, true)

    val timestamp = if (showTimestamps && songDuration != null) Timestamps(
        start = System.currentTimeMillis(),
        end = System.currentTimeMillis() + ((songDuration as Long) - (elapsedDuration as Long))
    )
    else null
    val albumText = if (album != "Single" && album != "null") "On $album" else "Single"
    val buttons: MutableList<String> = mutableListOf()
    val btnMetadata: MutableList<String> = mutableListOf()
    if (listenAlong) {
        buttons.add("♪ Listen Along ♪")
        btnMetadata.add("https://music.youtube.com/watch?v=" + mediaID)
    }
    if (downloadIT) {
        buttons.add("Download InnerTune")
        btnMetadata.add("https://github.com/z-huang/InnerTune/releases")
    }

    rpc.setActivity(
        activity = Activity(
            applicationId = "1244393423738376326",
            name = if (showAppName) "InnerTune" else title,
            details = if (showAppName) title else if (showTimestamps) "By $artist" else title,
            state = if (showAppName) "By $artist" else if (showTimestamps) albumText else "By $artist",
            type = if (showTimestamps) 0 else 2,
            timestamps = timestamp,
            assets = Assets(
                largeImage = artwork,
                smallImage = if (artistArtwork == "") null else artistArtwork,
                smallText = if (uploader == "") null else uploader,
                largeText = albumText
            ),
            buttons = buttons,
            metadata = Metadata(btnMetadata)
        ),
        since = System.currentTimeMillis()
    )
}

@SuppressLint("SetJavaScriptEnabled", "CoroutineCreationDuringComposition")
fun closeDiscordRPC(ctx: Context) {
    val discordToken = ctx.dataStore.get(DiscordTokenKey, "")
    rpc.token = discordToken
    rpc.closeRPC()
}

@SuppressLint("SetJavaScriptEnabled", "CoroutineCreationDuringComposition")
fun createDiscordRPC(player: Player, ctx: Context) {
    val discordToken = ctx.dataStore.get(DiscordTokenKey, "")
    rpc.token = discordToken

    rpc.closeRPC()
    while (rpc.isRpcRunning()) {
        rpc.closeRPC()
    }

    val enableRPC = ctx.dataStore.get(EnableDiscordRPCKey, true)
    val showArtistAvatar = ctx.dataStore.get(ShowArtistRPCKey, true)
    val showAppName = ctx.dataStore.get(ShowAppNameRPCKey, true)
    val showTimestamps = ctx.dataStore.get(ShowTimestampsRPCKey, true)


    if (discordToken != "" || !enableRPC) {
        val client = HttpClient()
        val clientDiscordCDN = HttpClient()

        val mediaID = player.currentMediaItem?.mediaId
        val title = player.currentMediaItem?.mediaMetadata?.title.toString()
        val album = if (title == player.currentMediaItem?.mediaMetadata?.albumTitle.toString()) "Single" else player.currentMediaItem?.mediaMetadata?.albumTitle.toString()
        val artist = player.currentMediaItem?.mediaMetadata?.artist.toString()
        val artwork = player.currentMediaItem?.mediaMetadata?.artworkUri.toString()

        if (title == "null") {
            rpc.closeRPC()
            return
        }
        var songDuration = player.contentDuration
        var elapsedDuration = player.contentPosition

        if (showArtistAvatar) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response: HttpResponse = client.get("https://pipedapi.r4fo.com/streams/" + mediaID)
                    val responseBody: String = response.bodyAsText()

                    var artistArtwork = ""
                    var artistArtworkCDN = ""
                    var artworkCDN = ""
                    var uploader = ""

                    val json = JSONObject(responseBody)
                    artistArtwork = json.getString("uploaderAvatar")
                    uploader = json.getString("uploader").split(" - Topic")[0]

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val response: HttpResponse = clientDiscordCDN.get("https://kizzyapi-1-z9614716.deta.app/image?url=" + artistArtwork)
                            artistArtworkCDN = JSONObject(response.bodyAsText()).getString("id")
                        } catch (e: Exception) {
                            reportException(e)
                        } finally {
                            clientDiscordCDN.close()
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val response: HttpResponse = clientDiscordCDN.get("https://kizzyapi-1-z9614716.deta.app/image?url=" + artwork)
                            artworkCDN = JSONObject(response.bodyAsText()).getString("id")
                            Thread.sleep(500)
                            if (artistArtworkCDN == "") {
                                // Already uploaded to Discord CDN placeholder image
                                artistArtworkCDN = "mp:external/_jGArMHI-5rpJu4qVDuiBARu8iEXnHeT0SZS6tZnZug/https/i.imgur.com/zDxXZKk.png"
                            }
                            previousArtwork = artworkCDN
                            previousAvatar = artistArtworkCDN
                            previousUploader = uploader
                            setRPC(ctx, title, artist, album, artworkCDN, artistArtworkCDN, uploader, songDuration, elapsedDuration, mediaID)
                        } catch (e: Exception) {
                            reportException(e)
                        } finally {
                            clientDiscordCDN.close()
                        }
                    }

                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    client.close()
                }
            }
        }

        else{
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response: HttpResponse = clientDiscordCDN.get("https://kizzyapi-1-z9614716.deta.app/image?url=" + artwork)
                    val responseBody: String = response.bodyAsText()

                    val artworkCDN = JSONObject(responseBody).getString("id")
                    previousArtwork = artworkCDN
                    previousAvatar = ""
                    previousUploader = ""
                    setRPC(ctx, title, artist, album, artworkCDN, "", "", songDuration, elapsedDuration, mediaID)
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    clientDiscordCDN.close()
                }
            }
        }
    }
}