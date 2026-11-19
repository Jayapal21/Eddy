package app.eddy.browser

import android.app.Application
import app.eddy.browser.util.Strings

class EddyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Strings.install(resources)
        container = AppContainer(this)
        container.scheduleBackgroundStartup()
    }
}
