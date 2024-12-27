package com.coordinadora.camerascannerlibrary.barcodescanner

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeScannerProcessor(
    context: Context,
    private val exchangeScannedData: ExchangeScannedData
) : VisionProcessorBase<List<Barcode?>?>(context) {

    private val barcodeScanner: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128)
            .build()
        BarcodeScanning.getClient(options)
    }

    override fun stop() {
        super.stop()
        barcodeScanner.close()
    }

    override fun detectInImage(image: InputImage): Task<List<Barcode?>?> {
        return barcodeScanner.process(image)
    }

    override fun onSuccess(results: List<Barcode?>?, graphicOverlay: GraphicOverlay) {
        results?.forEach { barcode ->
            barcode?.let { barcodeLet ->
                graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcodeLet))
                barcodeLet.rawValue?.let { value ->
                    value.takeIf { data -> data.isNotEmpty() }
                        ?.let { exchangeScannedData.sendScannedCode(it) }
                }
            }
        }
    }

    override fun onFailure(e: Exception) {}
}