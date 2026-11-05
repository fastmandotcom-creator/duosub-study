package duosub.study.data

import duosub.study.update.isAutoUpdateCheckDue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    private val digest = "136c5c6363625a137e1b07cd4fcc6e00fd64ae23bfca427cfeff01ff038f080e"

    private fun official(
        tag: String,
        apkName: String = OFFICIAL_APK_NAME,
        apkDigest: String = "sha256:$digest",
    ) = """
        {"tag_name":"$tag","draft":false,"prerelease":false,
         "html_url":"https://github.com/fastmandotcom-creator/duosub-study/releases/tag/$tag",
         "assets":[
          {"name":"$apkName","size":34251919,"digest":"$apkDigest",
           "browser_download_url":"https://github.com/fastmandotcom-creator/duosub-study/releases/download/$tag/$apkName"},
          {"name":"DualSub-Replay.apk.sha256","size":85,
           "browser_download_url":"https://github.com/fastmandotcom-creator/duosub-study/releases/download/$tag/DualSub-Replay.apk.sha256"}
         ]}
        """.trimIndent()

    @Test
    fun theChannelFollowsTheBuild() {
        assertEquals(UpdateChannel.Official, updateChannelFor("duosub.study", false, "1.7.1"))
        assertEquals(
            UpdateChannel.Preview(138, 470),
            updateChannelFor("duosub.study.preview", true, "1.0.4-pr138-build470"),
        )
        // DuoSub Study is published as a debug build under its own application id.
        assertEquals(UpdateChannel.Study, updateChannelFor("duosub.study.study", true, "1.0.4-study.7"))
        assertEquals(UpdateChannel.Study, updateChannelFor("duosub.study.study", false, "1.0.4-study.7"))
        // A local debug build is signed with another key, and the release smoke build is a test copy.
        assertEquals(UpdateChannel.None, updateChannelFor("duosub.study", true, "1.0.4"))
        assertEquals(UpdateChannel.None, updateChannelFor("duosub.study.releasesmoke", false, "1.0.4"))
        assertEquals(UpdateChannel.None, updateChannelFor("duosub.study.preview", true, "1.0.4"))
    }

    @Test
    fun versionsCompareByTheirNumbers() {
        assertTrue(compareVersions("v1.7.2", "1.7.1") > 0)
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(compareVersions("2.0", "1.99.99") > 0)
        assertEquals(0, compareVersions("v1.7.1", "1.7.1"))
        assertEquals(0, compareVersions("1.7", "1.7.0"))
        assertTrue(compareVersions("1.7.1", "1.7.2-pr3-build9") < 0)
    }

    @Test
    fun aNewerOfficialReleaseIsOffered() {
        val update = parseOfficialRelease(official("v1.7.2"), "1.7.1")!!
        assertEquals("1.7.2", update.versionName)
        assertEquals(digest, update.sha256)
        assertEquals(34251919L, update.sizeBytes)
        assertTrue(update.downloadUrl.endsWith("/v1.7.2/DualSub-Replay.apk"))
        assertTrue(update.checksumUrl!!.endsWith("/v1.7.2/DualSub-Replay.apk.sha256"))
        assertTrue(update.pageUrl.endsWith("/releases/tag/v1.7.2"))
    }

    @Test
    fun theSameOrAnOlderReleaseIsUpToDate() {
        assertNull(parseOfficialRelease(official("v1.7.1"), "1.7.1"))
        assertNull(parseOfficialRelease(official("v1.6.9"), "1.7.1"))
    }

    @Test
    fun aReleaseWithoutTheAppIsNotOffered() {
        assertNull(parseOfficialRelease(official("v1.7.2", apkName = "Other.apk"), "1.7.1"))
    }

    @Test
    fun aMissingDigestFallsBackToTheChecksumFile() {
        val update = parseOfficialRelease(official("v1.7.2", apkDigest = ""), "1.7.1")!!
        assertNull(update.sha256)
        assertEquals(digest, parseSha256File("$digest  DualSub-Replay.apk\n"))
        assertNull(parseSha256File("not a checksum"))
        assertNull(sha256Digest("md5:$digest"))
        assertNull(sha256Digest("sha256:1234"))
        assertEquals(digest, sha256Digest("SHA256:${digest.uppercase()}"))
    }

    @Test
    fun aPreviewBuildUpdatesOnlyToANewerBuildOfItsOwnPullRequest() {
        fun asset(
            name: String,
            hex: String = digest,
        ) = """{"name":"$name","size":5,"digest":"sha256:$hex",
               "browser_download_url":"https://github.com/fastmandotcom-creator/duosub-study/releases/download/preview/$name"}"""
        val json =
            """{"html_url":"https://github.com/fastmandotcom-creator/duosub-study/releases/tag/preview","assets":[
                ${asset("DualSub-Replay-PR138-build471-preview.apk")},
                ${asset("DualSub-Replay-PR137-build480-preview.apk")},
                ${asset("DualSub-Replay-PR138-build469-preview.apk")}]}"""
        val update = parsePreviewRelease(json, UpdateChannel.Preview(138, 470))!!
        assertEquals("PR 138 build 471", update.versionName)
        assertTrue(update.downloadUrl.endsWith("DualSub-Replay-PR138-build471-preview.apk"))
        assertEquals(digest, update.sha256)
        assertNull(parsePreviewRelease(json, UpdateChannel.Preview(138, 471)))
        assertNull(parsePreviewRelease(json, UpdateChannel.Preview(140, 1)))
    }

    private fun study(
        tag: String,
        apkName: String = STUDY_APK_NAME,
        prerelease: Boolean = false,
        draft: Boolean = false,
    ) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,
         "html_url":"https://github.com/fastmandotcom-creator/duosub-study/releases/tag/$tag",
         "assets":[
          {"name":"$apkName","size":63657211,"digest":"sha256:$digest",
           "browser_download_url":"https://github.com/fastmandotcom-creator/duosub-study/releases/download/$tag/$apkName"}
         ]}
        """.trimIndent()

    @Test
    fun aNewerStudyReleaseIsOfferedToTheInstalledStudyBuild() {
        val update = parseStudyRelease(study("study-1.0.4.8"), "1.0.4-study.7")!!
        assertEquals("1.0.4.8", update.versionName)
        assertEquals(digest, update.sha256)
        assertEquals(63657211L, update.sizeBytes)
        assertTrue(update.downloadUrl.endsWith("/study-1.0.4.8/DualSub-Study.apk"))
        assertTrue(update.downloadUrl.startsWith("https://github.com/fastmandotcom-creator/duosub-study/"))
        assertTrue(update.pageUrl.endsWith("/releases/tag/study-1.0.4.8"))
        // The first Study release has three numbers and sorts below the four-number ones.
        assertNull(parseStudyRelease(study("study-1.0.4"), "1.0.4-study.2"))
        assertEquals("1.0.4.2", parseStudyRelease(study("study-1.0.4.2"), "1.0.4")!!.versionName)
        assertEquals("1.0.5", parseStudyRelease(study("study-1.0.5"), "1.0.4-study.9")!!.versionName)
    }

    @Test
    fun theSameOrAnOlderStudyReleaseIsUpToDate() {
        assertNull(parseStudyRelease(study("study-1.0.4.7"), "1.0.4-study.7"))
        assertNull(parseStudyRelease(study("study-1.0.4.6"), "1.0.4-study.7"))
        // 7 is a number, not text: 10 is newer than 9.
        assertEquals("1.0.4.10", parseStudyRelease(study("study-1.0.4.10"), "1.0.4-study.9")!!.versionName)
    }

    @Test
    fun onlyAStudyReleaseWithItsApkIsOffered() {
        assertNull(parseStudyRelease(study("study-1.0.4.8", apkName = "Other.apk"), "1.0.4-study.7"))
        assertNull(parseStudyRelease(study("study-1.0.4.8", prerelease = true), "1.0.4-study.7"))
        assertNull(parseStudyRelease(study("study-1.0.4.8", draft = true), "1.0.4-study.7"))
        assertNull(parseStudyRelease(study("v1.9.0"), "1.0.4-study.7"))
        assertNull(parseStudyRelease(study("preview"), "1.0.4-study.7"))
        assertEquals("1.0.4.7", studyInstalledVersion("1.0.4-study.7"))
        assertEquals("1.0.4", studyInstalledVersion("1.0.4"))
    }

    @Test
    fun downloadsComeOnlyFromGitHub() {
        assertTrue(isAllowedUpdateHost("api.github.com"))
        assertTrue(isAllowedUpdateHost("github.com"))
        assertTrue(isAllowedUpdateHost("release-assets.githubusercontent.com"))
        assertFalse(isAllowedUpdateHost("example.com"))
        assertFalse(isAllowedUpdateHost("github.com.example.com"))
    }

    @Test
    fun automaticUpdatesAskGitHubAtMostOnceADay() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(isAutoUpdateCheckDue(0, 1_000))
        assertFalse(isAutoUpdateCheckDue(10 * day, 10 * day + day - 1))
        assertTrue(isAutoUpdateCheckDue(10 * day, 11 * day))
        // A clock moved backwards does not stop checks for good.
        assertTrue(isAutoUpdateCheckDue(10 * day, 5 * day))
    }
}
