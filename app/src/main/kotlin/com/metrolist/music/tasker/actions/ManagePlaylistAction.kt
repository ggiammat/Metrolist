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
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.tasker.CommonRunner
import com.metrolist.music.tasker.TaskerConfigurationItem
import com.metrolist.music.tasker.TaskerConfigurationScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

enum class Command {
    ADD_SONG_TO_PLAYLIST,
    REMOVE_SONG_TO_PLAYLIST
}

@TaskerInputRoot
class ManagePlaylistInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "playlistName",
        labelResIdName = "tasker_input_playlist_name",
        descriptionResIdName = "tasker_input_playlist_name_description"
    ) var playlistName: String? = null,

    @field:TaskerInputField(
        "song_id",
        labelResIdName = "song_id_name",
        descriptionResIdName = "song_id_description"
    ) var songId: String? = null,

    @field:TaskerInputField(
        "command",
        labelResIdName = "command_name",
        descriptionResIdName = "command_description"
    ) var command: String? = null
)

@TaskerOutputObject
class ManagePlaylistOutput (
    @get:TaskerOutputVariable("executed",
        labelResIdName = "out_executed_name",
        htmlLabelResIdName = "out_executed_description"
    ) var executed: Boolean?,
)

class ManagePlaylistActionHelper(config: TaskerPluginConfig<ManagePlaylistInput>) :
    TaskerPluginConfigHelper<ManagePlaylistInput, ManagePlaylistOutput, ManagePlaylistActionRunner>(config) {
    override val inputClass = ManagePlaylistInput::class.java
    override val outputClass = ManagePlaylistOutput::class.java
    override val runnerClass = ManagePlaylistActionRunner::class.java
}


@AndroidEntryPoint
class ManagePlaylistConfigActivity : ComponentActivity(), TaskerPluginConfig<ManagePlaylistInput> {

    @Inject
    lateinit var database: MusicDatabase

    override val context get() = applicationContext

    val playlistName = mutableStateOf("")
    val songId = mutableStateOf("")
    val command = mutableStateOf("")


    override fun assignFromInput(input: TaskerInput<ManagePlaylistInput>) {
        playlistName.value = input.regular.playlistName ?: ""
        songId.value = input.regular.songId ?: ""
        command.value = input.regular.command ?: ""
    }

    override val inputForTasker: TaskerInput<ManagePlaylistInput>
        get() = TaskerInput(
            ManagePlaylistInput(
                playlistName = playlistName.value,
                songId = songId.value,
                command = command.value
            )
        )

    private val taskerHelper by lazy { ManagePlaylistActionHelper(this) }

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
                    inputLabel = stringResource(R.string.command_name),
                    inputDescription = stringResource(R.string.command_description),
                    command,
                    inputOptions = Command.entries.map { c -> Pair(c.toString(), null) },
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.song_id_name),
                    inputDescription = stringResource(R.string.song_id_description),
                    songId,
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_playlist_name),
                    inputDescription = stringResource(R.string.tasker_input_playlist_name_description),
                    playlistName,
                    inputOptions = getPlaylistNames(),
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



class ManagePlaylistActionRunner : CommonRunner<ManagePlaylistInput, ManagePlaylistOutput>() {

    override suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<ManagePlaylistInput>,
        musicService: MusicService
    ): TaskerPluginResult<ManagePlaylistOutput> {

        val songId = input.regular.songId.takeIf { it != "" } ?: musicService.currentMediaMetadata.value?.id
        val song = musicService.database.song(songId).firstOrNull()
            ?: return TaskerPluginResultErrorWithOutput(
                1,
                "Cannot find song with id $songId"
            )

        val playlistName = input.regular.playlistName
        val playlist = musicService.database.playlistsByNameAsc().firstOrNull()
            ?.firstOrNull { p -> p.playlist.name == playlistName }
            ?: return TaskerPluginResultErrorWithOutput(
                1,
                "Cannot find playlist with name $playlistName"
            )

        when(input.regular.command) {

            Command.ADD_SONG_TO_PLAYLIST.toString() -> {
                val duplicate = musicService.database.playlistDuplicates(playlist.id, listOf(song.id)).firstOrNull()
                if (duplicate != null) {
                    return TaskerPluginResultSucess(
                        ManagePlaylistOutput(
                            executed = false
                        )
                    )
                }
                musicService.database.addSongToPlaylist(playlist, listOf(song.id))
                playlist.playlist.browseId?.let { plist ->
                    YouTube.addToPlaylist(plist, song.id)
                }
            }
            Command.REMOVE_SONG_TO_PLAYLIST.toString() -> {
               val playlistSong = musicService.database.playlistSongs(playlist.id).firstOrNull()
                   ?.first { it.song.id == song.id } ?: return TaskerPluginResultSucess(
                    ManagePlaylistOutput(
                        executed = false
                    )
                )

                playlist.playlist.browseId?.let { playlistId ->
                    if (playlistSong.map.setVideoId != null) {
                        YouTube.removeFromPlaylist(
                            playlistId, playlistSong.map.songId, playlistSong.map.setVideoId
                        )
                    }
                }

                musicService.database.transaction {
                    move(playlistSong.map.playlistId, playlistSong.map.position, Int.MAX_VALUE)
                    delete(playlistSong.map.copy(position = Int.MAX_VALUE))
                }
            }
        }


        return TaskerPluginResultSucess(
            ManagePlaylistOutput(executed = true)
        )

    }
}
