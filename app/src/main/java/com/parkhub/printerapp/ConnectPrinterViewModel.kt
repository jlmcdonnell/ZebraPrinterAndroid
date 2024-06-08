@file:SuppressLint("MissingPermission")

package com.parkhub.printerapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import com.parkhub.printerapp.ConnectPrinterViewModel.SideEffect
import com.parkhub.printerapp.ConnectPrinterViewModel.State
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.printer.ZebraPrinterFactory
import com.zebra.sdk.printer.ZebraPrinterLinkOs
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.time.withTimeoutOrNull
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.reduce
import org.orbitmvi.orbit.viewmodel.container
import java.time.Duration
import javax.inject.Inject
import kotlin.coroutines.resume

@HiltViewModel
class ConnectPrinterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel(),
    ContainerHost<State, SideEffect> {

    override val container = container<State, SideEffect>(State())

    private var searchJob: Job? = null
    private var connectJob: Job? = null
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

    fun cancelSearch() {
        intent {
            bluetoothManager.adapter.cancelDiscovery()
        }
    }

    fun searchPrinter() {
        println("Searching printers")
        intent {
            reduce {
                state.copy(
                    connectingSerial = null,
                    searching = true,
                    results = emptySet(),
                )
            }

            searchJob?.cancel()

            searchJob = CoroutineScope(Dispatchers.IO).launch {
                suspendCancellableCoroutine { continuation ->
                    println("findPrinters")
                    BluetoothDiscoverer.findPrinters(
                        context.applicationContext,
                        object : DiscoveryHandler {
                            override fun foundPrinter(printer: DiscoveredPrinter) {
                                println("foundPrinter: ${printer.address}")
                                intent {
                                    val address = printer.address
                                    if (address != null) {
                                        reduce {
                                            state.copy(results = state.results + printer as DiscoveredPrinterBluetooth)
                                        }
                                    } else {
                                        println("Found printer with no address")
                                    }
                                }
                            }

                            override fun discoveryFinished() {
                                println("discoveryFinished")
                                intent {
                                    reduce {
                                        state.copy(
                                            searching = false,
                                        )
                                    }
                                }
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }

                            override fun discoveryError(message: String) {
                                println("discoveryError: $message")
                                intent {
                                    reduce {
                                        state.copy(
                                            errorMessage = "Searching printers: $message",
                                            searching = false,
                                        )
                                    }
                                }
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }
                        },
                    )
                }
            }
            searchJob!!.invokeOnCompletion {
                println("Cancelling discovery")
                bluetoothManager.adapter.cancelDiscovery()
            }
        }
    }

    fun connectPrinter(printer: DiscoveredPrinterBluetooth) {
        intent {
            reduce {
                state.copy(
                    connectingSerial = printer.friendlyName,
                    searching = false,
                )
            }
            connectJob?.cancel()
            connectJob = CoroutineScope(Dispatchers.IO).launch {
                withTimeoutOrNull(Duration.ofSeconds(10)) {
                    val connection = BluetoothConnection(printer.address, BT_READ_TIMEOUT_MILLIS, BT_WAIT_TIMEOUT_MILLIS)
                    while (isActive) {
                        try {
                            connection.open()
                            println("Connection open, creating ZebraPrinter")
                            val zebraPrinter = ZebraPrinterFactory.getInstance(connection)
                            val zebraPrinterLinkOs = ZebraPrinterFactory.createLinkOsPrinter(zebraPrinter)
                            println("Created ZebraPrinter")
                            reduce {
                                state.copy(
                                    connectedPrinter = zebraPrinterLinkOs,
                                    connectingSerial = null,
                                )
                            }
                            break
                        } catch (e: Exception) {
                            e.printStackTrace()
                            delay(CONNECT_RETRY_DELAY_MILLIS)
                        }
                    }
                }
                if (state.connectedPrinter == null) {
                    reduce {
                        state.copy(errorMessage = "Failed to connect to printer")
                    }
                }
            }
        }
    }

    fun disconnect() {
        intent {
            reduce { state.copy(disconnecting = true) }
            state.connectedPrinter?.let { printer ->
                runCatching {
                    printer.connection.close()
                    reduce { state.copy(connectedPrinter = null) }
                }.onFailure {
                    println("Error closing connection: ${it.stackTraceToString()}")
                    reduce { state.copy(errorMessage = "Error closing connection: ${it.message}") }
                }
            } ?: run {
                println("No connected printer")
            }
            reduce { state.copy(disconnecting = false) }
        }
    }

    fun printTestPage() {
        intent {
            state.connectedPrinter?.printStoredFormat(TEST_PAGE_ZPL, emptyArray()) ?: run {
                println("No connected printer")
            }
        }
    }

    data class State(
        val searching: Boolean = false,
        val connectingSerial: String? = null,
        val results: Set<DiscoveredPrinterBluetooth> = emptySet(),
        val connectedPrinter: ZebraPrinterLinkOs? = null,
        val errorMessage: String = "",
        val disconnecting: Boolean = false,
    )

    sealed interface SideEffect

    companion object {
        private const val CONNECT_RETRY_DELAY_MILLIS = 1000L
        private const val BT_READ_TIMEOUT_MILLIS = 5000
        private const val BT_WAIT_TIMEOUT_MILLIS = 500

        private const val TEST_PAGE_ZPL = """
            ^XA^PON^PW400^MNN^LL530^LH0, 0 
            ^FO10, 220 
            ^A0, N, 25, 25 
            ^FD PRINTER TEST PAGE ^ FS  
            ^FO10, 260 
            ^A0, N, 25, 25 
            ^FD Attendant name: Attendant ^FS 
            ^FO10, 300 
            ^A0, N, 25, 25 
            ^FD Phone name: Pax A6650 ^FS 
            ^FO10, 340 
            ^A0, N, 25, 25 
            ^FD Printer serial: 123456789 ^FS 
            ^FS^LL50^XZ
        """
    }
}
