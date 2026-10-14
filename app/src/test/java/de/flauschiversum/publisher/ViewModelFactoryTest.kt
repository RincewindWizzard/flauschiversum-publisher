package de.flauschiversum.publisher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import de.flauschiversum.publisher.ui.AppViewModelFactory
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.settings.SettingsViewModel
import de.flauschiversum.publisher.ui.setup.SetupViewModel
import de.flauschiversum.publisher.ui.share.ShareViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die App erzeugt ViewModels per Reflexion mit genau einem Parameter (dem Container). Ein zusätzlicher
 * Parameter mit Standardwert würde diesen Konstruktor verschwinden lassen und die App beim Start abstürzen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ViewModelFactoryTest {
    private val factory get() = AppViewModelFactory(ApplicationProvider.getApplicationContext<PublisherApp>().container)

    private fun <T : ViewModel> create(c: Class<T>): T = factory.create(c, CreationExtras.Empty)

    @Test fun alleViewModelsDerFactoryLassenSichErzeugen() {
        assertNotNull(create(HomeViewModel::class.java))
        assertNotNull(create(SetupViewModel::class.java))
        assertNotNull(create(SettingsViewModel::class.java))
        assertNotNull(create(ShareViewModel::class.java))
    }
}
