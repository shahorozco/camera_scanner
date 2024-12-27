package com.coordinadora.camerascannerlibrary.barcodescanner.particles

import android.graphics.Canvas
import android.os.Handler

class ParticleSystem(numParticles: Int, canvasWidth: Int, canvasHeight: Int) {
    private val particles: MutableList<Particle> = mutableListOf()
    private val handler = Handler()
    private val updateRunnable = Runnable { updateParticles() }

    init {
        for (i in 0 until numParticles) {
            particles.add(Particle(canvasWidth, canvasHeight))
        }
        start()
    }

    private fun start() {
        handler.postDelayed(updateRunnable, 50)  // Intervalo corto para suavizar la transición
    }

    private fun updateParticles() {
        particles.forEach { it.update() }
        handler.postDelayed(updateRunnable, 50)
    }

    fun draw(canvas: Canvas) {
        particles.forEach { it.draw(canvas) }
    }
}