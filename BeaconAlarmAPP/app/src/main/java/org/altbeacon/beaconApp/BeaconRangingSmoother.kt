package org.altbeacon.beaconApp

import org.altbeacon.beacon.Beacon
import java.util.ArrayList

class BeaconRangingSmoother {
    private val beacons: MutableMap<String, Beacon> = mutableMapOf()
    var smoothingWindowMillis: Long = 10000

    val visibleBeacons: List<Beacon>
        get() {
            val visible = ArrayList<Beacon>()
            val currentTime = System.currentTimeMillis()
            for (beacon in beacons.values) {
                if (currentTime - beacon.lastCycleDetectionTimestamp < smoothingWindowMillis) {
                    visible.add(beacon)
                }
            }
            return visible
        }

    fun add(detectedBeacons: Collection<Beacon>): BeaconRangingSmoother {
        val currentTime = System.currentTimeMillis()
        for (beacon in detectedBeacons) {
            beacon.lastCycleDetectionTimestamp = currentTime
            beacons[beacon.id1.toString()] = beacon // Update or add the beacon to the map
        }
        return this
    }

    companion object {
        const val TAG = "BeaconRangingSmoother"
        val shared = BeaconRangingSmoother()
    }
}
