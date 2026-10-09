/*
 * Project Froyo — APBn Squad Message Portal
 * Created & Maintained by Fahad Al-Belal
 * Portfolio: https://fahadnway.qd.je
 */

package com.froyo.apbnsquad.portal

import android.app.Application
import com.froyo.apbnsquad.portal.service.NotificationHelper

/**
 * Base Application class for Project Froyo Portal.
 */
class PortalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            NotificationHelper.createNotificationChannels(this)
        } catch (_: Throwable) {
        }
    }
}
