package io.github.vivekg7.galla

import android.app.Application
import android.content.Context
import io.github.vivekg7.galla.data.Cart
import io.github.vivekg7.galla.data.Db
import io.github.vivekg7.galla.data.ShopInfo
import io.github.vivekg7.galla.data.shop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** Every long-lived object, built once. No DI framework: there are four of them. */
class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { Db.open(this) }
    val dao get() = db.dao()
    val cart = Cart()

    /** Null until the first read, so the first-run screen does not flash up for a set-up shop. */
    val shop by lazy { dao.shop().stateIn(scope, SharingStarted.Eagerly, null as ShopInfo?) }
}

val Context.app get() = applicationContext as App
