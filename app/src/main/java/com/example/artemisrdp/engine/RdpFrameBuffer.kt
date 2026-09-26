package com.example.artemisrdp.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RdpFrameBuffer(val width: Int, val height: Int) {

    private val lock = ReentrantLock()
    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Version counter to trigger Compose recomposition / canvas invalidation
    private val _frameRevision = MutableStateFlow(0L)
    val frameRevision: StateFlow<Long> = _frameRevision.asStateFlow()

    fun updateRect(srcBitmap: Bitmap, srcRect: Rect, destRect: Rect) {
        lock.withLock {
            canvas.drawBitmap(srcBitmap, srcRect, destRect, paint)
            _frameRevision.value++
        }
    }

    fun drawCustom(drawBlock: (Canvas) -> Unit) {
        lock.withLock {
            drawBlock(canvas)
            _frameRevision.value++
        }
    }

    fun withBuffer(action: (Bitmap) -> Unit) {
        lock.withLock {
            action(bitmap)
        }
    }
}
