import javafx.scene.control.Label
import kotlinx.coroutines.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class Downloader(link: String, private val target: File, private val progressLabel: Label) {

    private val client = HttpClient.newHttpClient()
    private val request = HttpRequest.newBuilder(URI(link)).build()
    private var totalBytes = 0L
    private var downloadedBytes = 0L
    private var startTime = 0L
    private val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes / totalBytes.toFloat()) * 100f else 0f
    private val speed: Float
        get() = downloadedBytes / ((System.currentTimeMillis() - startTime) / 1000.0f)

    suspend fun start(scope: CoroutineScope) {
        startTime = System.currentTimeMillis()
        val job = scope.launch(Dispatchers.IO) {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1)
            response.body().use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloadedBytes += n
                    }
                }
            }
        }
        while (!job.isCompleted) {
            val speed = speed / 1000f
            val progress = progress.toString().take(4)
            withContext(Dispatchers.Main) {
                progressLabel.text = if (speed < 1000f)
                    "$progress %\t${speed.toString().take(5)} KB/s"
                else "$progress %\t${(speed / 1000f).toString().take(5)} MB/s"
            }
            delay(1000)
        }
    }
}
