package ru.infra.alerts

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Watchdog.schedule(this)
        AlertService.start(this)
    }
}
