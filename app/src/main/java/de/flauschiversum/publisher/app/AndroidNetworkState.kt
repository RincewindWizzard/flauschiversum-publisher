package de.flauschiversum.publisher.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import de.flauschiversum.publisher.git.NetworkState

/** Netzstatus über den ConnectivityManager. */
class AndroidNetworkState(context: Context) : NetworkState {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Mobilfunk oder eine andere getaktete Verbindung (Datenvolumen zählt). */
    fun isMetered(): Boolean = cm.isActiveNetworkMetered
}
