package io.github.lucaskfp.learnharness

import android.app.Application
import io.github.lucaskfp.learnharness.app.AppContainer

class LearnHarnessApplication : Application() {
    val container: AppContainer by lazy { AppContainer.create(this) }
}
