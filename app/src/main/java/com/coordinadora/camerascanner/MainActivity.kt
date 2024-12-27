package com.coordinadora.camerascanner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.coordinadora.camerascannerlibrary.main.CameraDialogFragment

class MainActivity : AppCompatActivity(), CameraDialogFragment.UnitProvider,
    CameraDialogFragment.DialogListener {

    private var mCameraDialogFragment: CameraDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (mCameraDialogFragment == null) {
            mCameraDialogFragment = CameraDialogFragment()
            mCameraDialogFragment?.show(supportFragmentManager, "CameraDialogFragmentTag")
        }
    }

    /**
     * Devuelve la lista de unidades asignables
     * @return Lista de unidades asignables
     */
    override fun getUnits(): List<CameraDialogFragment.ScannableUnit> {
        val listaDeUnidadesAsig = ArrayList<CameraDialogFragment.ScannableUnit>()

        listaDeUnidadesAsig.add(UnidadAsig("73940671083001", "2D"))

        return listaDeUnidadesAsig
    }

    /**
     * Configura el icono del scanner
     */
    override fun setupScannerDrawable() {
        // Configurar el icono del scanner
    }

    /**
     * Muestra un mensaje al usuario cuando se scanee una unidad
     */
    override fun validateScanning(scanning: String) {
        Toast.makeText(this, scanning, Toast.LENGTH_SHORT).show()
    }

    /**
     * Clase de ejemplo para unidades asignables
     */
    class UnidadAsig(val etiqueta1d: String, val etiqueta2d: String) :
        CameraDialogFragment.ScannableUnit {
        override fun hasCode(code: String): Boolean = etiqueta1d == code || etiqueta2d == code
    }
}