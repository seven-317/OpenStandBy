package io.github.seven317.openstandby

import android.app.Application

class OpenStandByApplication : Application() {
    internal val container by lazy { AppContainer(this) }
}
