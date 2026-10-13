package de.flauschiversum.publisher.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import de.flauschiversum.publisher.git.NetworkInfo

/** Netzstatus über den ConnectivityManager. */
class AndroidNetworkState(context: Context) : NetworkInfo {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Mobilfunk oder eine andere getaktete Verbindung (Datenvolumen zählt). */
    override fun isMetered(): Boolean = cm.isActiveNetworkMetered
}
