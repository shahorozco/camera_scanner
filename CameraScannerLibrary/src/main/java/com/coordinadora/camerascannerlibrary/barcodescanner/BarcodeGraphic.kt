package com.coordinadora.camerascannerlibrary.barcodescanner

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.barcode.common.Barcode

class BarcodeGraphic(
    overlay: GraphicOverlay,
    private val barcode: Barcode?
) : GraphicOverlay.Graphic(overlay) {

    private val rectPaint = Paint().apply {
        color = Color.parseColor("#5799E8")
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
    }

    companion object {
        private const val STROKE_WIDTH = 4.0f
    }

    override fun draw(canvas: Canvas?) {
        barcode?.boundingBox?.let { box ->
            val translatedRect = RectF(
                translateX(box.left.toFloat()),
                translateY(box.top.toFloat()),
                translateX(box.right.toFloat()),
                translateY(box.bottom.toFloat())
            )

            canvas?.drawRect(translatedRect, rectPaint)
        }
    }
}