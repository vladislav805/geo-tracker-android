package com.vlad805.onlinegpstracker

import android.Manifest
import android.content.*
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kotlinpermissions.KotlinPermissions
import kotlinx.android.synthetic.main.activity_main.*

enum class State {
    ASK_PERMISSIONS,
    SETUP,
    TRACKING,
}

const val ENDPOINT = "https://gpsc.velu.ga"

class MainActivity : AppCompatActivity() {
    private var state: State = State.ASK_PERMISSIONS

    private fun getPref(): SharedPreferences {
        return getSharedPreferences("def", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        askPermissions()

        button_start.setOnClickListener {
            val endpoint = etv_endpoint.text.toString()
            val key = etv_key.text.toString()
            var interval = etv_interval.toString().toLongOrNull()
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

        button_stop.setOnClickListener {
            stopService(Intent(this, TrackerService::class.java))
            setState(State.SETUP)
        }

        val pref = getPref()
        etv_interval.setText(pref.getLong("interval", 10).toString())
        etv_key.setText(pref.getString("key", "test"))
        etv_endpoint.setText(pref.getString("endpoint", ENDPOINT))

        button_copy_link.setOnClickListener {
            val url = "${etv_endpoint.text}/?key=${etv_key.text}";
            copyText(url)
            Toast.makeText(this, "Link copied\n\n${url}", Toast.LENGTH_LONG).show()
        }

        button_generate.setOnClickListener {
            etv_key.setText(generateRandomKey())
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
        val clip = ClipData.newPlainText("text", text);
        clipboard?.primaryClip = clip;
    }

    private fun setState(state: State) {
        this.state = state;
        when (state) {
            State.ASK_PERMISSIONS -> {
                button_start.visibility = View.VISIBLE
                button_start.isEnabled = true
                button_stop.isEnabled = false
            }

            State.SETUP -> {
                etv_key.isEnabled = true
                etv_interval.isEnabled = true
                etv_endpoint.isEnabled = true
                button_start.isEnabled = true
                button_stop.isEnabled = false
            }

            State.TRACKING -> {
                etv_key.isEnabled = false
                etv_endpoint.isEnabled = false
                etv_interval.isEnabled = false
                button_start.isEnabled = false
                button_stop.isEnabled = true

                getPref().edit().putString("endpoint", etv_endpoint.text.toString()).apply()
                getPref().edit().putString("key", etv_key.text.toString()).apply()
                getPref().edit().putLong("interval", etv_interval.text.toString().toLong()).apply()
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
