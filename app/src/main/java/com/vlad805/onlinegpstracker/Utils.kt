package com.vlad805.onlinegpstracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

fun urlEncodeUTF8(s: String?): String? {
	return try {
		URLEncoder.encode(s, "UTF-8")
	} catch (e: UnsupportedEncodingException) {
		throw UnsupportedOperationException(e)
	}
}

fun urlEncodeUTF8(map: Map<*, *>): String {
	val sb = StringBuilder()
	for ((key, value) in map) {
		if (sb.isNotEmpty()) {
			sb.append("&")
		}
		sb.append(
			String.format(
				"%s=%s",
				urlEncodeUTF8(key.toString()),
				urlEncodeUTF8(value.toString())
			)
		)
	}
	return sb.toString()
}

fun copyText(context: Context, text: String) {
	val clipboard = context.getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager?
	val clip = ClipData.newPlainText("text", text)
	clipboard?.setPrimaryClip(clip)
}

fun generateRandomKey(): String {
	val length = 16
	val allowedChars = ('0'..'9') + ('a'..'f')
	return (1..length)
		.map { allowedChars.random() }
		.joinToString("")
}

fun isPermissionGranted(ctx: Context, id: String): Boolean {
	return ContextCompat.checkSelfPermission(ctx, id) != PackageManager.PERMISSION_GRANTED
}
