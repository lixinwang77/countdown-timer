package com.example.countdown.alert

import android.content.Context

interface TimerAlertController {
    fun scheduleFinish(endAtMillis: Long)
    fun cancelSchedule()
    fun startAlert()
    fun stopAlert()
}

object NoOpTimerAlertController : TimerAlertController {
    override fun scheduleFinish(endAtMillis: Long) = Unit
    override fun cancelSchedule() = Unit
    override fun startAlert() = Unit
    override fun stopAlert() = Unit
}

class AndroidTimerAlertController(
    context: Context,
) : TimerAlertController {
    private val appContext = context.applicationContext
    private val scheduler = AndroidTimerAlarmScheduler(appContext)

    override fun scheduleFinish(endAtMillis: Long) = scheduler.schedule(endAtMillis)
    override fun cancelSchedule() = scheduler.cancel()
    override fun startAlert() = FinishedAlertService.start(appContext)
    override fun stopAlert() = FinishedAlertService.stop(appContext)
}
