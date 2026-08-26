package de.autosugar.car

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the string content that Google Play rejected AutoSugar over: the car app must not
 * surface initial-setup tasks — or direct the driver to their phone — while driving
 * (car app quality guidelines IT-1 and VI-1).
 *
 * These assertions live in a JVM test rather than alongside the car screen tests in
 * `androidTest` because CI runs `test` but never `connectedAndroidTest`, and because the
 * on-device string resolution would depend on the emulator's locale.
 */
class DriverDistractionStringsTest {

    private val resDir = sequenceOf("app/src/main/res", "src/main/res")
        .map { File(it) }
        .first { it.isDirectory }

    private fun strings(qualifier: String): Map<String, String> {
        val xml = File(resDir, "$qualifier/strings.xml").readText()
        return Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val localeQualifiers =
        resDir.listFiles()!!
            .filter { it.isDirectory && it.name.startsWith("values") && it.name != "values-night" }
            .map { it.name }

    /**
     * The default car screen for an unconfigured app is shown while driving, so its body
     * must be a status statement — not an instruction to pick up the phone. The phone steps
     * belong in [R.string.msg_setup_instructions], which only renders while parked.
     */
    @Test
    fun notSetUpLabel_doesNotInstructPhoneUse() {
        val message = strings("values").getValue("label_not_set_up")
        assertTrue(
            "label_not_set_up must not mention the phone: $message",
            !message.contains("phone", ignoreCase = true),
        )
        assertTrue(
            "label_not_set_up must state that setup is unavailable while driving: $message",
            message.contains("driving", ignoreCase = true),
        )
    }

    /** VI-1: tell the user to look at their phone only when it is safe to do so. */
    @Test
    fun setupInstructions_carrySafeToLookWording() {
        val message = strings("values").getValue("msg_setup_instructions")
        assertTrue(
            "msg_setup_instructions must carry the VI-1 safety wording: $message",
            message.contains("safe", ignoreCase = true),
        )
    }

    /** Keeps the locale sweeps below from passing vacuously if resource discovery breaks. */
    @Test
    fun everyTranslatedLocaleIsDiscovered() {
        assertTrue(
            "expected the default locale plus 10 translations, found $localeQualifiers",
            localeQualifiers.size == 11 && "values" in localeQualifiers,
        )
    }

    /** A missing translation would fall back to English, not to a driving-unsafe string. */
    @Test
    fun everyLocaleDefinesTheSetupStrings() {
        val required = listOf(
            "label_not_set_up",
            "title_setup_required",
            "action_how_to_set_up",
            "action_done",
            "msg_setup_instructions",
        )
        localeQualifiers.forEach { qualifier ->
            val keys = strings(qualifier).keys
            required.forEach { key ->
                assertTrue("$qualifier/strings.xml is missing $key", key in keys)
            }
        }
    }

    /** The rejected string must be gone everywhere, not just in the default locale. */
    @Test
    fun theRejectedNoProfilesStringIsGone() {
        localeQualifiers.forEach { qualifier ->
            assertTrue(
                "$qualifier/strings.xml still defines label_no_profiles",
                "label_no_profiles" !in strings(qualifier).keys,
            )
        }
    }
}
