package io.github.cqusurvive.app.data.official

import android.util.Log
import io.github.cqusurvive.app.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A process-local, allowlisted HTTP CONNECT proxy.
 *
 * It only selects the destination IP; TLS remains end-to-end between WebView and
 * the official host, so certificate and hostname verification are not weakened.
 */
internal class ScopedConnectProxy : AutoCloseable {
    private val fixedAddresses = mapOf(
        "my.cqu.edu.cn" to "202.202.2.6",
        "sso.cqu.edu.cn" to "202.202.9.246",
    )
    private val allowedHosts = fixedAddresses.keys
    private val running = AtomicBoolean(true)
    private val workers: ExecutorService = Executors.newCachedThreadPool()
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    }

    val port: Int get() = server.localPort

    init {
        workers.execute {
            while (running.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                workers.execute { handle(client) }
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { local ->
            local.soTimeout = 15_000
            val input = BufferedInputStream(local.getInputStream())
            val output = BufferedOutputStream(local.getOutputStream())
            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(' ')
            if (BuildConfig.DEBUG) Log.d("ReCQU.Proxy", "request method=${parts.firstOrNull() ?: "unknown"}")
            while (true) {
                val line = readAsciiLine(input) ?: return
                if (line.isEmpty()) break
            }

            if (parts.size < 3 || parts[0] != "CONNECT") {
                reject(output, 405, "Method Not Allowed")
                return
            }
            val authority = parts[1]
            val separator = authority.lastIndexOf(':')
            if (separator <= 0) {
                reject(output, 403, "Forbidden")
                return
            }
            val host = authority.take(separator).lowercase()
            val port = authority.drop(separator + 1).toIntOrNull()
            if (BuildConfig.DEBUG) Log.d("ReCQU.Proxy", "connect host=$host port=$port")
            if (host !in allowedHosts || port != 443) {
                reject(output, 403, "Forbidden")
                return
            }

            val address = fixedAddresses[host]?.let(InetAddress::getByName)
                ?: InetAddress.getAllByName(host).firstOrNull()
                ?: run {
                    reject(output, 502, "Bad Gateway")
                    return
                }
            val upstream = Socket()
            try {
                upstream.connect(InetSocketAddress(address, port), 10_000)
                upstream.soTimeout = 30_000
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII))
                output.flush()
                relay(local, input, output, upstream)
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) Log.w("ReCQU.Proxy", "upstream host=$host error=${error.javaClass.simpleName}")
                runCatching { reject(output, 502, "Bad Gateway") }
            } finally {
                upstream.close()
            }
        }
    }

    private fun relay(
        client: Socket,
        clientInput: BufferedInputStream,
        clientOutput: BufferedOutputStream,
        upstream: Socket,
    ) {
        val upstreamInput = BufferedInputStream(upstream.getInputStream())
        val upstreamOutput = BufferedOutputStream(upstream.getOutputStream())
        val upload = workers.submit {
            runCatching { copy(clientInput, upstreamOutput) }
            runCatching { upstream.shutdownOutput() }
        }
        runCatching { copy(upstreamInput, clientOutput) }
        runCatching { client.shutdownOutput() }
        upload.cancel(true)
    }

    private fun copy(input: BufferedInputStream, output: BufferedOutputStream) {
        val buffer = ByteArray(32 * 1024)
        while (running.get()) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            output.flush()
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size < 8 * 1024) {
            val value = input.read()
            if (value < 0) return null
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }

    private fun reject(output: BufferedOutputStream, status: Int, message: String) {
        output.write("HTTP/1.1 $status $message\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server.close() }
        workers.shutdownNow()
    }
}
