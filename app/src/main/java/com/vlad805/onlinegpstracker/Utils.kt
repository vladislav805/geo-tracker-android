package com.vlad805.onlinegpstracker

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

fun urlEncodeUTF8(s: String?): String? {
	return try {
		URLEncoder.encode(s, "UTF-8")
	} catch (e: UnsupportedEncodingException) {
		throw UnsupportedOperationException(e)
	}
}

fun urlEncodeUTF8(map: Map<*, *>): String? {
	val sb = StringBuilder()
	for ((key, value) in map) {
		if (sb.length > 0) {
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