package com.example.countdown.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.countdown.MainActivity

/** 调度/取消到点闹钟；后台也能准时触发。 */
interface TimerAlarmScheduler {
    fun schedule(endAtMillis: Long)
    fun cancel()
}

object NoOpTimerAlarmScheduler : TimerAlarmScheduler {
    override fun schedule(endAtMillis: Long) = Unit
    override fun cancel() = Unit
}

class AndroidTimerAlarmScheduler(
    context: Context,
) : TimerAlarmScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(endAtMillis: Long) {
        cancel()
        val triggerAt = endAtMillis.coerceAtLeast(System.currentTimeMillis() + 100L)
        val showIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_SHOW,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingFlags(),
        )
        val operation = PendingIntent.getBroadcast(
            appContext,
            REQUEST_FIRE,
            Intent(appContext, TimerFinishedReceiver::class.java).setAction(TimerFinishedReceiver.ACTION_FIRE),
            pendingFlags(),
        )
        runCatching {
            val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
            alarmManager.setAlarmClock(info, operation)
        }.recoverCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        }
        // 若仍失败（无精确闹钟权限），不抛出；进程存活时由 ViewModel ticker 到点结束
    }

    override fun cancel() {
        val operation = PendingIntent.getBroadcast(
            appContext,
            REQUEST_FIRE,
            Intent(appContext, TimerFinishedReceiver::class.java).setAction(TimerFinishedReceiver.ACTION_FIRE),
            pendingFlags(),
        )
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun pendingFlags(): Int {
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.FLAG_UPDATE_CURRENT or immutable
    }

    companion object {
        private const val REQUEST_FIRE = 1001
        private const val REQUEST_SHOW = 1002
    }
}
