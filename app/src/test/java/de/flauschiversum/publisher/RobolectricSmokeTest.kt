package de.flauschiversum.publisher

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RobolectricSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun composeUiLaeuftAufDerJvm() {
        compose.setContent { FlauschiversumPublisherTheme { Text("Hallo Flauschi") } }
        compose.onNodeWithText("Hallo Flauschi").assertExists()
    }
}
