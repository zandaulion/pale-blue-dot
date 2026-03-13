package com.globe.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var globeView: GlobeSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while the app is visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        globeView = GlobeSurfaceView(this)
        setContentView(globeView)
    }

    override fun onResume() {
        super.onResume()
        globeView.onResume()
    }

    override fun onPause() {
        super.onPause()
        globeView.onPause()
    }
}
