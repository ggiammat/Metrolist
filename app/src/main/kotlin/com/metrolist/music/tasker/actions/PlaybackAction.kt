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
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.extensions.collectLatest
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.tasker.CommonRunner
import com.metrolist.music.tasker.TaskerConfigurationItem
import com.metrolist.music.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class Commands {
    TOGGLE_PLAY_PAUSE,
    PLAY,
    STOP,
    NEXT_SONG,
    PREVIOUS_SONG,
    LIKE_SONG,
    UNLIKE_SONG,
    TOGGLE_LIKE,
}

@TaskerInputRoot
class PlaybackCommandInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "command",
        labelResIdName = "command_name",
        descriptionResIdName = "command_description"
    ) var command: String? = null
)

@TaskerOutputObject
class PlaybackCommandOutput(
    @get:TaskerOutputVariable("executed",
        labelResIdName = "out_executed_name",
        htmlLabelResIdName = "out_executed_description"
    ) var executed: Boolean?,
)

class PlaybackCommandActionHelper(config: TaskerPluginConfig<PlaybackCommandInput>) :
    TaskerPluginConfigHelper<PlaybackCommandInput, PlaybackCommandOutput, PlaybackCommandActionRunner>(config) {
    override val inputClass = PlaybackCommandInput::class.java
    override val outputClass = PlaybackCommandOutput::class.java
    override val runnerClass = PlaybackCommandActionRunner::class.java
}

class PlaybackCommandConfigActivity : ComponentActivity(), TaskerPluginConfig<PlaybackCommandInput> {

    override val context get() = applicationContext

    val command = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<PlaybackCommandInput>) {
        command.value = input.regular.command ?: ""
    }

    override val inputForTasker: TaskerInput<PlaybackCommandInput>
        get() = TaskerInput(PlaybackCommandInput(command = command.value))

    private val taskerHelper by lazy { PlaybackCommandActionHelper(this) }

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
                    inputOptions = Commands.entries.map { c -> Pair(c.toString(), null) },
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


class PlaybackCommandActionRunner : CommonRunner<PlaybackCommandInput, PlaybackCommandOutput>() {

    override suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<PlaybackCommandInput>,
        musicService: MusicService
    ): TaskerPluginResult<PlaybackCommandOutput> {

        var executed = false
        val command: Commands

        try{
            command = Commands.valueOf(input.regular.command ?: "")
        }catch (e: Exception){
            return TaskerPluginResultErrorWithOutput(
                code = 1,
                message = "Invalid command (${input.regular.command})"
            )
        }

        when (command) {

            Commands.TOGGLE_LIKE -> {
                musicService.toggleLike()
                executed = true
            }

            Commands.LIKE_SONG -> {
                val songEntity = musicService.database.song(musicService.currentMediaMetadata.value?.id)
                    .firstOrNull()?.song
                if (songEntity?.liked == false) {
                    musicService.toggleLike()
                    executed = true
                }
            }

            Commands.UNLIKE_SONG -> {
                val songEntity = musicService.database.song(musicService.currentMediaMetadata.value?.id)
                    .firstOrNull()?.song
                if (songEntity?.liked == true) {
                    musicService.toggleLike()
                    executed = true
                }
            }

            Commands.TOGGLE_PLAY_PAUSE -> {
                withContext(Dispatchers.Main) { musicService.player.togglePlayPause()  }
                executed = true
            }

            Commands.PLAY -> {
                if(!musicService.isPlaybackOngoing) {
                    withContext(Dispatchers.Main) { musicService.player.play() }
                    executed = true
                }
            }

            Commands.STOP -> {
                if(musicService.isPlaybackOngoing) {
                    withContext(Dispatchers.Main) { musicService.player.stop() }
                    executed = true
                }
            }

            Commands.NEXT_SONG -> {
                withContext(Dispatchers.Main) {
                    if(musicService.player.hasNextMediaItem()) {
                        musicService.player.seekToNext()
                        executed = true
                    }
                }
            }

            Commands.PREVIOUS_SONG -> {
                withContext(Dispatchers.Main) {
                    if(musicService.player.hasPreviousMediaItem()) {
                        musicService.player.seekToPrevious()
                        executed = true
                    }
                }
            }
        }

        return TaskerPluginResultSucess(
            regular = PlaybackCommandOutput(
                executed = executed
            )
        )

    }
}

