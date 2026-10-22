package io.github.revocx35.tiktoklogger

import android.app.Application
import io.github.revocx35.tiktoklogger.data.AppGraph
import io.github.revocx35.tiktoklogger.data.Diagnostics
import io.github.revocx35.tiktoklogger.media.AutoSaver
import io.github.revocx35.tiktoklogger.media.MediaOps

class TikTokLoggerApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val diagnostics = Diagnostics(this).also { it.install() }
        MediaOps.cleanup(this)
        graph = AppGraph(this, diagnostics)
        // Re-registers the periodic job if it was lost (e.g. after an app update); KEEP leaves a scheduled one alone.
        if (graph.prefs.autoSave) AutoSaver.schedule(this, true)
    }
}
