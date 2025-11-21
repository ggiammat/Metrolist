package com.metrolist.music.tasker.actions

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.media3.exoplayer.offline.DownloadService
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
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.ExoDownloadService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject


@TaskerInputRoot
class RemoveSongInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "songId",
        labelResIdName = "song_id_name",
        descriptionResIdName = "song_id_description"
    ) var songId: String? = null,

)

@TaskerOutputObject
class RemoveSongOutput(
)


class RemoveSongActionHelper(config: TaskerPluginConfig<RemoveSongInput>) :
    TaskerPluginConfigHelper<RemoveSongInput, RemoveSongOutput, RemoveSongActionRunner>(config) {
    override val inputClass = RemoveSongInput::class.java
    override val outputClass = RemoveSongOutput::class.java
    override val runnerClass = RemoveSongActionRunner::class.java
}


class RemoveSongConfigActivity : ComponentActivity(), TaskerPluginConfig<RemoveSongInput> {

    override val context get() = applicationContext

    val songId = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<RemoveSongInput>) {
        songId.value = input.regular.songId ?: ""

    }

    override val inputForTasker: TaskerInput<RemoveSongInput>
        get() = TaskerInput(
            RemoveSongInput(
                songId = songId.value
            )
        )

    private val taskerHelper by lazy { RemoveSongActionHelper(this) }



    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContent {
            TaskerConfigurationScreen(
                title = "Configure Playback Command action"
            ) {
                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.song_id_name),
                    inputDescription = stringResource(R.string.song_id_description),
                    songId,
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


class RemoveSongActionRunner : CommonRunner<RemoveSongInput, RemoveSongOutput>() {

    override suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<RemoveSongInput>,
        musicService: MusicService
    ): TaskerPluginResult<RemoveSongOutput> {

        val songId =
            input.regular.songId ?: return TaskerPluginResultErrorWithOutput(1, "Empty songId")

        val song = musicService.database.song(songId).firstOrNull()?.song ?: return TaskerPluginResultErrorWithOutput(1, "Song not found in the database")

        // remove from downloads
        if (song.isDownloaded) {
            DownloadService.sendRemoveDownload(
                context,
                ExoDownloadService::class.java,
                songId,
                false,
            )
        }

        // remove from cache
        withContext(Dispatchers.Main) {
            musicService.playerCache.removeResource(songId)
        }

        // remove from library (both the db and YTM)
        if (song.inLibrary != null){

            song.libraryRemoveToken?.let {
                withContext(Dispatchers.IO){
                    YouTube.feedback(listOf(it))
                }
            }

            musicService.database.query { update(song.toggleLibrary()) }
        }

        return TaskerPluginResultSucess()
    }

}
