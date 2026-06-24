package com.rama.blelab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rama.blelab.data.repository.AndroidBluetoothRepository
import com.rama.blelab.data.repository.FormulaImporter
import com.rama.blelab.data.repository.MacroDataStore
import com.rama.blelab.domain.repository.BluetoothRepository
import com.rama.blelab.domain.usecase.*
import com.rama.blelab.presentation.scanner.ScannerScreen
import com.rama.blelab.presentation.scanner.ScannerViewModel
import com.rama.blelab.presentation.terminal.TerminalScreen
import com.rama.blelab.presentation.terminal.TerminalViewModel
import com.rama.blelab.ui.theme.BleLabTheme

class MainActivity : ComponentActivity() {

    private val repository: BluetoothRepository by lazy {
        AndroidBluetoothRepository(applicationContext)
    }

    private val macroDataStore by lazy {
        MacroDataStore(applicationContext)
    }

    private val formulaImporter by lazy {
        FormulaImporter(applicationContext)
    }

    private val useCases by lazy {
        BleUseCases(
            startScan = StartScanUseCase(repository),
            stopScan = StopScanUseCase(repository),
            connectToDevice = ConnectToDeviceUseCase(repository),
            disconnect = DisconnectUseCase(repository),
            sendMessage = SendMessageUseCase(repository)
        )
    }

    private val scannerViewModel: ScannerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScannerViewModel(repository, useCases) as T
            }
        }
    }

    private val terminalViewModel: TerminalViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TerminalViewModel(repository, useCases, macroDataStore) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BleLabTheme {
                BleAppNavigation(scannerViewModel, terminalViewModel, formulaImporter)
            }
        }
    }
}

@Composable
fun BleAppNavigation(
    scannerViewModel: ScannerViewModel,
    terminalViewModel: TerminalViewModel,
    formulaImporter: FormulaImporter
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "scanner") {
        composable("scanner") {
            ScannerScreen(
                viewModel = scannerViewModel,
                onDeviceClick = { device ->
                    scannerViewModel.connect(device.address)
                    navController.navigate("terminal")
                }
            )
        }
        composable("terminal") {
            TerminalScreen(
                viewModel = terminalViewModel,
                formulaImporter = formulaImporter,
                onBack = {
                    terminalViewModel.disconnect()
                    navController.popBackStack()
                }
            )
        }
    }
}
