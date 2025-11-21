package com.metrolist.music.tasker.actions

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.tasker.CommonRunner
import com.metrolist.music.tasker.TaskerConfigurationItem
import com.metrolist.music.tasker.TaskerConfigurationScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

enum class AutoPlaylist(val title: String, val description: String? = null) {
    MostPlayed("[Auto] Most Played Songs", "Argument specify the number of days"),
    Downloaded("[Auto] Downloaded Songs"),
    Liked("[Auto] Liked Songs"),
    ArtistRadio(
        "[Auto] Artist Radio",
        "Start radio of the current artist or the artist with the id specified in the Argument input"
    ),
    ArtistSongs("[Auto] Artist Songs"),
    AlbumSongs("[Auto] Album Songs");

    fun toPair() = Pair(title, description)
}

enum class SortType {
    Natural,
    Shuffled
}


@TaskerInputRoot
class PlaylistInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "playlistName",
        labelResIdName = "tasker_input_playlist_name",
        descriptionResIdName = "tasker_input_playlist_name_description"
    ) var playlistName: String? = null,

    @field:TaskerInputField(
        "arg",
        labelResIdName = "tasker_input_arg",
        descriptionResIdName = "tasker_input_arg_description"
    ) var arg: String? = null,

    @field:TaskerInputField(
        "sorting",
        labelResIdName = "tasker_input_sorting",
        descriptionResIdName = "tasker_input_sorting_description"
    ) var sorting: String? = null,

    @field:TaskerInputField(
        "limit",
        labelResIdName = "tasker_input_limit",
        descriptionResIdName = "tasker_input_limit_description"
    ) var limit: String? = null
)

@TaskerOutputObject
class PlaylistOutput(
)


class PlaylistActionHelper(config: TaskerPluginConfig<PlaylistInput>) :
    TaskerPluginConfigHelper<PlaylistInput, PlaylistOutput, PlaylistActionRunner>(config) {
    override val inputClass = PlaylistInput::class.java
    override val outputClass = PlaylistOutput::class.java
    override val runnerClass = PlaylistActionRunner::class.java
}


@AndroidEntryPoint
class PlaylistConfigActivity : ComponentActivity(), TaskerPluginConfig<PlaylistInput> {

    @Inject
    lateinit var database: MusicDatabase

    override val context get() = applicationContext

    val playlistName = mutableStateOf("")
    val arg = mutableStateOf("")
    val sorting = mutableStateOf("")
    val limit = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<PlaylistInput>) {
        playlistName.value = input.regular.playlistName ?: AutoPlaylist.Liked.title
        arg.value = input.regular.arg ?: ""
        sorting.value = input.regular.sorting ?: SortType.Natural.toString()
        limit.value = input.regular.limit ?: ""
    }

    override val inputForTasker: TaskerInput<PlaylistInput>
        get() = TaskerInput(
            PlaylistInput(
                playlistName = playlistName.value,
                arg = arg.value,
                sorting = sorting.value,
                limit = limit.value
            )
        )

    private val taskerHelper by lazy { PlaylistActionHelper(this) }

    private fun getPlaylistNames(): List<Pair<String, String?>> {
        var playlists: List<Playlist> = emptyList()
        runBlocking {
            playlists = database.playlists(PlaylistSortType.NAME, true).first()
        }
        return playlists.map { Pair(it.title, null) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContent {
            TaskerConfigurationScreen(
                title = "Configure Playback Command action"
            ) {
                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_playlist_name),
                    inputDescription = stringResource(R.string.tasker_input_playlist_name_description),
                    playlistName,
                    inputOptions = AutoPlaylist.entries.map { it.toPair() }.plus(Pair("---", null))
                        .plus(getPlaylistNames()),
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_arg),
                    inputDescription = stringResource(R.string.tasker_input_arg_description),
                    arg,
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_sorting),
                    inputDescription = stringResource(R.string.tasker_input_sorting_description),
                    sorting,
                    inputOptions = SortType.entries.map { Pair(it.toString(), null) },
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_limit),
                    inputDescription = stringResource(R.string.tasker_input_limit_description),
                    limit,
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )
            }
        }

        taskerHelper.onCreate()
        //taskerHelper.finishForTasker()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val result = taskerHelper.onBackPressed()
                if (result is SimpleResultError) {
                    Timber.tag("Tasker").w("Settings are not valid:\n\n${result.message}")
                }
                if (result.success) finish()
            }
        })
    }
}


class PlaylistActionRunner : CommonRunner<PlaylistInput, PlaylistOutput>() {

