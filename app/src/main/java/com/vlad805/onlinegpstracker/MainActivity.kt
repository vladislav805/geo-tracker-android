package com.vlad805.onlinegpstracker

import android.Manifest
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

    private fun getPref(): SharedPreferences {
        return getSharedPreferences("def", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        askPermissions()

        binding.buttonStart.setOnClickListener {
            val endpoint = binding.etvEndpoint.text.toString()
            val key = binding.etvKey.text.toString()
            var interval = binding.etvInterval.toString().toLongOrNull()
            if (interval == null) {
                interval = TRACK_PERIOD_TIME
            }

            val intent = Intent(this, TrackerService::class.java)

            intent.putExtra("endpoint", endpoint)
            intent.putExtra("key", key)
            intent.putExtra("interval", interval)
            startService(intent)
            setState(State.TRACKING)
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
            copyText(url)
            Toast.makeText(this, "Link copied\n\n${url}", Toast.LENGTH_LONG).show()
        }

        binding.buttonGenerate.setOnClickListener {
            binding.etvKey.setText(generateRandomKey())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                binding.buttonDisableOptimizations.setOnClickListener {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            } else {
                binding.buttonDisableOptimizations.visibility = View.GONE
            }
        } else {
            binding.buttonDisableOptimizations.visibility = View.GONE
        }
    }

    private fun askPermissions() {
        KotlinPermissions.with(this)
            .permissions(Manifest.permission.ACCESS_FINE_LOCATION)
            .onAccepted {
                setState(if (!TrackerService.isServiceStarted) State.SETUP else State.TRACKING)
            }
            .onDenied {

            }
            .onForeverDenied {

            }
            .ask()
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("text", text)
        clipboard?.setPrimaryClip(clip)
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
                binding.etvKey.isEnabled = true
                binding.etvInterval.isEnabled = true
                binding.etvEndpoint.isEnabled = true
                binding.buttonStart.isEnabled = true
                binding.buttonStop.isEnabled = false
            }

            State.TRACKING -> {
                binding.etvKey.isEnabled = false
                binding.etvEndpoint.isEnabled = false
                binding.etvInterval.isEnabled = false
                binding.buttonStart.isEnabled = false
                binding.buttonStop.isEnabled = true

                getPref().edit().putString("endpoint", binding.etvEndpoint.text.toString()).apply()
                getPref().edit().putString("key", binding.etvKey.text.toString()).apply()
                getPref().edit().putLong("interval", binding.etvInterval.text.toString().toLong()).apply()
            }
        }
    }

    private fun generateRandomKey(): String {
        val length = 16
        val allowedChars = ('0'..'9') + ('a'..'f')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
