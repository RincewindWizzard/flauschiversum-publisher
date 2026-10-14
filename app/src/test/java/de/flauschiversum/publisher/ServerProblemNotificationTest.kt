package de.flauschiversum.publisher

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.flauschiversum.publisher.upload.UploadNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Ein Fehler des Servers beim Veröffentlichen wird als Benachrichtigung gezeigt, mit dem ganzen Text ausgeklappt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServerProblemNotificationTest {
    @Test fun fehlerWirdMitGrundUndVollemTextGezeigt() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        UploadNotifications(ctx).serverProblem("Veröffentlicht, aber der Server meldet einen Fehler", "Fehler: Inhalte enthalten Fehler", "Deployment von abc\nFehler: Inhalte enthalten Fehler\nFEHLGESCHLAGEN")
        val shadow = shadowOf(ctx.getSystemService(NotificationManager::class.java))
        val n = shadow.allNotifications.single()
        assertEquals("Veröffentlicht, aber der Server meldet einen Fehler", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Fehler: Inhalte enthalten Fehler", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("FEHLGESCHLAGEN"))
        assertEquals(NotificationManager.IMPORTANCE_HIGH, ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(UploadNotifications.PROBLEM_CHANNEL).importance)
    }
}
