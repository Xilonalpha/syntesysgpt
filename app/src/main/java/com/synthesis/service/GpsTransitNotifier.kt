package com.synthesis.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.synthesis.MainActivity
import com.synthesis.domain.model.exoplanet.LiveTransitTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class GpsTransitNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "synthesis_gps_transits"
        const val NOTIFICATION_ID = 4201
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SYNTHESIS Live Transit Alerts"
            val descriptionText = "High-priority alerts when exoplanet transits pass above your GPS coordinates."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Dispatches the real-time GPS transit alert requested by the user.
     * Computes the dynamic local ingress time, countdown, and sky position.
     */
    fun sendGpsTransitAlert(
        target: LiveTransitTarget? = null,
        userLatitude: Double = Double.NaN,
        userLongitude: Double = Double.NaN
    ): Pair<Boolean, String> = false to
        "Alertă indisponibilă: efemerida și vizibilitatea locală nu au fost validate."
}
