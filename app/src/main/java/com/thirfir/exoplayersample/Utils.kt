package com.thirfir.exoplayersample

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.TypedValue

fun Float.toPx(context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        context.resources.displayMetrics
    )
}

fun Uri.getRealPath(context: Context): String? {
    var filePath: String? = null
    val cursor = context.contentResolver.query(this, arrayOf(MediaStore.Video.Media.DATA), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val columnIndex = it.getColumnIndex(MediaStore.Video.Media.DATA)
            if (columnIndex != -1) {
                filePath = it.getString(columnIndex)
            }
        }
    }
    return filePath
}

fun Long.roundToNearestInterval(interval: Long): Long {
    val quotient = this / interval
    val remainder = this % interval

    val halfInterval = interval / 2

    return if (remainder < halfInterval) {
        quotient * interval // 아래 경계로 반올림
    } else {
        (quotient + 1) * interval // 위 경계로 반올림
    }
}