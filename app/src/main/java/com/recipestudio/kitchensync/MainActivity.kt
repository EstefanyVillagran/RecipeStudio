package com.recipestudio.kitchensync

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.recipestudio.kitchensync.data.remote.FirebaseSoporte
import com.recipestudio.kitchensync.ui.AppRoot
import com.recipestudio.kitchensync.ui.theme.RecipeStudioTheme

// FragmentActivity (no ComponentActivity): BiometricPrompt lo exige para mostrar el diálogo de huella/rostro.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseSoporte.iniciar(applicationContext)
        setContent {
            RecipeStudioTheme {
                AppRoot()
            }
        }
    }
}
