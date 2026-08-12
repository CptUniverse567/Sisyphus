package org.sisyphus.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.sisyphus.android.ui.theme.sisyphusTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChallengeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            sisyphusTheme {
                val state by viewModel.state.collectAsState()
                sisyphusUi(ui = viewModel, state = state)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        viewModel.resumeTracking()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopTracking()
    }
}
