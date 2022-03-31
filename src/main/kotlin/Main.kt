// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import my.SocketIO
import java.time.format.DateTimeFormatter

@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberScaffoldState()
    var host by remember { mutableStateOf("localhost") }
    val formatter = remember { DateTimeFormatter.ofPattern("yy-MM-dd/HH:mm:ss") }
    val messages = remember { mutableStateListOf<Pair<SocketIO.SocketChat,String>>() }
    val chats by remember { derivedStateOf { messages.map { it.first }.toSet().toList() } }
    val activeChats by remember { derivedStateOf { chats.filter { it.connected } } }
    //var chat by remember { mutableStateOf<SocketIO.SocketChat?>(null) }
    //val chatMessages by remember { derivedStateOf { messages.filter { it.first == chat }.map { it.second } } }

    val onError: (String)->Unit = { error ->
        scope.launch {
            scaffoldState.snackbarHostState.showSnackbar(error)
        }
    }

    val io = remember { SocketIO(9999, onError) }

    val onReceive: SocketIO.SocketChat.(String)->Unit = { message ->
        println("$key from $address ($connected): $message")
        messages.add(this to message)
    }

    Scaffold(scaffoldState = scaffoldState) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!io.connected) {
                    if (io.listening)
                        Button({ io.stop() }) { Text("Не слушать") }
                    else
                        Button({ io.listen(onReceive) }) { Text("Слушать") }
                }
                Spacer(Modifier.weight(1f))
                if (!io.listening) {
                    if (io.connected)
                        Button({ io.disconnect() }) { Text("Отключиться") }
                    else
                        Button({ io.connect(host, onReceive) }) {
                            BasicTextField(
                                host, { host = it }, Modifier.background(MaterialTheme.colors.background),
                                textStyle = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center)
                            )
                            Text("Подключиться", Modifier.padding(start = 10.dp))
                        }
                }
            }

            Row(Modifier.fillMaxSize()) {
//                if (chats.size > 1) LazyColumn(Modifier.fillMaxHeight()) {
//                    items(chats) { chat ->
//                        Text(
//                            chat.address + "/" + chat.created.format(formatter),
//                            color = if (chat.connected) MaterialTheme.colors.primaryVariant else Color.Gray
//                        )
//                    }
//                }
                activeChats.forEach { current ->
                    var message by remember { mutableStateOf("") }
                    val currentMessages by remember(current) {
                        derivedStateOf {
                            println("$current:")
                            messages.filter { it.first == current }.map { it.second }
                        }
                    }
                    Card(Modifier.weight(1f).fillMaxHeight().padding(10.dp), elevation = 10.dp) {
                        Column(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                                item {
                                    Text(current.address + "/" + current.created.format(formatter))
                                    Divider()
                                }
                                items(currentMessages) {
                                    Text(it)
                                    Divider()
                                }
                            }
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                                OutlinedTextField(message, { message = it }, Modifier.weight(1f))
                                Button({
                                    if (message.isNotEmpty()) current.send(message)
                                    message = ""
                                }, Modifier.fillMaxHeight()) { Text("Отправить") }
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            io.dispose()
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
