package com.kdroid.filter.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object IconLoader {
    fun load(ctx: Context, pkg: String, sizePx: Int = 96): ImageBitmap? {
        return try {
            val d: Drawable = ctx.packageManager.getApplicationIcon(pkg)
            d.toBitmap(sizePx).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bmp
    }
}
