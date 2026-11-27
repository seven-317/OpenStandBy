package io.github.seven317.openstandby.data.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(version("v1.10.0") > version("1.9.9"))
        assertTrue(version("1.0.10") > version("1.0.2"))
        assertTrue(version("2.0.0") > version("1.99.99"))
        assertTrue(version("v1.0.0") < version("1.0.1"))
    }

    @Test
    fun ignoresTagPrefixAndBuildMetadata() {
        assertEquals(version("1.0.0"), version("v1.0.0+build.12"))
        assertEquals("1.0.0", version("V1.0.0").toString())
    }

    @Test
    fun rejectsPrereleaseAndInvalidTags() {
        for (tag in listOf(null, "v2.0.0-rc.1", "latest", "v1.0", "01.0.0", "v2147483648.0.0")) {
            assertNull(tag, ReleaseVersion.parse(tag))
        }
    }

    private fun version(value: String): ReleaseVersion = requireNotNull(ReleaseVersion.parse(value))
}
