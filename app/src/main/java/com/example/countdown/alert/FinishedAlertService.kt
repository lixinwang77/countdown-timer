package com.example.countdown.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.countdown.MainActivity
import com.example.countdown.R
import com.example.countdown.data.TimerSessionStore

/**
 * 结束后台响铃服务：即使 Activity 不在前台也能循环播放提示音并震动。
 * 后台按音量键会触发系统 VOLUME_CHANGED，由此停止响铃。
 */
class FinishedAlertService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var toneThread: Thread? = null
    private var volumeReceiver: BroadcastReceiver? = null
    private var alertStartedAtElapsed: Long = 0L
    private var stopping: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlertAndSelf(notifyUi = true)
                return START_NOT_STICKY
            }
            else -> {
                ensureChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startAlertPlayback()
                registerVolumeKeyStop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterVolumeKeyStop()
        stopPlayback()
        super.onDestroy()
    }

    private fun stopAlertAndSelf(notifyUi: Boolean) {
        if (stopping) return
        stopping = true
        TimerSessionStore(this).setAlertActive(false)
        unregisterVolumeKeyStop()
        stopPlayback()
        if (notifyUi) {
            TimerFinishBus.emitSilenced()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerVolumeKeyStop() {
        if (volumeReceiver != null) return
        alertStartedAtElapsed = SystemClock.elapsedRealtime()
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != VOLUME_CHANGED_ACTION) return
                // 避开启动瞬间可能的音量回调，防止误停
                if (SystemClock.elapsedRealtime() - alertStartedAtElapsed < 600L) return
                val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (stream == AudioManager.STREAM_ALARM ||
                    stream == AudioManager.STREAM_MUSIC ||
                    stream == AudioManager.STREAM_RING ||
                    stream == AudioManager.STREAM_NOTIFICATION
                ) {
                    stopAlertAndSelf(notifyUi = true)
                }
            }
        }
        val filter = IntentFilter(VOLUME_CHANGED_ACTION)
        // 系统广播，需 EXPORTED 才能收到
        ContextCompat.registerReceiver(
            this,
            volumeReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun unregisterVolumeKeyStop() {
        val receiver = volumeReceiver ?: return
        runCatching { unregisterReceiver(receiver) }
        volumeReceiver = null
    }

    private fun startAlertPlayback() {
        if (mediaPlayer != null || toneThread != null) return
        stopping = false
        TimerSessionStore(this).setAlertActive(true)
        val played = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return@runCatching false
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
            true
        }.getOrDefault(false)

        if (!played) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
                toneGenerator = tone
                toneThread = Thread {
                    try {
                        while (!Thread.currentThread().isInterrupted) {
                            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)
                            Thread.sleep(1600L)
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }.also { it.start() }
            }
        }

        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 300, 500), 0))
        }
    }

    private fun stopPlayback() {
        toneThread?.interrupt()
        toneThread = null
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        runCatching {
            mediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.alert_channel_desc)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FinishedAlertService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.finished))
            .setContentText(getString(R.string.alert_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, getString(R.string.stop), stop)
            .build()
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    companion object {
        const val ACTION_START = "com.example.countdown.action.START_ALERT"
        const val ACTION_STOP = "com.example.countdown.action.STOP_ALERT"
        private const val CHANNEL_ID = "countdown_finished_alert"
        private const val NOTIFICATION_ID = 42
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        fun start(context: Context) {
            val intent = Intent(context, FinishedAlertService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FinishedAlertService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
