package com.maloy.innertube.models.response

import com.maloy.innertube.models.Button
import com.maloy.innertube.models.Continuation
import com.maloy.innertube.models.GridRenderer
import com.maloy.innertube.models.Menu
import com.maloy.innertube.models.MusicDescriptionShelfRenderer
import com.maloy.innertube.models.MusicShelfRenderer
import com.maloy.innertube.models.ResponseContext
import com.maloy.innertube.models.Runs
import com.maloy.innertube.models.SectionListRenderer
import com.maloy.innertube.models.SubscriptionButton
import com.maloy.innertube.models.Tabs
import com.maloy.innertube.models.ThumbnailRenderer
import com.maloy.innertube.models.Thumbnails
import kotlinx.serialization.Serializable

@Serializable
data class BrowseResponse(
    val contents: Contents?,
    val continuationContents: ContinuationContents?,
    val onResponseReceivedActions: List<ResponseAction>?,
    val header: Header?,
    val microformat: Microformat?,
    val responseContext: ResponseContext,
    val background: MusicThumbnailRenderer?,
) {
    @Serializable
    data class Contents(
        val singleColumnBrowseResultsRenderer: Tabs?,
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer?,
        val sectionListRenderer: SectionListRenderer?,
    )

    @Serializable
    data class TwoColumnBrowseResultsRenderer(
        val tabs: List<Tabs.Tab?>?,
        val secondaryContents: SecondaryContents?,
    )

    @Serializable
    data class SecondaryContents(
        val sectionListRenderer: SectionListRenderer?,
    )

    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails?,
        val thumbnailCrop: String?,
    )

    @Serializable
    data class ContinuationContents(
        val sectionListContinuation: SectionListContinuation?,
        val musicPlaylistShelfContinuation: MusicPlaylistShelfContinuation?,
        val gridContinuation: GridContinuation?,
        val musicShelfContinuation: MusicShelfRenderer?,
    ) {
        @Serializable
        data class SectionListContinuation(
            val contents: List<SectionListRenderer.Content>,
            val continuations: List<Continuation>?,
        )

        @Serializable
        data class MusicPlaylistShelfContinuation(
            val contents: List<MusicShelfRenderer.Content>,
            val continuations: List<Continuation>?,
        )

        @Serializable
        data class GridContinuation(
            val items: List<GridRenderer.Item>,
            val continuations: List<Continuation>?,
        )
    }


    @Serializable
    data class ResponseAction(
        val appendContinuationItemsAction: ContinuationItems?,
    ) {
        @Serializable
        data class ContinuationItems(
            val continuationItems: List<MusicShelfRenderer.Content>?,
        )
    }

    @Serializable
    data class Header(
        val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer?,
        val musicDetailHeaderRenderer: MusicDetailHeaderRenderer?,
        val musicEditablePlaylistDetailHeaderRenderer: MusicEditablePlaylistDetailHeaderRenderer?,
        val musicVisualHeaderRenderer: MusicVisualHeaderRenderer?,
        val musicHeaderRenderer: MusicHeaderRenderer?,
    ) {
        @Serializable
        data class MusicImmersiveHeaderRenderer(
            val title: Runs,
            val description: Runs?,
            val thumbnail: ThumbnailRenderer?,
            val playButton: Button?,
            val startRadioButton: Button?,
            val subscriptionButton: SubscriptionButton?,
            val menu: Menu,
            val subscriptionButton2: SubscriptionButton2?,
            val monthlyListenerCount: Runs? = null,
        ) {
            @Serializable
            data class SubscriptionButton2(
                val subscribeButtonRenderer: SubscribeButtonRenderer?,
            ) {
                @Serializable
                data class SubscribeButtonRenderer(
                    val subscriberCountWithSubscribeText: Runs?,
                )
            }
        }

        @Serializable
        data class MusicDetailHeaderRenderer(
            val title: Runs,
            val subtitle: Runs,
            val secondSubtitle: Runs,
            val description: Runs?,
            val thumbnail: ThumbnailRenderer,
            val menu: Menu,
        )

        @Serializable
        data class MusicEditablePlaylistDetailHeaderRenderer(
            val header: Header,
        ) {
            @Serializable
            data class Header(
                val musicDetailHeaderRenderer: MusicDetailHeaderRenderer?,
                val musicResponsiveHeaderRenderer: MusicHeaderRenderer?,
            )
        }

        @Serializable
        data class MusicVisualHeaderRenderer(
            val title: Runs,
            val foregroundThumbnail: ThumbnailRenderer,
            val thumbnail: ThumbnailRenderer?,
        )

        @Serializable
        data class Buttons(
            val menuRenderer: Menu.MenuRenderer?,
        )

        @Serializable
        data class MusicHeaderRenderer(
            val buttons: List<Buttons>?,
            val title: Runs?,
            val thumbnail: MusicThumbnailRenderer?,
            val subtitle: Runs?,
            val secondSubtitle: Runs?,
            val straplineTextOne: Runs?,
            val straplineThumbnail: MusicThumbnailRenderer?,
            val description: DescriptionWrapper? = null,
            val facepile: FacepileWrapper? = null,
        )

        @Serializable
        data class MusicThumbnail(
            val url: String?,
        )

        @Serializable
        data class MusicThumbnailRenderer(
            val musicThumbnailRenderer: BrowseResponse.MusicThumbnailRenderer,
            val thumbnails: List<MusicThumbnail>?,
        )
    }

    @Serializable
    data class Microformat(
        val microformatDataRenderer: MicroformatDataRenderer?,
    ) {
        @Serializable
        data class MicroformatDataRenderer(
            val urlCanonical: String?,
        )
    }
    @Serializable
    data class AvatarStackViewModel(
        val avatars: List<Avatar>?,
        val text: AvatarText?,
        val rendererContext: RendererContext?,
    ) {
        @Serializable
        data class Avatar(
            val avatarViewModel: AvatarViewModel?,
        )

        @Serializable
        data class AvatarViewModel(
            val image: AvatarImage?,
        )

        @Serializable
        data class AvatarImage(
            val sources: List<ImageSource>?,
        )

        @Serializable
        data class ImageSource(
            val url: String?,
        )

        @Serializable
        data class AvatarText(
            val content: String?,
        )

        @Serializable
        data class RendererContext(
            val commandContext: CommandContext?,
        )

        @Serializable
        data class CommandContext(
            val onTap: OnTap?,
        )

        @Serializable
        data class OnTap(
            val innertubeCommand: InnerTubeBrowseCommand?,
        )

        @Serializable
        data class InnerTubeBrowseCommand(
            val browseEndpoint: BrowseEndpoint?,
        )

        @Serializable
        data class BrowseEndpoint(
            val browseId: String?,
        )
    }

    @Serializable
    data class DescriptionWrapper(
        val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer?,
    )

    @Serializable
    data class FacepileWrapper(
        val avatarStackViewModel: AvatarStackViewModel?,
    )
}