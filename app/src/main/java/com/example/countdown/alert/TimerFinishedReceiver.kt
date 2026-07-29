package com.example.countdown.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.countdown.data.TimerSessionStore

/**
 * AlarmManager 到点回调：标记结束并启动响铃前台服务。
 * 不依赖 Activity / Compose 是否在前台。
 */
class TimerFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val appContext = context.applicationContext
        val session = TimerSessionStore(appContext)
        session.markFinished(alertActive = true)
        FinishedAlertService.start(appContext)
        TimerFinishBus.emitFinished()
    }

    companion object {
        const val ACTION_FIRE = "com.example.countdown.action.TIMER_FINISHED"
    }
}
