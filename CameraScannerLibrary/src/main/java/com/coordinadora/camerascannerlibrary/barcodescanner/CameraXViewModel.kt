package com.coordinadora.camerascannerlibrary.barcodescanner

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class CameraXViewModel(application: Application) : AndroidViewModel(application) {
    private var cameraProviderLiveData: MutableLiveData<ProcessCameraProvider>? = null
    val processCameraProvider: LiveData<ProcessCameraProvider>
        get() {
            if (cameraProviderLiveData != null) return cameraProviderLiveData!!

            cameraProviderLiveData = MutableLiveData()
            val cameraProviderFuture = ProcessCameraProvider.getInstance(getApplication())
            cameraProviderFuture.addListener(
                {
                    try {
                        cameraProviderLiveData!!.setValue(cameraProviderFuture.get())
                    } catch (_: Exception) {

                    }
                },
                ContextCompat.getMainExecutor(getApplication())
            )

            return cameraProviderLiveData!!
        }
}