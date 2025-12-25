package com.rsinkwitz.notif_snd_assist

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DialogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Diese Activity wird nicht mehr benötigt, da die Einstellungen direkt geöffnet werden.
        finish()
    }
}
