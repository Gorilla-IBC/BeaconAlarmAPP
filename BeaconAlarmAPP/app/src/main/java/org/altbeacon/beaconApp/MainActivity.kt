package org.altbeacon.beaconApp

import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.MonitorNotifier
import android.content.Intent
import org.altbeacon.beacon.permissions.BeaconScanPermissionsActivity
import org.altbeacon.beaconreference.R

class MainActivity : AppCompatActivity() {
    lateinit var beaconListView: ListView
    lateinit var beaconCountTextView: TextView
    lateinit var monitoringButton: Button
    lateinit var rangingButton: Button
    lateinit var beaconReferenceApplication: BeaconReferenceApplication
    var alertDialog: AlertDialog? = null

    private lateinit var mediaPlayer: MediaPlayer
    private var isAlarmPlaying = false

    private val beaconList = mutableMapOf<String, Beacon>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        beaconReferenceApplication = application as BeaconReferenceApplication

        // Set up a Live Data observer for beacon data
        val regionViewModel = BeaconManager.getInstanceForApplication(this).getRegionViewModel(beaconReferenceApplication.region)
        regionViewModel.regionState.observe(this, monitoringObserver)
        regionViewModel.rangedBeacons.observe(this, rangingObserver)

        rangingButton = findViewById(R.id.rangingButton)
        monitoringButton = findViewById(R.id.monitoringButton)
        beaconListView = findViewById(R.id.beaconList)
        beaconCountTextView = findViewById(R.id.beaconCount)
        beaconCountTextView.text = "No beacons detected"
        beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))

        // Initialize MediaPlayer for alarm sound
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound)
        mediaPlayer.isLooping = true // Loop the alarm sound
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
        stopAlarm() // Ensure alarm is stopped when the activity is paused
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()
        // Check and request permissions
        if (!BeaconScanPermissionsActivity.allPermissionsGranted(this, true)) {
            val intent = Intent(this, BeaconScanPermissionsActivity::class.java)
            intent.putExtra("backgroundAccessRequested", true)
            startActivity(intent)
        } else {
            if (BeaconManager.getInstanceForApplication(this).monitoredRegions.isEmpty()) {
                (application as BeaconReferenceApplication).setupBeaconScanning()
            }
        }
    }

    val monitoringObserver = Observer<Int> { state ->
        var dialogTitle = "Beacons detected"
        var dialogMessage = "didEnterRegionEvent has fired"
        var stateString = "inside"
        if (state == MonitorNotifier.OUTSIDE) {
            dialogTitle = "No beacons detected"
            dialogMessage = "didExitRegionEvent has fired"
            stateString = "outside"
            beaconCountTextView.text = "Outside of the beacon region -- no beacons detected"
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
        } else {
            beaconCountTextView.text = "Inside the beacon region."
        }
        Log.d(TAG, "monitoring state changed to : $stateString")
        val builder = AlertDialog.Builder(this)
        builder.setTitle(dialogTitle)
        builder.setMessage(dialogMessage)
        builder.setPositiveButton(android.R.string.ok, null)
        alertDialog?.dismiss()
        alertDialog = builder.create()
        alertDialog?.show()
    }

    val rangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(TAG, "Ranged: ${beacons.size} beacons")
        // Update or add beacons to the map
        for (beacon in beacons) {
            beaconList[beacon.id1.toString()] = beacon
            // Check distance immediately
            val distance = beacon.distance
            if (distance > 0.5) {
                startAlarm()
            } else {
                stopAlarm()
            }
        }
        updateBeaconList()
    }

    private fun updateBeaconList() {
        val sortedBeacons = beaconList.values.sortedBy { it.distance }
        beaconCountTextView.text = "Ranging enabled: ${sortedBeacons.size} beacon(s) detected"
        beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            sortedBeacons.map { "${it.id1}\nid2: ${it.id2} id3: ${it.id3} rssi: ${it.rssi}\nest. distance: ${it.distance} m" }.toTypedArray())
    }

    private fun startAlarm() {
        if (!isAlarmPlaying) {
            mediaPlayer.start()
            isAlarmPlaying = true
            Log.d(TAG, "Alarm started")
        }
    }

    private fun stopAlarm() {
        if (isAlarmPlaying) {
            mediaPlayer.pause()
            mediaPlayer.seekTo(0) // Reset to the beginning
            isAlarmPlaying = false
            Log.d(TAG, "Alarm stopped")
        }
    }

    fun rangingButtonTapped(view: View) {
        val beaconManager = BeaconManager.getInstanceForApplication(this)
        if (beaconManager.rangedRegions.isEmpty()) {
            beaconManager.startRangingBeacons(beaconReferenceApplication.region)
            rangingButton.text = "Stop Ranging"
            beaconCountTextView.text = "Ranging enabled -- awaiting first callback"
        } else {
            beaconManager.stopRangingBeacons(beaconReferenceApplication.region)
            rangingButton.text = "Start Ranging"
            beaconCountTextView.text = "Ranging disabled -- no beacons detected"
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
            stopAlarm() // Stop the alarm when ranging is stopped
        }
    }

    fun monitoringButtonTapped(view: View) {
        val beaconManager = BeaconManager.getInstanceForApplication(this)
        val dialogTitle: String
        val dialogMessage: String
        if (beaconManager.monitoredRegions.isEmpty()) {
            beaconManager.startMonitoring(beaconReferenceApplication.region)
            dialogTitle = "Beacon monitoring started."
            dialogMessage = "You will see a dialog if a beacon is detected, and another if beacons then stop being detected."
            monitoringButton.text = "Stop Monitoring"
        } else {
            beaconManager.stopMonitoring(beaconReferenceApplication.region)
            dialogTitle = "Beacon monitoring stopped."
            dialogMessage = "You will no longer see dialogs when beacons start/stop being detected."
            monitoringButton.text = "Start Monitoring"
        }
        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setMessage(dialogMessage)
            .setPositiveButton(android.R.string.ok, null)
            .also {
                alertDialog?.dismiss()
                alertDialog = it.create()
            }.show()
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
