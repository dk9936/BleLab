package com.rama.blelab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.rama.blelab.presentation.home.HomeScreen
import com.rama.blelab.presentation.router.RouterScannerScreen
import com.rama.blelab.presentation.router.RouterScannerViewModel
import com.rama.blelab.presentation.scanner.DeviceDetailsScreen
import com.rama.blelab.presentation.scanner.ScannerScreen
import com.rama.blelab.presentation.scanner.ScannerViewModel
import com.rama.blelab.presentation.terminal.TerminalScreen
import com.rama.blelab.presentation.terminal.TerminalViewModel
import com.rama.blelab.presentation.websocket.WebSocketScreen
import com.rama.blelab.presentation.websocket.WebSocketViewModel
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
            discoverGattDetails = DiscoverGattDetailsUseCase(repository),
            clearGattDetails = ClearGattDetailsUseCase(repository),
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

    private val webSocketViewModel: WebSocketViewModel by viewModels()

    private val routerScannerViewModel: RouterScannerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RouterScannerViewModel(applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BleLabTheme {
                BleAppNavigation(
                    scannerViewModel = scannerViewModel,
                    terminalViewModel = terminalViewModel,
                    webSocketViewModel = webSocketViewModel,
                    routerScannerViewModel = routerScannerViewModel,
                    formulaImporter = formulaImporter
                )
            }
        }
    }
}

@Composable
fun BleAppNavigation(
    scannerViewModel: ScannerViewModel,
    terminalViewModel: TerminalViewModel,
    webSocketViewModel: WebSocketViewModel,
    routerScannerViewModel: RouterScannerViewModel,
    formulaImporter: FormulaImporter
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onBleLabClick = { navController.navigate("scanner") },
                onWebSocketLabClick = { navController.navigate("webSocket") },
                onRouterScannerClick = { navController.navigate("routerScanner") }
            )
        }
        composable("scanner") {
            ScannerScreen(
                viewModel = scannerViewModel,
                onDeviceClick = { device ->
                    scannerViewModel.selectDevice(device)
                    navController.navigate("deviceDetails")
                },
                onConnectClick = { device ->
                    scannerViewModel.connect(device.address)
                    navController.navigate("terminal")
                }
            )
        }
        composable("deviceDetails") {
            val selectedDevice by scannerViewModel.selectedDevice.collectAsState()
            val gattDetailsState by scannerViewModel.gattDetailsState.collectAsState()
            DeviceDetailsScreen(
                device = selectedDevice,
                gattDetailsState = gattDetailsState,
                onBack = { navController.popBackStack() },
                onDiscoverGattDetails = scannerViewModel::discoverGattDetails,
                onConnect = { device ->
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
        composable("webSocket") {
            WebSocketScreen(
                viewModel = webSocketViewModel,
                onBack = {
                    webSocketViewModel.disconnect()
                    navController.popBackStack()
                }
            )
        }
        composable("routerScanner") {
            RouterScannerScreen(
                viewModel = routerScannerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
