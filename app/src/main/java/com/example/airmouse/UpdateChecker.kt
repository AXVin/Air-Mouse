package com.example.airmouse

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Checks GitHub Releases API for newer versions of Air Mouse and provides
 * direct APK download and release page links.
 */
object UpdateChecker {

    sealed class UpdateResult {
        data class UpdateAvailable(
            val latestTag: String,
            val currentVersion: String,
            val releaseNotes: String,
            val apkDownloadUrl: String,
            val releasePageUrl: String
        ) : UpdateResult()

        data class UpToDate(
            val currentVersion: String,
            val latestTag: String
        ) : UpdateResult()

        data class Error(val message: String) : UpdateResult()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getInstalledVersionName(context: Context): String {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "5.1"
        } catch (_: Throwable) {
            "5.1"
        }
    }

    fun checkForUpdates(
        context: Context,
        repoSlug: String,
        onResult: (UpdateResult) -> Unit
    ) {
        val currentVersion = getInstalledVersionName(context)
        val cleanSlug = repoSlug.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
            .ifEmpty { "AXVin/Air-Mouse" }

        executor.execute {
            val result = fetchLatestRelease(cleanSlug, currentVersion)
            mainHandler.post { onResult(result) }
        }
    }

    private fun fetchLatestRelease(repoSlug: String, currentVersion: String): UpdateResult {
        var connection: HttpURLConnection? = null
        return try {
            val apiUrl = URL("https://api.github.com/repos/$repoSlug/releases/latest")
            connection = (apiUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AirMouse-Android-Updater")
            }

            val code = connection.responseCode
            if (code == 404) {
                return UpdateResult.Error(
                    "No published GitHub Releases found at '$repoSlug'. " +
                        "Verify the repository owner/name in Settings or run the GitHub Action to publish a release."
                )
            }
            if (code !in 200..299) {
                return UpdateResult.Error("GitHub API returned HTTP $code for '$repoSlug'.")
            }

            val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }
            val json = JSONObject(responseText)
            val tagName = json.optString("tag_name", "").ifEmpty { json.optString("name", "") }
            val htmlUrl = json.optString("html_url", "https://github.com/$repoSlug/releases")
            val body = json.optString("body", "New release available on GitHub.")

            var apkUrl = htmlUrl
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotEmpty()) {
                        apkUrl = downloadUrl
                        break
                    }
                }
            }

            if (tagName.isEmpty()) {
                return UpdateResult.Error("Could not parse release tag from GitHub response.")
            }

            if (compareVersions(tagName, currentVersion) > 0) {
                UpdateResult.UpdateAvailable(
                    latestTag = tagName,
                    currentVersion = currentVersion,
                    releaseNotes = body,
                    apkDownloadUrl = apkUrl,
                    releasePageUrl = htmlUrl
                )
            } else {
                UpdateResult.UpToDate(
                    currentVersion = currentVersion,
                    latestTag = tagName
                )
            }
        } catch (t: Throwable) {
            UpdateResult.Error("Network error while checking GitHub releases: ${t.localizedMessage ?: "Check internet connection"}")
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Compares two version strings (e.g., "v5.1.12" vs "5.1.10" or "v5.2" vs "5.1").
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, and 0 if equal.
     */
    fun compareVersions(v1Raw: String, v2Raw: String): Int {
        val parts1 = extractNumericParts(v1Raw)
        val parts2 = extractNumericParts(v2Raw)
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun extractNumericParts(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        return Regex("\\d+").findAll(cleaned).mapNotNull { it.value.toIntOrNull() }.toList()
    }
}
