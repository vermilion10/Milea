package com.github.vermilion10.milea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.vermilion10.milea.data.repository.AppearanceSettings
import com.github.vermilion10.milea.data.repository.CurrencySettings
import com.github.vermilion10.milea.data.repository.SettingsRepository
import com.github.vermilion10.milea.ui.navigation.MileaNavigation
import com.github.vermilion10.milea.ui.theme.MileaTheme
import com.github.vermilion10.milea.util.ConsumptionUnit
import com.github.vermilion10.milea.util.LocalConsumptionUnit
import com.github.vermilion10.milea.util.LocalMoney
import com.github.vermilion10.milea.util.MoneyFormat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by settingsRepository.appearance
                .collectAsStateWithLifecycle(AppearanceSettings())
            val currency by settingsRepository.currency
                .collectAsStateWithLifecycle(CurrencySettings())
            val money = remember(currency) { MoneyFormat(currency) }
            val consumptionUnit by settingsRepository.consumptionUnit
                .collectAsStateWithLifecycle(ConsumptionUnit.AUTO)

            MileaTheme(themeMode = appearance.themeMode, dynamicColor = appearance.dynamicColor) {
                CompositionLocalProvider(
                    LocalMoney provides money,
                    LocalConsumptionUnit provides consumptionUnit
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MileaNavigation()
                    }
                }
            }
        }
    }
}
