package com.vlad805.onlinegpstracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kotlinpermissions.KotlinPermissions
import com.vlad805.onlinegpstracker.databinding.ActivityMainBinding

enum class State {
    ASK_PERMISSIONS,
    SETUP,
    TRACKING,
}

const val ENDPOINT = "https://gpsc.velu.ga"

class MainActivity : AppCompatActivity() {
    private var state: State = State.ASK_PERMISSIONS

    private lateinit var binding: ActivityMainBinding

    private val RESULT_ID_DISABLE_OPTIMIZATION = 4795

    private fun getPref(): SharedPreferences {
        return getSharedPreferences("def", Context.MODE_PRIVATE)
    }

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.buttonStart.setOnClickListener {
            askPermissions()
        }

        binding.buttonStop.setOnClickListener {
            stopService(Intent(this, TrackerService::class.java))
            setState(State.SETUP)
        }

        val pref = getPref()
        binding.etvInterval.setText(pref.getLong("interval", 10).toString())
        binding.etvKey.setText(pref.getString("key", "test"))
        binding.etvEndpoint.setText(pref.getString("endpoint", ENDPOINT))

        binding.buttonCopyLink.setOnClickListener {
            val url = "${binding.etvEndpoint.text}/?key=${binding.etvKey.text}"
            copyText(this, url)
            Toast.makeText(this, "Link copied\n\n${url}", Toast.LENGTH_LONG).show()
        }

        binding.buttonGenerate.setOnClickListener {
            binding.etvKey.setText(generateRandomKey())
        }

        updateButtonDisableOptimization()
    }

    private fun askPermissions() {
        with(KotlinPermissions.with(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                permissions(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            onAccepted {
                setState(State.SETUP)
                startTrackerService()
            }
            onDenied {
                setState(State.ASK_PERMISSIONS)
            }
            ask()
        }
    }

    private fun startTrackerService() {
        val endpoint = binding.etvEndpoint.text.toString()
        val key = binding.etvKey.text.toString()
        var interval = binding.etvInterval.toString().toLongOrNull()

        if (interval == null) {
            interval = TRACK_PERIOD_TIME
        }

        val intent = Intent(this, TrackerService::class.java).apply {
            putExtra("endpoint", endpoint)
            putExtra("key", key)
            putExtra("interval", interval)
        }

        startService(intent)
        setState(State.TRACKING)
    }

    private fun setState(state: State) {
        this.state = state

        when (state) {
            State.ASK_PERMISSIONS -> {
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonStart.isEnabled = true
                binding.buttonStop.isEnabled = false
            }

            State.SETUP -> {
                setTextAreasEnabled(true)
                binding.buttonStart.isEnabled = true
                binding.buttonStop.isEnabled = false
            }

            State.TRACKING -> {
                setTextAreasEnabled(false)
                binding.buttonStart.isEnabled = false
                binding.buttonStop.isEnabled = true

                val pref = getPref().edit()
                pref.putString("endpoint", binding.etvEndpoint.text.toString())
                pref.putString("key", binding.etvKey.text.toString())
                pref.putLong("interval", binding.etvInterval.text.toString().toLong())
                pref.apply()
            }
        }
    }

    private fun setTextAreasEnabled(state: Boolean) {
        binding.etvKey.isEnabled = state
        binding.etvEndpoint.isEnabled = state
        binding.etvInterval.isEnabled = state
    }

    @SuppressLint("BatteryLife")
    private fun updateButtonDisableOptimization() {
        // If old device, hide button
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            binding.buttonDisableOptimizations.visibility = View.GONE
            return
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager

        // If already optimizations are disabled
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            binding.buttonDisableOptimizations.visibility = View.GONE
            return
        }

        // If enabled, show button and set click listener
        binding.buttonDisableOptimizations.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, RESULT_ID_DISABLE_OPTIMIZATION)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RESULT_ID_DISABLE_OPTIMIZATION -> {
                updateButtonDisableOptimization()
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
