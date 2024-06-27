package org.altbeacon.beaconApp

import android.app.*
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import org.altbeacon.beacon.*

class BeaconReferenceApplication : Application() {
    val region = Region("all-beacons", null, null, null)

    override fun onCreate() {
        super.onCreate()
        val beaconManager = BeaconManager.getInstanceForApplication(this)
        BeaconManager.setDebug(true)
        val parser = BeaconParser().apply {
            setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
            setHardwareAssistManufacturerCodes(intArrayOf(0x004c))
        }
        beaconManager.beaconParsers.add(parser)
        setupBeaconScanning()
    }

    fun setupBeaconScanning() {
        val beaconManager = BeaconManager.getInstanceForApplication(this)
        try {
            setupForegroundService()
        } catch (e: SecurityException) {
            Log.d(TAG, "Not setting up foreground service scanning until location permission granted by user")
            return
        }
        beaconManager.startMonitoring(region)
        beaconManager.startRangingBeacons(region)
        val regionViewModel = beaconManager.getRegionViewModel(region)
        regionViewModel.regionState.observeForever(centralMonitoringObserver)
        regionViewModel.rangedBeacons.observeForever(centralRangingObserver)
    }

    private fun setupForegroundService() {
        val builder = Notification.Builder(this, "beacon-ref-notification-id") // `builder` burada tanımlanıyor
            .setSmallIcon(android.R.drawable.ic_notification_overlay) // Varsayılan bir simge kullanabilirsiniz
            .setContentTitle("Scanning for Beacons")
            .setContentText("Beacon scanning is active")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val channel = NotificationChannel("beacon-ref-notification-id", "Beacon Notification", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notification channel for beacon scanning"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        builder.setChannelId(channel.id)
        BeaconManager.getInstanceForApplication(this).enableForegroundServiceScanning(builder.build(), 456)
    }

    private val centralMonitoringObserver = Observer<Int> { state ->
        val stateString = if (state == MonitorNotifier.OUTSIDE) {
            "outside"
        } else {
            "inside"
        }
        Log.d(TAG, "monitoring state changed to: $stateString")
        if (state == MonitorNotifier.OUTSIDE) {
            Log.d(TAG, "outside beacon region: $region")
        } else {
            Log.d(TAG, "inside beacon region: $region")
            sendNotification()
        }
    }

    private val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        val rangeAgeMillis = System.currentTimeMillis() - (beacons.firstOrNull()?.lastCycleDetectionTimestamp ?: 0)
        if (rangeAgeMillis < 10000) {
            Log.d(TAG, "Ranged: ${beacons.size} beacons")
            for (beacon in beacons) {
                Log.d(TAG, "$beacon about ${beacon.distance} meters away")
            }
        } else {
            Log.d(TAG, "Ignoring stale ranged beacons from $rangeAgeMillis millis ago")
        }
    }

    private fun sendNotification() {
        val builder = NotificationCompat.Builder(this, "beacon-ref-notification-id") // `builder` burada tanımlanıyor
            .setContentTitle("Beacon Reference Application")
            .setContentText("A beacon is nearby.")
            .setSmallIcon(android.R.drawable.ic_notification_overlay) // Varsayılan bir simge kullanabilirsiniz

        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntent(Intent(this, MainActivity::class.java))
        builder.setContentIntent(stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val channel = NotificationChannel("beacon-ref-notification-id", "Beacon Notification", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notification channel for beacon events"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        builder.setChannelId(channel.id)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, builder.build())
    }

    companion object {
        const val TAG = "BeaconReference"
    }
}
