package com.metrolist.music.tasker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicService.MusicBinder
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class CommonRunner<TInput : Any, TOutput : Any> :
    TaskerPluginRunnerAction<TInput, TOutput>() {

    /**
     * This is a suspend function to simplify the implementations, since many MusicService
     * and MusicService.database functions are suspend functions.
     */
    abstract suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<TInput>,
        musicService: MusicService
    ): TaskerPluginResult<TOutput>

    override fun run(context: Context, input: TaskerInput<TInput>): TaskerPluginResult<TOutput> {

        val countDownLatch = CountDownLatch(1)
        var musicService: MusicService? = null

        // Create listener for MusicService connection
        val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (service is MusicBinder) {
                        musicService = service.service
                        countDownLatch.countDown()
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    musicService = null
                }
            }

        // Request connection to the music service
        context.bindService(
            Intent(context, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        // Wait for musicService to be initialized
        countDownLatch.await(10, TimeUnit.SECONDS)

        try {
            var result: TaskerPluginResult<TOutput>

            // use runBlocking because runWithMusicService is a suspend function
            runBlocking {
                result = musicService?.let {
                    runWithMusicService(context, input, it)
                } ?: TaskerPluginResultErrorWithOutput(
                    1,
                    "Cannot connect to the MusicService"
                )
            }

            return result
        } finally {
            context.unbindService(serviceConnection)
        }
    }
}
