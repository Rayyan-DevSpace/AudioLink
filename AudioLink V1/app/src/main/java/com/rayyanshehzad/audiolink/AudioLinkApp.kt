package com.rayyanshehzad.audiolink

import android.app.Application
import com.rayyanshehzad.audiolink.data.RoutingRepository

class AudioLinkApp : Application() {
    val repository: RoutingRepository by lazy { RoutingRepository(this) }
}
