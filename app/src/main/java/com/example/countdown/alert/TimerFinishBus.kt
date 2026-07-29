package com.example.countdown.alert

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 后台闹钟 / 响铃服务与前台 ViewModel 之间的事件总线。 */
object TimerFinishBus {
    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private val _silenced = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val silenced: SharedFlow<Unit> = _silenced.asSharedFlow()

    fun emitFinished() {
        _finished.tryEmit(Unit)
    }

    fun emitSilenced() {
        _silenced.tryEmit(Unit)
    }
}
