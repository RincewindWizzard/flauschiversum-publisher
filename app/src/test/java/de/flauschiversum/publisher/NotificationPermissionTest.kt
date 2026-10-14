package de.flauschiversum.publisher

import androidx.fragment.app.FragmentActivity
import de.flauschiversum.publisher.ui.NotificationPermission
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Die Anfrage darf eine `FragmentActivity` nicht zum Absturz bringen ("Can only use lower 16 bits for requestCode"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationPermissionTest {
    @Test fun anfrageAufFragmentActivityStuerztNichtAb() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume().get()
        NotificationPermission.request(activity)
    }

    @Test fun launcherCodeWuerdeAbgelehntWerden() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume().get()
        val e = runCatching { androidx.core.app.ActivityCompat.requestPermissions(activity, arrayOf("x"), 0x10000) }.exceptionOrNull()
        org.junit.Assert.assertTrue(e is IllegalArgumentException)
    }
}
