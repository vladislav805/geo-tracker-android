package com.vlad805.onlinegpstracker

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.location.bestlocationstrategy.BaseLocationStrategy
import com.location.bestlocationstrategy.LocationChangesListener
import com.location.bestlocationstrategy.LocationManagerStrategy
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat.getTimeInstance
import java.util.*

open class TrackerService : Service(), LocationChangesListener {
	companion object {
		var isServiceStarted = false
	}

	private var mEndpoint = ""
	private var mKey: String = ""
	private var mDeltaDistance: Long = TRACK_MIN_DISTANCE
	private var mDeltaTime: Long = TRACK_PERIOD_TIME

	override fun onCreate() {
		super.onCreate()

		if (
			isPermissionGranted(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
			isPermissionGranted(this, Manifest.permission.ACCESS_COARSE_LOCATION) ||
			isPermissionGranted(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
		) {
			Toast.makeText(this, "No permissions for location", Toast.LENGTH_LONG).show()
		}

		isServiceStarted = true
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent == null) {
			return START_STICKY
		}

		Log.i("Service", "onStartCommand")

		when (intent.action) {
			"STOP" -> {
				stopService(Intent(this, TrackerService::class.java))
				stopForeground(true)
			}

			else -> {
				mEndpoint = intent.getStringExtra("endpoint")!!
				mKey = intent.getStringExtra("key")!!
				mDeltaTime = intent.getLongExtra("interval", mDeltaTime)
				showNotification("Click for stop tracking")
				setupLocation()
				registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
			}
		}

		return START_STICKY
	}

	private fun showNotification(status: String) {
		val stopIntent = Intent(this, TrackerService::class.java).apply {
			action = "STOP"
		}

		val pendingIntent: PendingIntent = PendingIntent.getService(this, 0, stopIntent, 0)

		val builder = NotificationCompat.Builder(this, TRACKING_CHANNEL_ID).apply {
			setSmallIcon(R.drawable.ic_my_location)
			setContentTitle("Online tracking on")
			setContentText(status)
			setContentIntent(pendingIntent)
			setNotificationSilent()
			setAutoCancel(false)
			setOngoing(true)
			priority = NotificationCompat.PRIORITY_HIGH
		}

		with(NotificationManagerCompat.from(this)) {
			notify(NOTIFICATION_ID, builder.build())
		}
	}

	private var baseLocationStrategy: BaseLocationStrategy? = null

	override fun onDestroy() {
		if (baseLocationStrategy != null) {
			baseLocationStrategy!!.stopListeningForLocationChanges()
		}

		unregisterReceiver(mBatInfoReceiver)

		isServiceStarted = false

		super.onDestroy()
	}

	private fun setupLocation() {
		val bls = LocationManagerStrategy.getInstance(this).apply {
			setDisplacement(mDeltaDistance)
			setPeriodicalUpdateTime(mDeltaTime * 1000)
			setPeriodicalUpdateEnabled(true)
		}
		bls.startListeningForLocationChanges(this)
		bls.startLocationUpdates()
		baseLocationStrategy = bls
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onLocationChanged(location: Location?) {
		if (location == null) {
			return
		}

		val map = HashMap<String, String>()

		with(location) {
			map["key"] = mKey
			map["lat"] = latitude.toString()
			map["lng"] = longitude.toString()
			map["speed"] = (3.6 * speed).toString()
			map["bearing"] = bearing.toString()
			map["accuracy"] = accuracy.toString()
			map["time"] = (System.currentTimeMillis() / 1000).toString()
		}

		if (mBattery >= 0) {
			map["battery"] = mBattery.toString()
		}

		Thread {
			try {
				sendLocation(map)
			} catch (e: Throwable) {
				e.printStackTrace()
				showNotification("Location send failure: $e")
			}
		}.start()
	}

	override fun onFailure(reason: String?) {
		showNotification("Location failure: $reason")
	}

	override fun onConnected() {
		showNotification("Location connected")
	}

	override fun onConnectionStatusChanged() {
		Log.i("connect_status_change", "111")
	}

	private fun sendLocation(map: Map<String, String>) {
		val qs = urlEncodeUTF8(map)
		val url = "$mEndpoint/api/set?$qs"
		Log.i("sendLoc", url)
		val mURL = URL(url)

		with(mURL.openConnection() as HttpURLConnection) {
			// optional default is GET
			requestMethod = "POST"

			BufferedReader(InputStreamReader(inputStream)).use {
				val response = StringBuffer()

				var inputLine = it.readLine()
				while (inputLine != null) {
					response.append(inputLine)
					inputLine = it.readLine()
				}

				val json = JSONObject(response.toString())

				if (json.optBoolean("result")) {
					val currentDate = getTimeInstance().format(Date())

					showNotification("Location sent at $currentDate")
				} else {
					showNotification("Location sending failed")
				}
			}
		}
	}

	private var mBattery: Int = -1

	private val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(ctxt: Context?, intent: Intent) {
			mBattery = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
		}
	}
}
