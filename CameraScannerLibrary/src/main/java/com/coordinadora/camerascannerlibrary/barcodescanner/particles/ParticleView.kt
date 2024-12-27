package com.coordinadora.camerascannerlibrary.barcodescanner.particles

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class ParticleView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private lateinit var particleSystem: ParticleSystem

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        particleSystem = ParticleSystem(30, w, h)  // 30 partículas
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        particleSystem.draw(canvas)
        invalidate()
    }
}