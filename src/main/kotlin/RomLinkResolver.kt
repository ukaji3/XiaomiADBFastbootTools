import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object RomLinkResolver {

    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    private fun getLocation(codename: String, ending: String, region: String): String? {
        val request = HttpRequest.newBuilder(URI("http://update.miui.com/updates/v1/fullromdownload.php?d=$codename$ending&b=F&r=$region&n="))
            .header("Referer", "http://en.miui.com/a-234.html").GET().build()
        return try {
            client.send(request, HttpResponse.BodyHandlers.discarding())
                .headers().firstValue("Location").orElse(null)
        } catch (e: Exception) { null }
    }

    fun getLink(version: String, codename: String): String? = when (version) {
        "China Stable" -> getLocation(codename, "", "cn")
        "EEA Stable" -> getLocation(codename, "_eea_global", "eea")
        "Russia Stable" -> {
            arrayOf("ru", "global").map { getLocation(codename, "_ru_global", it) }
                .firstOrNull { it != null && "bigota" in it }
        }
        "Indonesia Stable" -> getLocation(codename, "_id_global", "global")
        "India Stable" -> {
            val endings = arrayOf("_in_global", "_india_global", "_global")
            arrayOf("in", "global").flatMap { region ->
                endings.takeWhile { !(region == "global" && it == "_global") }
                    .map { getLocation(codename, it, region) }
            }.firstOrNull { it != null && "bigota" in it }
        }
        else -> getLocation(codename, "_global", "global")
    }
}
