import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object RomLinkResolver {

    private fun getLocation(codename: String, ending: String, region: String): String? {
        (URL("http://update.miui.com/updates/v1/fullromdownload.php?d=$codename$ending&b=F&r=$region&n=").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Referer", "http://en.miui.com/a-234.html")
            instanceFollowRedirects = false
            try {
                connect()
                disconnect()
            } catch (e: IOException) {
                return null
            }
            return getHeaderField("Location")
        }
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
