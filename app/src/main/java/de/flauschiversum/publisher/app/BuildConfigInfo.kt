package de.flauschiversum.publisher.app

import de.flauschiversum.publisher.BuildConfig

/** Versionsangaben für Protokoll und „Über“. */
object BuildConfigInfo {
    val version: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_ID})"
}
