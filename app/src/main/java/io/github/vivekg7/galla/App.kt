package io.github.vivekg7.galla

import android.app.Application
import android.content.Context
import io.github.vivekg7.galla.data.Cart
import io.github.vivekg7.galla.data.Db
import io.github.vivekg7.galla.data.Setting
import io.github.vivekg7.galla.data.ShopInfo
import io.github.vivekg7.galla.data.Sync
import io.github.vivekg7.galla.ui.Welcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.util.UUID

/** Every long-lived object, built once. No DI framework: there are five of them. */
class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { Db.open(this) }
    val dao get() = db.dao()
    val cart = Cart()
    val sync by lazy { Sync(db, File(filesDir, "sync"), installId()) }

    /** Null until the first read, so the welcome screen does not flash up for a set-up shop. */
    val settings by lazy { dao.settings().stateIn(scope, SharingStarted.Eagerly, null as List<Setting>?) }
    val shop by lazy { settings.map { it?.let(ShopInfo::from) }.stateIn(scope, SharingStarted.Eagerly, null) }
    val welcome = Welcome()

    /**
     * New with each install, and kept out of every backup (noBackupFilesDir):
     * how Sync tells that the database was restored onto another install.
     */
    private fun installId(): String {
        val file = File(noBackupFilesDir, "install-id")
        if (!file.exists()) file.writeText(UUID.randomUUID().toString())
        return file.readText()
    }
}

val Context.app get() = applicationContext as App
