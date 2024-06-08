package com.parkhub.printerapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun ConnectPrinterScreen(viewModel: ConnectPrinterViewModel = hiltViewModel()) {
    val state by viewModel.collectAsState()

    Scaffold {
        Column(
            modifier = Modifier.padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.padding(vertical = 24.dp))
            if (state.errorMessage.isNotEmpty()) {
                Text(
                    modifier = Modifier,
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (state.connectedPrinter != null) {
                    if (state.disconnecting) {
                        Text(text = "Disconnecting")
                    } else {
                        TextButton(onClick = { viewModel.disconnect() }) {
                            Text(text = "Disconnect")
                        }
                    }
                } else {
                    TextButton(onClick = { viewModel.searchPrinter() }) {
                        Text(text = "Search Printers")
                    }
                }
            }

            if (state.connectingSerial != null) {
                Text(text = "Connecting to ${state.connectingSerial}")
            } else if (state.connectedPrinter == null) {
                Spacer(modifier = Modifier.height(24.dp))
                LazyColumn {
                    items(state.results.toList(), key = { address -> address }) { printer ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { viewModel.connectPrinter(printer) },
                        ) {
                            Text(
                                modifier = Modifier.padding(8.dp),
                                text = printer.friendlyName,
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                if (state.searching) {
                    CircularProgressIndicator()
                }
            }
            if (state.searching) {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = { viewModel.cancelSearch() }) {
                    Text(text = "Cancel Search")
                }
            }
            if (state.connectedPrinter != null && !state.disconnecting) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Connected")
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = { viewModel.printTestPage() }) {
                    Text(text = "Print Test Page")
                }
            }
        }
    }
}
