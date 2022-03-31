package my

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime

class SocketIO(
    private val port: Int,
    private val onError: ((String)->Unit)? = null
) {
    private var server: ServerSocket? = null
    private var client: Socket? = null
    private var job: Job? = null
    private val jobs = mutableMapOf<Socket,Job>()

    private var listenState = mutableStateOf(false)
    val listening: Boolean get() = listenState.value

    private var connectState = mutableStateOf(false)
    val connected: Boolean get() = connectState.value

    fun listen(onReceive: SocketChat.(String)->Unit) = if (job != null)
        onError?.invoke("job is busy")
    else CoroutineScope(Dispatchers.IO).launch {
        try {
            server = withContext(Dispatchers.IO) { ServerSocket(port) }
            println("Server is running on port ${server?.localPort}")
        } catch (ex: Exception) {
            onError?.invoke(ex.localizedMessage)
            job = null
            return@launch
        }
        server?.run {
            listenState.value = true
            while (true) {
                val client = try {
                    accept()
                } catch (ex: Exception) {
                    break
                }
                println("Client connected: ${client.inetAddress.hostAddress}")
                jobs[client] = CoroutineScope(Dispatchers.IO).launch {
                    run(client, onReceive)
                    withContext(Dispatchers.IO) { client.close() }
                    jobs.remove(client)
                    println("Client disconnected")
                }
                delay(100)
            }
            listenState.value = false
        }
    }.let { job = it }

    fun stop() = CoroutineScope(Dispatchers.IO).launch {
        jobs.toMap().forEach { (client, job) ->
            client.close()
            job.cancel()
            job.join()
            print("[x] ")
        }
        jobs.clear()
        withContext(Dispatchers.IO) { server?.close() }
        server = null
        job?.cancel()
        job?.join()
        job = null
        println("Server stopped")
    }

    fun connect(address: String, onReceive: SocketChat.(String)->Unit) = if (job != null)
        onError?.invoke("job is busy")
    else CoroutineScope(Dispatchers.IO).launch {
        try {
            client = withContext(Dispatchers.IO) { Socket(address, port) }
            println("Connected to server at $address on port $port")
        } catch (ex: Exception) {
            onError?.invoke(ex.localizedMessage)
            job = null
            return@launch
        }
        connectState.value = true
        client?.let {
            run(it, onReceive)
        }
        connectState.value = false
        withContext(Dispatchers.IO) { client?.close() }
        client = null
        job = null
        println("Disconnected from server")
    }.let { job = it }

    fun disconnect() = CoroutineScope(Dispatchers.IO).launch {
        connectState.value = false
        withContext(Dispatchers.IO) { client?.close() }
        client = null
        job?.cancel()
        job?.join()
        job = null
        println("Disconnected")
    }

    private fun disconnect(socketChatKey: String) = jobs.keys.firstOrNull { it.toString() == socketChatKey } ?.let { client ->
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) { client.close() }
            jobs[client]?.cancel()
            jobs[client]?.join()
            jobs.remove(client)
            println("Disconnected by admin")
        }
    }

    fun dispose() = CoroutineScope(Dispatchers.IO).launch {
        if (connected) disconnect().join()
        if (listening) stop().join()
    }

    private suspend fun run(connection: Socket, onReceive: SocketChat.(String)->Unit) = withContext(Dispatchers.IO) {
        val reader = connection.getInputStream().bufferedReader()
        val chat = SocketChat(connection, onError, ::disconnect)
        chat.onReceive("connected")
        while (true) {
            try {
                val message = reader.readLine()
                chat.onReceive(message)
            } catch (ex: Exception) {
                break
            }
            delay(10)
        }
        chat.connectState.value = false
        chat.onReceive("disconnected")
    }

    class SocketChat(
        connection: Socket,
        private val onError: ((String) -> Unit)?,
        private val disconnect: (String)->Unit
    ) {
        val key = connection.toString()
        val address: String? = connection.inetAddress.hostAddress
        private val writer = connection.getOutputStream().bufferedWriter()
        internal var connectState = mutableStateOf(true)
        val connected: Boolean get() = connectState.value
        val created = LocalDateTime.now()
        fun send(message: String) {
            if (connected) try {
                writer.write(message)
                writer.newLine()
                writer.flush()
            } catch (ex: Exception) {
                onError?.invoke(ex.localizedMessage)
            }
        }
        fun moderatorDisconnect() {
            if (connected) disconnect(key)
            connectState.value = false
        }
        override fun toString() = key
    }
}