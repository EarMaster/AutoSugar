package de.autosugar.data.storage

import de.autosugar.data.model.GlucoseUnit
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.model.ProfileIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSerializerTest {

    private val serializer = ProfileSerializer()

    private val profile = NightscoutProfile(
        id = "test-id",
        displayName = "Test Profile",
        baseUrl = "https://example.nightscout.io",
        apiToken = "secret",
        unit = GlucoseUnit.MG_DL,
        icon = ProfileIcon.PERSON,
        alertsEnabled = false,
    )

    @Test
    fun `round-trip preserves all fields`() {
        val result = serializer.fromJson(serializer.toJson(listOf(profile)))
        assertEquals(1, result.size)
        assertEquals(profile, result[0])
    }

    @Test
    fun `round-trip preserves alertsEnabled true`() {
        val withAlerts = profile.copy(alertsEnabled = true)
        val result = serializer.fromJson(serializer.toJson(listOf(withAlerts)))
        assertTrue(result[0].alertsEnabled)
    }

    @Test
    fun `round-trip preserves mmol_L unit`() {
        val mmol = profile.copy(unit = GlucoseUnit.MMOL_L)
        val result = serializer.fromJson(serializer.toJson(listOf(mmol)))
        assertEquals(GlucoseUnit.MMOL_L, result[0].unit)
    }

    @Test
    fun `round-trip preserves non-default icon`() {
        val withIcon = profile.copy(icon = ProfileIcon.HEART)
        val result = serializer.fromJson(serializer.toJson(listOf(withIcon)))
        assertEquals(ProfileIcon.HEART, result[0].icon)
    }

    @Test
    fun `fromJson falls back to PERSON for unknown icon`() {
        val json = """[{"id":"x","displayName":"X","baseUrl":"http://x.test","apiToken":"","unit":"MG_DL","icon":"INVALID_ICON","alertsEnabled":false}]"""
        val result = serializer.fromJson(json)
        assertEquals(ProfileIcon.PERSON, result[0].icon)
    }

    @Test
    fun `fromJson returns empty list for empty array`() {
        assertTrue(serializer.fromJson("[]").isEmpty())
    }

    @Test
    fun `toJson and fromJson handle multiple profiles`() {
        val profiles = listOf(
            profile,
            profile.copy(id = "id-2", displayName = "Profile 2", unit = GlucoseUnit.MMOL_L, icon = ProfileIcon.HEART),
        )
        val result = serializer.fromJson(serializer.toJson(profiles))
        assertEquals(2, result.size)
        assertEquals("id-2", result[1].id)
        assertEquals(GlucoseUnit.MMOL_L, result[1].unit)
        assertEquals(ProfileIcon.HEART, result[1].icon)
    }
}
