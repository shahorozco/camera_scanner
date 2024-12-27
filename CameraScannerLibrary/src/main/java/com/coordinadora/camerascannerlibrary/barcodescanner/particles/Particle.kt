package com.coordinadora.camerascannerlibrary.barcodescanner.particles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import kotlin.random.Random

class Particle(private val canvasWidth: Int, private val canvasHeight: Int) {
    private var position: Point = Point(Random.nextInt(canvasWidth), Random.nextInt(canvasHeight))
    private var alpha: Int = 150
    private var delay: Int = Random.nextInt(100)  // Retraso antes de empezar a cambiar
    private val paint: Paint = Paint()
    private var fadingOut: Boolean = true

    init {
        paint.style = Paint.Style.FILL
        paint.setARGB(alpha, 255, 255, 255)
    }

    fun update() {
        if (delay > 0) {
            delay--
            return
        }

        if (fadingOut) {
            alpha -= 15
            if (alpha <= 0) {
                alpha = 0
                fadingOut = false
                position = Point(Random.nextInt(canvasWidth), Random.nextInt(canvasHeight))
            }
        } else {
            alpha += 15
            if (alpha >= 150) {
                alpha = 150
                fadingOut = true
            }
        }
        paint.alpha = alpha
    }

    fun draw(canvas: Canvas) {
        canvas.drawCircle(position.x.toFloat(), position.y.toFloat(), 2.7f, paint)
    }
}