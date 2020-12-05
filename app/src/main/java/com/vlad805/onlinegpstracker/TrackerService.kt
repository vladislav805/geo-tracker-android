package com.vlad805.onlinegpstracker

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import kotlin.collections.HashMap


const val CHANNEL_ID = "chanId";
const val NOTIFICATION_ID = 54894;

open class TrackerService : Service(), LocationChangesListener {
	var mEndpoint = ""
	var mKey: String = ""
	var mDeltaDistance: Long = TRACK_MIN_DISTANCE
	var mDeltaTime: Long = TRACK_PERIOD_TIME

	override fun onCreate() {
		super.onCreate()

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(this, "No access", Toast.LENGTH_LONG).show()
		}

		isServiceStarted = true
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent == null) {
			return START_STICKY;
		}

		Log.i("Service", "onStartCommand")

		when (intent.action) {
			"STOP" -> {
				stopService(Intent(this, TrackerService::class.java))
				stopForeground(true)
			}

			else -> {
				mEndpoint = intent.getStringExtra("endpoint");
				mKey = intent.getStringExtra("key");
				mDeltaTime = intent.getLongExtra("interval", mDeltaTime);
				showNotification("Click for stop tracking")
				setupLocation()
				registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			}
		}


		return START_STICKY;
	}

	private fun showNotification(status: String) {
		val stopIntent = Intent(this, TrackerService::class.java).apply {
			action = "STOP"
		}
		val pendingIntent: PendingIntent = PendingIntent.getService(this, 0, stopIntent, 0)

		val builder = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_my_location)
			.setContentTitle("Online tracking on")
			.setContentText(status)
			.setContentIntent(pendingIntent)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT);

		with(NotificationManagerCompat.from(this)) {
			// notificationId is a unique int for each notification that you must define

			val notif = builder.build()

			notify(NOTIFICATION_ID, notif)
			//startForeground(NOTIFICATION_ID, notif)
		}
	}

	private var baseLocationStrategy: BaseLocationStrategy? = null;

	override fun onDestroy() {
		if (baseLocationStrategy != null) {
			baseLocationStrategy!!.stopListeningForLocationChanges()
		}

		unregisterReceiver(mBatInfoReceiver);

		isServiceStarted = false

		super.onDestroy()
	}

	private fun setupLocation() {
		val bls = LocationManagerStrategy.getInstance(this);
		bls.setDisplacement(mDeltaDistance);
		bls.setPeriodicalUpdateTime(mDeltaTime * 1000);
		bls.setPeriodicalUpdateEnabled(true);
		bls.startListeningForLocationChanges(this);
		bls.startLocationUpdates();

		baseLocationStrategy = bls;
	}

	override fun onBind(intent: Intent): IBinder? {
		return null;
	}

	override fun onLocationChanged(location: Location?) {
		if (location == null) {
			return;
		}

		val map = HashMap<String, String>()

		map["key"] = mKey
		map["lat"] = location.latitude.toString()
		map["lng"] = location.longitude.toString()
		map["speed"] = (3.6 * location.speed).toString()
		map["bearing"] = location.bearing.toString()
		map["accuracy"] = location.accuracy.toString()
		map["time"] = (System.currentTimeMillis() / 1000).toString()

		if (battery >= 0) {
			map["battery"] = battery.toString();
		}

		Thread(Runnable {
			try {
				sendLocation(map)
			} catch (e: Throwable) {
				e.printStackTrace()
				showNotification("Location send failure: ${e.toString()}")
			}
		}).start()
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
		val url = "${mEndpoint}/api/set?$qs";
		Log.i("sendLoc", url);
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

	companion object {
		var isServiceStarted = false
	}

	var battery: Int = -1;

	private val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(ctxt: Context?, intent: Intent) {
			val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
			battery = level;
		}
	}
}