    override suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<PlaylistInput>,
        musicService: MusicService
    ): TaskerPluginResult<PlaylistOutput> {

        val playlistName = input.regular.playlistName ?: ""
        val sorting = input.regular.sorting ?: ""
        val limit = input.regular.limit?.toIntOrNull() ?: 0
        val arg = input.regular.arg

        var queue: Queue?

        when (playlistName) {

            AutoPlaylist.ArtistRadio.title -> {
                val artistId =
                    arg ?: musicService.database.song(musicService.currentMediaMetadata.value?.id)
                        .firstOrNull()?.artists?.firstOrNull()?.id
                if (artistId != null) {
                    val artistPage = YouTube.artist(artistId)
                    queue = YouTubeQueue(artistPage.getOrNull()?.artist?.radioEndpoint!!)
                } else {
                    return TaskerPluginResultErrorWithOutput(1, "Cannot find artist by id")
                }
            }

            AutoPlaylist.ArtistSongs.title -> {
                val artistId =
                    arg ?: musicService.database.song(musicService.currentMediaMetadata.value?.id)
                        .firstOrNull()?.artists?.firstOrNull()?.id
                if (artistId != null) {
                    val artistPage = YouTube.artist(artistId)
                    queue = YouTubeQueue(artistPage.getOrNull()?.artist?.shuffleEndpoint!!)
                } else {
                    return TaskerPluginResultErrorWithOutput(1, "Cannot find artist by id")
                }
            }

            AutoPlaylist.AlbumSongs.title -> {

                val albumId =
                    arg ?: musicService.database.song(musicService.currentMediaMetadata.value?.id)
                        .firstOrNull()?.song?.albumId

                Timber.tag("Tasker").d("Album id: $albumId")

                if (albumId != null) {
                    val albumWithSongs = musicService.database.albumWithSongs(albumId).firstOrNull()
                    if (albumWithSongs != null) {
                        queue = LocalAlbumRadio(albumWithSongs)
                    } else {
                        Timber.tag("Tasker")
                            .e("Cannot find album songs in the db. Getting them from YTM")
                        val items =
                            YouTube.album(albumId).getOrNull()?.songs?.map { s -> s.toMediaItem() }
                        if (items != null)
                            queue = ListQueue("Album Queue", items)
                        else
                            return TaskerPluginResultErrorWithOutput(
                                1,
                                "Error getting album songs from YTM"
                            )
                    }
                } else {
                    return TaskerPluginResultErrorWithOutput(1, "Cannot find album by id")
                }
            }

            AutoPlaylist.MostPlayed.title -> {

                val fromTimestamp = if(arg != null) {
                    LocalDateTime
                        .now()
                        .minusDays(1)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                } else { 0 }



                val mostPlayed = musicService.database.mostPlayedSongs(fromTimestamp).firstOrNull()
                    ?: emptyList()
                Timber.tag("Tasker").d("Most played songs from ($fromTimestamp) query returned ${mostPlayed.size} songs")
                queue = ListQueue("Most Played", mostPlayed.map { s -> s.toMediaItem() })
            }

            AutoPlaylist.Downloaded.title -> {
                val cachedIds = musicService.playerCache.keys.mapNotNull { it?.toString() }.toSet()

                val songs = if (cachedIds.isNotEmpty()) {
                    musicService.database.getSongsByIds(cachedIds.toList())
                } else {
                    emptyList()
                }

                val completeSongs = songs.filter {
                    val contentLength = it.format?.contentLength
                    contentLength != null && musicService.playerCache.isCached(
                        it.song.id,
                        0,
                        contentLength
                    )
                }

                val downloadedSongs =
                    musicService.database.downloadedSongs(SongSortType.NAME, true).firstOrNull()
                        ?: emptyList()

                val all = completeSongs.plus(downloadedSongs)

                queue = ListQueue("Tasker Cached and Downloaded", all.map { s -> s.toMediaItem() })
            }

            AutoPlaylist.Liked.title -> {
                val liked = musicService.database.likedSongsByNameAsc().firstOrNull() ?: emptyList()

                queue = ListQueue("Tasker Liked", liked.map { s -> s.toMediaItem() })
            }

            else -> {
                val playlist = musicService.database.playlistsByNameAsc().firstOrNull()
                    ?.firstOrNull { p -> p.playlist.name == playlistName }
                if (playlist != null) {
                    val songs = musicService.database.playlistSongs(playlist.id).firstOrNull()
                        ?.map { s -> s.song.toMediaItem() }
                    queue = ListQueue(playlistName, songs ?: emptyList())
                } else {
                    return TaskerPluginResultErrorWithOutput(
                        1,
                        "Cannot find playlist with name $playlistName"
                    )
                }
            }


        }

        if (limit > 0 && queue.getInitialStatus().items.size > limit) {
            queue = ListQueue(
                queue.getInitialStatus().title,
                queue.getInitialStatus().items.take(limit)
            )
        }

        if (SortType.valueOf(sorting) == SortType.Shuffled) {
            queue =
                ListQueue(queue.getInitialStatus().title, queue.getInitialStatus().items.shuffled())
        }

        withContext(Dispatchers.Main) {
            musicService.playQueue(queue)
        }

        return TaskerPluginResultSucess()
    }

}
