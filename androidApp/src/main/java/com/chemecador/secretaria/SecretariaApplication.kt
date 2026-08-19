package com.chemecador.secretaria

import android.app.Application
import com.google.firebase.FirebaseApp

class SecretariaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppCheckProviderInstaller.install()
    }
}
