package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkSeriesTest {
    @Test
    fun everyProfileIsInExactlyOneSeries() {
        for (p in Profiles.BUILT_IN) {
            assertEquals(p.id, 1, ApkSeries.entries.count { p.id in it.profileIds })
        }
        val known = Profiles.BUILT_IN.map { it.id }.toSet()
        for (s in ApkSeries.entries) assertTrue("${s.id} names an unknown profile", known.containsAll(s.profileIds))
    }

    @Test
    fun everySetIsInASeriesDownload() {
        assertEquals(PackageSets.ALL.map { it.id }.toSet(), ApkSeries.entries.flatMap { it.setIds() }.toSet())
    }

    /** app-android/packages/series.txt (what app/build.gradle.kts packs) lists exactly [ApkSeries.setIds]. */
    @Test
    fun theListBuildsUseIsTheModels() {
        val dir = File(System.getProperty("oneos.packagesDir") ?: error("oneos.packagesDir not set"))
        val lines = File(dir, "series.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val listed = lines.associate { l -> l.split(Regex("\\s+")).let { it.first() to it.drop(1).toSet() } }
        assertEquals(ApkSeries.entries.map { it.id }.toSet(), listed.keys)
        for (s in ApkSeries.entries) assertEquals(s.id, s.setIds(), listed.getValue(s.id))
    }

    @Test
    fun aSeriesDownloadIsMuchSmallerThanTheAllInOne() {
        // The Fold's sets are not an S phone's but Infinity-X's and HyperOS's (0.4.27, 0.4.31: one set each on
        // every phone), and the two S series share the ColorOS ones too.
        assertEquals(setOf(PackageSets.ID_INFINITYX, PackageSets.ID_HYPER_F8U), ApkSeries.FOLD.setIds().intersect(ApkSeries.S25.setIds()))
        assertEquals(
            setOf(PackageSets.ID_X9U_SULTRA, PackageSets.ID_X9U_IN_SULTRA, PackageSets.ID_INFINITYX, PackageSets.ID_HYPER_F8U),
            ApkSeries.S26.setIds().intersect(ApkSeries.S25.setIds()),
        )
        for (s in ApkSeries.entries) assertNotEquals(s.id, PackageSets.ALL.size, s.setIds().size)
    }

    @Test
    fun anotherSeriesDownloadNamesTheRightOne() {
        val phone = Bundles.F8U
        val profile = Profiles.resolve(phone.facts).profile!!
        val image = Images.byId(profile.choices.first().imageId)!!
        fun gate(series: ApkSeries?) = SwitchGates.evaluate(
            GatePhase.PREPARE,
            GateInput(
                facts = phone.facts,
                screenLockSet = false,
                oldSwitcherInstalled = false,
                installd = phone.installd,
                stage = StageState(emptyList(), emptyList(), false, null, false),
                imageFile = ImageFileCheck(image.gzName, image.gzBytes, image.gzSha256, image.gzBytes),
                series = series,
            ),
        ).find(SwitchGates.ID_IMAGE)!!.finding

        val wrong = gate(ApkSeries.S25)
        assertEquals(Level.BLOCK, wrong.level)
        assertEquals(SwitchGates.R_IMAGE_OTHER_SERIES, wrong.reason)
        assertEquals("This OneOS is for the Galaxy S25, S24, S23, Z Flip7, Z Flip5, A57, A56 and A07", wrong.title)
        assertTrue(wrong.detail, wrong.detail.contains("needs OneOS for the Galaxy Z Fold (the file ending in -fold.apk)"))
        // Its own series and the all-in-one build do not stop it.
        assertFalse(gate(ApkSeries.FOLD).reason == SwitchGates.R_IMAGE_OTHER_SERIES)
        assertFalse(gate(null).reason == SwitchGates.R_IMAGE_OTHER_SERIES)
    }
}
