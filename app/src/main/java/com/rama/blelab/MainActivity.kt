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
import com.rama.blelab.data.repository.ScannerProfileDataStore
import com.rama.blelab.domain.repository.BluetoothRepository
import com.rama.blelab.domain.usecase.*
import com.rama.blelab.presentation.esp.EspTesterScreen
import com.rama.blelab.presentation.esp.EspTesterViewModel
import com.rama.blelab.presentation.explorer.NetworkExplorerScreen
import com.rama.blelab.presentation.explorer.NetworkExplorerViewModel
import com.rama.blelab.presentation.home.HomeScreen
import com.rama.blelab.presentation.mqtt.MqttTesterScreen
import com.rama.blelab.presentation.mqtt.MqttTesterViewModel
import com.rama.blelab.presentation.network.NetworkInfoScreen
import com.rama.blelab.presentation.network.NetworkInfoViewModel
import com.rama.blelab.presentation.router.RouterDetailsScreen
import com.rama.blelab.presentation.router.RouterScannerScreen
import com.rama.blelab.presentation.router.RouterScannerViewModel
import com.rama.blelab.presentation.router.RouterToolsScreen
import com.rama.blelab.presentation.router.SpeedGraphScreen
import com.rama.blelab.presentation.scanner.DeviceDetailsScreen
import com.rama.blelab.presentation.scanner.ScannerRadarScreen
import com.rama.blelab.presentation.scanner.ScannerScreen
import com.rama.blelab.presentation.scanner.ScannerViewModel
import com.rama.blelab.presentation.storage.StorageInfoScreen
import com.rama.blelab.presentation.storage.StorageInfoViewModel
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

    private val scannerProfileDataStore by lazy {
        ScannerProfileDataStore(applicationContext)
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
                return ScannerViewModel(repository, useCases, scannerProfileDataStore) as T
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
    private val mqttTesterViewModel: MqttTesterViewModel by viewModels()
    private val networkExplorerViewModel: NetworkExplorerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NetworkExplorerViewModel(applicationContext) as T
            }
        }
    }
    private val networkInfoViewModel: NetworkInfoViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NetworkInfoViewModel(applicationContext) as T
            }
        }
    }
    private val storageInfoViewModel: StorageInfoViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StorageInfoViewModel(applicationContext) as T
            }
        }
    }

    private val espTesterViewModel: EspTesterViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EspTesterViewModel(applicationContext) as T
            }
        }
    }

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
                    mqttTesterViewModel = mqttTesterViewModel,
                    networkExplorerViewModel = networkExplorerViewModel,
                    networkInfoViewModel = networkInfoViewModel,
                    storageInfoViewModel = storageInfoViewModel,
                    espTesterViewModel = espTesterViewModel,
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
    mqttTesterViewModel: MqttTesterViewModel,
    networkExplorerViewModel: NetworkExplorerViewModel,
    networkInfoViewModel: NetworkInfoViewModel,
    storageInfoViewModel: StorageInfoViewModel,
    espTesterViewModel: EspTesterViewModel,
    routerScannerViewModel: RouterScannerViewModel,
    formulaImporter: FormulaImporter
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onBleLabClick = { navController.navigate("scanner") },
                onNetworkExplorerClick = { navController.navigate("networkExplorer") },
                onWebSocketLabClick = { navController.navigate("webSocket") },
                onMqttTesterClick = { navController.navigate("mqttTester") },
                onNetworkInfoClick = { navController.navigate("networkInfo") },
                onStorageInfoClick = { navController.navigate("storageInfo") },
                onRouterScannerClick = { navController.navigate("routerScanner") },
                onEspTesterClick = { navController.navigate("espTester") }
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
                },
                onRadarClick = { navController.navigate("scannerRadar") }
            )
        }
        composable("networkExplorer") {
            NetworkExplorerScreen(
                viewModel = networkExplorerViewModel,
                onBack = {
                    networkExplorerViewModel.stopScan()
                    navController.popBackStack()
                }
            )
        }
        composable("scannerRadar") {
            ScannerRadarScreen(
                viewModel = scannerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("deviceDetails") {
            val selectedDevice by scannerViewModel.selectedDevice.collectAsState()
            val gattDetailsState by scannerViewModel.gattDetailsState.collectAsState()
            val profiles by scannerViewModel.profiles.collectAsState()
            val rssiHistory by scannerViewModel.rssiHistory.collectAsState()
            DeviceDetailsScreen(
                device = selectedDevice,
                profile = selectedDevice?.address?.let { profiles[it] },
                history = selectedDevice?.address?.let { rssiHistory[it] }.orEmpty(),
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
        composable("mqttTester") {
            MqttTesterScreen(
                viewModel = mqttTesterViewModel,
                onBack = {
                    mqttTesterViewModel.disconnect()
                    navController.popBackStack()
                }
            )
        }
        composable("networkInfo") {
            NetworkInfoScreen(
                viewModel = networkInfoViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("storageInfo") {
            StorageInfoScreen(
                viewModel = storageInfoViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("espTester") {
            EspTesterScreen(
                viewModel = espTesterViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("routerScanner") {
            RouterScannerScreen(
                viewModel = routerScannerViewModel,
                onBack = { navController.popBackStack() },
                onRouterClick = { router ->
                    routerScannerViewModel.selectRouter(router)
                    navController.navigate("routerDetails")
                }
            )
        }
        composable("routerDetails") {
            val selectedRouter by routerScannerViewModel.selectedRouter.collectAsState()
            RouterDetailsScreen(
                router = selectedRouter,
                onBack = { navController.popBackStack() },
                onOpenConnectedTools = { navController.navigate("routerTools") }
            )
        }
        composable("routerTools") {
            RouterToolsScreen(
                viewModel = routerScannerViewModel,
                onBack = { navController.popBackStack() },
                onOpenSpeedGraph = { navController.navigate("speedGraph") }
            )
        }
        composable("speedGraph") {
            SpeedGraphScreen(
                viewModel = routerScannerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
