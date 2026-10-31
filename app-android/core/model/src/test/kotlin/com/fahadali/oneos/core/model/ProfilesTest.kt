package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {
    @Test
    fun theFourBundlesResolveToTheirProfiles() {
        assertEquals(Profiles.F8U, Profiles.resolve(Bundles.F8U.facts).profile)
        assertEquals(Profiles.S_ULTRA_8850, Profiles.resolve(Bundles.S26U.facts).profile)
        assertEquals(Profiles.S_ULTRA_8750, Profiles.resolve(Bundles.S25U.facts).profile)
        assertEquals(Profiles.S_8750, Profiles.resolve(Bundles.S25.facts).profile)
        // The hardware says what the profile says.
        assertEquals(FormFactor.FOLD, Bundles.F8U.facts.form.form)
        assertEquals(FormFactor.BAR, Bundles.S26U.facts.form.form)
        assertEquals(FormFactor.BAR, Bundles.S25U.facts.form.form)
        assertEquals(FormFactor.BAR, Bundles.S25.facts.form.form)
        assertTrue(Bundles.S25.facts.form.evidence.contains("1 built-in display"))
    }

    @Test
    fun builtInTable() {
        // 0.4.27: Infinity-X, last, on every phone, untested.
        val ifx = ImageChoice(Images.ID_INFINITYX, PackageSets.ID_INFINITYX, tested = false)
        // 0.4.31: tested where a tester's session reached the home screen on it
        // (reports of 2026-10-29/30, docs/compatibility.md).
        val ifxT = ifx.copy(tested = true)
        // 0.4.31: HyperOS after it, on every phone, untested.
        val hyper = ImageChoice(Images.ID_HYPEROS_LHASA, PackageSets.ID_HYPER_F8U, tested = false)
        val f8u = Profiles.F8U
        assertEquals(listOf("SM-F976", "q8q", "SM8850"), listOf(f8u.modelPrefix, f8u.codename, f8u.socModel))
        assertEquals(FormFactor.FOLD, f8u.form)
        assertEquals(
            listOf(
                ImageChoice("coloros17-findn6", "n6-f8u", tested = true),
                // 0.3.7: the Pixel 11 Fold image, which runs 41-44 booted on this phone.
                ImageChoice("pixel11fold-yogi", "pixel-f8u", tested = true),
                ifx,
                // HyperOS (0.4.31: on every phone), untested.
                ImageChoice("hyperos-lhasa", "hyper-f8u", tested = false),
            ),
            f8u.choices,
        )
        assertTrue(f8u.testedWithAnyImage && f8u.enabled)

        val s26 = Profiles.S_ULTRA_8850
        assertEquals(listOf("SM-S948", "m3q", "SM8850"), listOf(s26.modelPrefix, s26.codename, s26.socModel))
        assertEquals(FormFactor.BAR, s26.form)
        // The Find X9 Ultra CN image, tested on the S Ultras and the S25 since 0.4.7; from
        // 0.4.11 the IN build first, untested.
        val xin = ImageChoice(Images.ID_FIND_X9U_IN, PackageSets.ID_X9U_IN_SULTRA, tested = false)
        val xinT = xin.copy(tested = true)
        val sUltra = listOf(
            xin,
            ImageChoice("coloros17-findx9u", "x9u-sultra", tested = true),
        )
        // 0.4.10: and the Pixel 11 Pro image with its own set, untested.
        assertEquals(sUltra + ImageChoice(Images.ID_PIXEL_KODIAK, PackageSets.ID_PIXEL_SULTRA, tested = false) + ifxT + hyper, s26.choices)
        assertTrue(s26.testedWithAnyImage && s26.enabled)

        val s25 = Profiles.S_ULTRA_8750
        assertEquals(listOf("SM-S938", "pa3q", "SM8750"), listOf(s25.modelPrefix, s25.codename, s25.socModel))
        assertEquals(FormFactor.BAR, s25.form)
        // 0.4.22: and the Pixel 9 Pro XL image with its own set, untested.
        val pixel9 = ImageChoice(Images.ID_PIXEL_KOMODO, PackageSets.ID_PIXEL9_SULTRA, tested = false)
        assertEquals(sUltra + pixel9 + ifxT + hyper, s25.choices)
        assertTrue(s25.testedWithAnyImage && s25.enabled)

        // Galaxy S25 (0.3.4): the S Ultra image and set (tested since 0.4.7), the S25 Ultra's notes.
        val s25Base = Profiles.S_8750
        assertEquals("s-8750", s25Base.id)
        assertEquals("Galaxy S25", s25Base.label)
        assertEquals(listOf("SM-S931", "pa1q", "SM8750"), listOf(s25Base.modelPrefix, s25Base.codename, s25Base.socModel))
        assertEquals(FormFactor.BAR, s25Base.form)
        assertEquals(sUltra + pixel9 + ifxT + hyper, s25Base.choices)
        assertTrue(s25Base.testedWithAnyImage && s25Base.enabled)
        assertEquals(s25.notes, s25Base.notes)
        assertEquals(listOf("profile-vendor-older"), s25Base.notes.map { it.id })
        // Galaxy Z Fold7 (0.4.8): the Find N6 image with the Fold's set, untested; the SM8750 notes; no Pixel image (SVE).
        val fold7 = Profiles.FOLD7
        assertEquals("f7", fold7.id)
        assertEquals("Galaxy Z Fold7", fold7.label)
        assertEquals(listOf("SM-F966", "q7q", "SM8750"), listOf(fold7.modelPrefix, fold7.codename, fold7.socModel))
        assertEquals(FormFactor.FOLD, fold7.form)
        assertEquals(listOf(ImageChoice(Images.ID_FIND_N6, PackageSets.ID_N6_F8U, tested = false), ifx, hyper), fold7.choices)
        assertTrue(fold7.enabled && !fold7.testedWithAnyImage)
        assertEquals(s25.notes, fold7.notes)
        assertTrue(fold7.choice(Images.ID_PIXEL_YOGI) == null)
        // Galaxy S25+ (0.4.10): the S25's image and set, untested; the SM8750 notes.
        val s25Plus = Profiles.S_PLUS_8750
        assertEquals(listOf("s-plus-8750", "Galaxy S25+"), listOf(s25Plus.id, s25Plus.label))
        assertEquals(listOf("SM-S936", "pa2q", "SM8750"), listOf(s25Plus.modelPrefix, s25Plus.codename, s25Plus.socModel))
        assertEquals(FormFactor.BAR, s25Plus.form)
        assertEquals(listOf(xin, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), pixel9, ifx, hyper), s25Plus.choices)
        assertEquals(s25.notes, s25Plus.notes)
        // Galaxy Z Fold8 (0.4.10): the Fold's images and sets, untested; no older-vendor note (vendor API 202504).
        val fold8 = Profiles.FOLD8
        assertEquals(listOf("f8", "Galaxy Z Fold8"), listOf(fold8.id, fold8.label))
        assertEquals(listOf("SM-F971", "h8q", "SM8850"), listOf(fold8.modelPrefix, fold8.codename, fold8.socModel))
        assertEquals(FormFactor.FOLD, fold8.form)
        // 0.4.14 (pull request #1): the Find N6 image with its own set n6-f8, run on the friend's SM-F971B.
        assertEquals(
            listOf(
                ImageChoice(Images.ID_FIND_N6, PackageSets.ID_N6_F8, tested = true),
                ImageChoice(Images.ID_PIXEL_YOGI, PackageSets.ID_PIXEL_F8, tested = true),
                ifx,
                hyper,
            ),
            fold8.choices,
        )
        assertTrue(fold8.notes.isEmpty() && fold8.testedWithAnyImage)
        assertEquals(Images.ID_FIND_N6, fold8.recommended?.imageId)
        // Galaxy S26 (0.4.11): the S Ultra's images and sets, untested; one profile per model number.
        val s26Base = Profiles.S_8850
        assertEquals(listOf("s-8850", "Galaxy S26"), listOf(s26Base.id, s26Base.label))
        assertEquals(listOf("SM-S942", "m1q", "SM8850"), listOf(s26Base.modelPrefix, s26Base.codename, s26Base.socModel))
        assertEquals(FormFactor.BAR, s26Base.form)
        assertEquals(
            listOf(
                xin,
                ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false),
                ImageChoice(Images.ID_PIXEL_KODIAK, PackageSets.ID_PIXEL_SULTRA, tested = false),
                ifx,
                hyper,
            ),
            s26Base.choices,
        )
        assertTrue(s26Base.notes.isEmpty() && !s26Base.testedWithAnyImage)
        val s26Jp = Profiles.S_8850_JP
        assertEquals(listOf("s-8850-jp", "SC-51G", "m1q", "SM8850"), listOf(s26Jp.id, s26Jp.modelPrefix, s26Jp.codename, s26Jp.socModel))
        assertEquals(s26Base.choices, s26Jp.choices)
        assertEquals(s26Base.form, s26Jp.form)
        // Galaxy S24 Ultra (0.4.11): the X9U image, untested; the older-vendor note with its own versions.
        val s24u = Profiles.S_ULTRA_8650
        assertEquals(listOf("s-ultra-8650", "Galaxy S24 Ultra"), listOf(s24u.id, s24u.label))
        assertEquals(listOf("SM-S928", "e3q", "SM8650"), listOf(s24u.modelPrefix, s24u.codename, s24u.socModel))
        assertEquals(listOf(xin, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), pixel9, ifxT, hyper), s24u.choices)
        assertEquals(listOf(Profiles.ID_NOTE_OLDER_VENDOR), s24u.notes.map { it.id })
        assertTrue(s24u.notes.single().detail.contains("vendor API 34 (Android 14) and kernel 6.1"))
        // Galaxy S26+ (0.4.12): the S26's images and sets, untested, no notes (vendor API 202504).
        val s26Plus = Profiles.S_PLUS_8850
        assertEquals(listOf("s-plus-8850", "Galaxy S26+"), listOf(s26Plus.id, s26Plus.label))
        assertEquals(listOf("SM-S947", "m2q", "SM8850"), listOf(s26Plus.modelPrefix, s26Plus.codename, s26Plus.socModel))
        assertEquals(FormFactor.BAR, s26Plus.form)
        assertEquals(s26Base.choices, s26Plus.choices)
        assertTrue(s26Plus.notes.isEmpty() && !s26Plus.testedWithAnyImage)
        // The Exynos Galaxy S26+ and Z Flip7 (0.4.17): their own codenames and chips, untested, the Exynos note;
        // the S26+ takes the S26's images (SVE for Pixel), the Flip7 the Find X9 Ultra builds as a phone.
        val s26PlusExynos = Profiles.S_PLUS_9965
        assertEquals(listOf("s-plus-9965", "Galaxy S26+ (Exynos)"), listOf(s26PlusExynos.id, s26PlusExynos.label))
        assertEquals(listOf("SM-S947", "m2s", "s5e9965"), listOf(s26PlusExynos.modelPrefix, s26PlusExynos.codename, s26PlusExynos.socModel))
        assertEquals(FormFactor.BAR, s26PlusExynos.form)
        assertEquals(s26Base.choices, s26PlusExynos.choices)
        val flip7 = Profiles.FLIP7
        assertEquals(listOf("flip7", "Galaxy Z Flip7"), listOf(flip7.id, flip7.label))
        assertEquals(listOf("SM-F766", "b7s", "s5e9955"), listOf(flip7.modelPrefix, flip7.codename, flip7.socModel))
        assertEquals(FormFactor.FLIP, flip7.form)
        assertEquals(listOf(xin, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), ImageChoice(Images.ID_PIXEL_KOMODO, PackageSets.ID_PIXEL9_SULTRA, tested = false), ifx, hyper), flip7.choices)
        // The Galaxy A57 (0.4.29): the first A-series phone, Exynos, a bar phone with the Flip7's images.
        val a57 = Profiles.A57
        assertEquals(listOf("a57", "Galaxy A57"), listOf(a57.id, a57.label))
        assertEquals(listOf("SM-A576", "a57x", "s5e8865"), listOf(a57.modelPrefix, a57.codename, a57.socModel))
        assertEquals(FormFactor.BAR, a57.form)
        // 0.4.31: a tester's session reached home on the CN ColorOS build (2026-10-30),
        // the first A-series report; the rest is still the Flip7's list.
        assertEquals(
            listOf(xin, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = true), pixel9, ifx, hyper),
            a57.choices,
        )
        assertTrue(a57.testedWithAnyImage)
        // The Galaxy A56 (0.4.31): the A57's smaller sibling, one Exynos generation down.
        val a56 = Profiles.A56
        assertEquals(listOf("a56", "Galaxy A56"), listOf(a56.id, a56.label))
        assertEquals(listOf("SM-A566", "a56x", "s5e8855"), listOf(a56.modelPrefix, a56.codename, a56.socModel))
        assertEquals(FormFactor.BAR, a56.form)
        assertEquals(flip7.choices, a56.choices)
        for (p in listOf(s26PlusExynos, flip7, a56)) {
            assertEquals(listOf(Profiles.ID_NOTE_EXYNOS), p.notes.map { it.id })
            assertEquals(Level.WARN, p.notes.single().level)
            assertFalse(p.testedWithAnyImage)
        }
        // Galaxy Z Flip5 (0.4.29): the S23s' chip and vendor, a flip with their images and note.
        val flip5 = Profiles.FLIP5
        assertEquals(listOf("flip5", "Galaxy Z Flip5"), listOf(flip5.id, flip5.label))
        assertEquals(listOf("SM-F731", "b5q", "SM8550"), listOf(flip5.modelPrefix, flip5.codename, flip5.socModel))
        assertEquals(FormFactor.FLIP, flip5.form)
        // 0.4.31: reported on the global ColorOS build and Infinity-X (camera broken on both),
        // so it is no longer the S23's untested list.
        assertEquals(
            listOf(xinT, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), pixel9, ifxT, hyper),
            flip5.choices,
        )
        assertEquals(listOf(Profiles.ID_NOTE_EXPECT_NO_START), flip5.notes.map { it.id })
        // Galaxy S23 and S23+ (0.4.11): the X9U image, untested, with the will-most-likely-not-start note.
        val s23 = Profiles.S_8550
        val s23Plus = Profiles.S_PLUS_8550
        assertEquals(listOf("s-8550", "Galaxy S23"), listOf(s23.id, s23.label))
        assertEquals(listOf("SM-S911", "dm1q", "SM8550"), listOf(s23.modelPrefix, s23.codename, s23.socModel))
        assertEquals(listOf("s-plus-8550", "Galaxy S23+"), listOf(s23Plus.id, s23Plus.label))
        assertEquals(listOf("SM-S916", "dm2q", "SM8550"), listOf(s23Plus.modelPrefix, s23Plus.codename, s23Plus.socModel))
        for (p in listOf(s23, s23Plus)) {
            assertEquals(FormFactor.BAR, p.form)
            assertEquals(listOf(xin, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), ImageChoice(Images.ID_PIXEL_KOMODO, PackageSets.ID_PIXEL9_SULTRA, tested = false), ifx, hyper), p.choices)
            assertEquals(listOf(Profiles.ID_NOTE_EXPECT_NO_START), p.notes.map { it.id })
            assertEquals(Level.WARN, p.notes.single().level)
        }
        assertEquals(
            listOf(
                Profiles.F8U, Profiles.S_ULTRA_8850, Profiles.S_ULTRA_8750, Profiles.S_8750, Profiles.FOLD7, Profiles.S_PLUS_8750, Profiles.FOLD8,
                Profiles.S_8850, Profiles.S_8850_JP, Profiles.S_ULTRA_8650, Profiles.S_8550, Profiles.S_PLUS_8550, Profiles.S_PLUS_8850,
                Profiles.S_PLUS_9965, Profiles.FLIP7, Profiles.A56, Profiles.A57, Profiles.FLIP5,
                Profiles.FOLD6, Profiles.A07,
            ),
            Profiles.BUILT_IN,
        )

        // Ids unique; every choice names an image and a set built from that image,
        // each image once per profile; the fold grid with a ColorOS image on the fold
        // form only: the Pixel and Infinity-X sets have none (ColorOS's wallpaper app's;
        // Infinity-X has OPPO's jars but not that app's layout).
        assertEquals(Profiles.BUILT_IN.size, Profiles.BUILT_IN.map { it.id }.toSet().size)
        for (p in Profiles.BUILT_IN) {
            assertTrue(p.id, p.choices.isNotEmpty())
            assertEquals(p.id, p.choices.size, p.choices.map { it.imageId }.toSet().size)
            for (c in p.choices) {
                val image = Images.byId(c.imageId)
                val set = PackageSets.byId(c.packageSetId)
                assertNotNull("${p.id} ${c.imageId}", image)
                assertNotNull("${p.id} ${c.packageSetId}", set)
                assertEquals("${p.id} ${c.imageId}", image!!.id, set!!.imageId)
                val colorOs = image.family == SystemFamily.COLOROS
                assertEquals("${p.id} ${set.id}", p.form == FormFactor.FOLD && colorOs, PackageSets.WALLPAPER_GRID_RRO in set.names)
                assertEquals(c, p.choice(c.imageId))
            }
            assertEquals(p.choices.map { it.imageId }, p.images().map { it.id })
            assertTrue(p.id, p.id.matches(Regex("[a-z0-9][a-z0-9-]{0,31}")))
        }
        assertNull(Profiles.F8U.choice(Images.ID_FIND_X9U))
        // The owner does not want the X9U (CN) image on the Fold: only the N6 and Pixel images there.
        // The Fold7 (0.4.8) takes the N6 image only; the Fold6 (0.4.33) the same.
        assertEquals(listOf(Profiles.F8U, Profiles.FOLD7, Profiles.FOLD8, Profiles.FOLD6), Profiles.BUILT_IN.filter { it.form == FormFactor.FOLD })
        // The Find X9 Ultra builds run as a phone: the bar phones and (0.4.17) the Z Flip7 on its main screen.
        assertTrue(Profiles.BUILT_IN.filter { it.choice(Images.ID_FIND_X9U) != null }.all { it.form == FormFactor.BAR || it.form == FormFactor.FLIP })
        assertEquals(listOf(Profiles.FLIP7, Profiles.FLIP5), Profiles.BUILT_IN.filter { it.form == FormFactor.FLIP })
        // The Pixel 11 Fold image on the SM8850 folds (it ran on the Fold8 Ultra; 0.4.10 the Z Fold8, untested), with the Fold's set.
        assertEquals(listOf(Profiles.F8U, Profiles.FOLD8), Profiles.BUILT_IN.filter { it.choice(Images.ID_PIXEL_YOGI) != null })
        // The Pixel 11 images only on chips with SVE, which their system code needs: SM8850, and (0.4.17) the
        // Exynos 2600 of the S26+; not the older Snapdragons nor the Flip7's Exynos 2500 (no SME).
        val sve = setOf("SM8850", "s5e9965")
        assertTrue(Profiles.BUILT_IN.filter { it.socModel !in sve }.all { p -> p.images().none { "sve" in it.cpuRequired } })
        // 0.4.22: the Pixel 9 Pro XL image, which needs none, on the SM8750 bar phones and the S24 Ultra; not the
        // Z Fold7 (a bar phone's system on a fold). 0.4.30: also the S23s and the Z Flip5 (Infinity-X started on
        // the S23 with the HIDL marker), the Z Flip7 and the A57; 0.4.31 the A56: every s25 phone, whose APK has its set.
        assertEquals(
            listOf(
                Profiles.S_ULTRA_8750, Profiles.S_8750, Profiles.S_PLUS_8750, Profiles.S_ULTRA_8650, Profiles.S_8550, Profiles.S_PLUS_8550,
                Profiles.FLIP7, Profiles.A56, Profiles.A57, Profiles.FLIP5,
            ),
            Profiles.BUILT_IN.filter { it.choice(Images.ID_PIXEL_KOMODO) != null },
        )
        assertEquals(PackageSets.ID_PIXEL_F8U, Profiles.F8U.choice(Images.ID_PIXEL_YOGI)!!.packageSetId)
        // 0.4.11: every bar phone that takes the CN build takes the IN build too, listed first; never a fold.
        assertEquals(
            Profiles.BUILT_IN.filter { it.choice(Images.ID_FIND_X9U) != null },
            Profiles.BUILT_IN.filter { it.choices.firstOrNull()?.imageId == Images.ID_FIND_X9U_IN },
        )
        assertTrue(Profiles.BUILT_IN.filter { it.form == FormFactor.FOLD }.all { it.choice(Images.ID_FIND_X9U_IN) == null })
        // Where the CN build has run it stays the preselected one until the IN build has.
        assertEquals(Images.ID_FIND_X9U, Profiles.S_ULTRA_8750.recommended?.imageId)
        assertEquals(Images.ID_FIND_X9U_IN, Profiles.S_PLUS_8750.recommended?.imageId)
        // Every built-in image is some profile's.
        assertEquals(Images.ALL.map { it.id }.toSet(), Profiles.BUILT_IN.flatMap { p -> p.choices.map { it.imageId } }.toSet())
    }

    @Test
    fun allThreeIdsMustMatch() {
        // The probe builds matched model OR codename; build 2 needs all three.
        fun profile(model: String, device: String, soc: String) = Profiles.resolve(model, device, soc).profile

        assertEquals(Profiles.S_ULTRA_8850, profile("SM-S948U", "m3q", "SM8850"))
        assertEquals(Profiles.S_ULTRA_8850, profile("SM-S948B", "M3Q", "sm8850"))
        assertEquals(Profiles.S_ULTRA_8850, profile(" sm-s9480 ", "m3q", "SM8850"))
        assertNull(profile("SM-S948U", "pa3q", "SM8850")) // right model, wrong codename
        assertNull(profile("SM-S948U", "m3q", "SM8750")) // right model and codename, other chip
        assertNull(profile("SM-S948U", "m3q", "s5e9955")) // an Exynos variant
        assertNull(profile("SM-S938B", "m3q", "SM8850")) // S25 model number, S26 codename
        assertNull(profile("SM-F966B", "q8q", "SM8850")) // codename alone matched in the probe builds
        assertNull(profile("SM-F976B", "q8q", "")) // chip unknown: fail closed
        assertEquals(Profiles.S_ULTRA_8650, profile("SM-S928B", "e3q", "SM8650")) // S24 Ultra (0.4.11)
        assertNull(profile("SM-S928B", "e3q", "s5e9945")) // an Exynos S24 Ultra
        // Galaxy S26 (0.4.11): SM-S942 or the Japanese SC-51G, codename m1q, SM8850; not the Exynos S26.
        assertEquals(Profiles.S_8850, profile("SM-S942B", "m1q", "SM8850"))
        assertEquals(Profiles.S_8850_JP, profile("SC-51G", "m1q", "SM8850"))
        assertNull(profile("SM-S942B", "m1q", "s5e9965"))
        assertNull(profile("SM-S948B", "m1q", "SM8850")) // S26 Ultra model number, S26 codename
        // Galaxy S26+ (0.4.12): SM-S947, m2q, SM8850.
        assertEquals(Profiles.S_PLUS_8850, profile("SM-S947U", "m2q", "SM8850"))
        assertEquals(Profiles.S_PLUS_8850, profile("SM-S947B", "m2q", "SM8850"))
        assertNull(profile("SM-S947B", "m2q", "s5e9965")) // Exynos chip, Snapdragon codename
        // The Exynos S26+ and Z Flip7 (0.4.17).
        assertEquals(Profiles.S_PLUS_9965, profile("SM-S947B", "m2s", "s5e9965"))
        assertNull(profile("SM-S947B", "m2s", "SM8850"))
        assertEquals(Profiles.FLIP7, profile("SM-F766U", "b7s", "s5e9955"))
        assertEquals(Profiles.FLIP7, profile("SM-F766B", "B7S", "S5E9955"))
        assertEquals(Profiles.A57, profile("SM-A576B", "a57x", "s5e8865"))
        assertEquals(Profiles.FLIP5, profile("SM-F731B", "b5q", "SM8550"))
        assertNull(profile("SM-F731B", "b5q", "SM8650"))
        assertEquals(Profiles.A56, profile("SM-A566B", "a56x", "s5e8855")) // the A56 (0.4.31)
        assertNull(profile("SM-A566B", "a57x", "s5e8855")) // A56 model number, A57 codename
        // 0.4.33: the Galaxy Z Fold6 (the S24 Ultra's SM8650, a fold) and the Galaxy A07 (MediaTek MT6789).
        assertEquals(Profiles.FOLD6, profile("SM-F956B", "q6q", "SM8650"))
        assertNull(profile("SM-F956B", "q6q", "SM8750"))
        assertEquals(FormFactor.FOLD, Profiles.FOLD6.form)
        assertEquals(listOf(Images.ID_FIND_N6, Images.ID_INFINITYX), Profiles.FOLD6.choices.map { it.imageId })
        assertEquals(Profiles.A07, profile("SM-A075F", "a07", "MT6789"))
        assertNull(profile("SM-A075F", "a07", "s5e8855"))
        assertEquals(listOf(Images.ID_INFINITYX), Profiles.A07.choices.map { it.imageId })
        assertEquals(listOf(Profiles.ID_NOTE_MEDIATEK, Profiles.ID_NOTE_OLDER_VENDOR), Profiles.A07.notes.map { it.id })
        assertNull(profile("SM-F766U", "b7s", "s5e9965"))
        assertNull(profile("SM-S947U", "m3q", "SM8850")) // S26+ model number, S26 Ultra codename
        // Galaxy S23 and S23+ (0.4.11).
        assertEquals(Profiles.S_8550, profile("SM-S911B", "dm1q", "SM8550"))
        assertEquals(Profiles.S_PLUS_8550, profile("SM-S916U", "dm2q", "SM8550"))
        assertNull(profile("SM-S911B", "dm2q", "SM8550"))
        assertNull(profile("SM-S918B", "dm3q", "SM8550")) // S23 Ultra: no report, no profile
        // Galaxy S25 (base): all three ids, like the others.
        assertEquals(Profiles.S_8750, profile("SM-S931B", "pa1q", "SM8750"))
        assertEquals(Profiles.S_8750, profile("SM-S931U1", "PA1Q", "sm8750"))
        assertNull(profile("SM-S931B", "pa3q", "SM8750")) // S25 model number, S25 Ultra codename
        assertNull(profile("SM-S938B", "pa1q", "SM8750")) // S25 Ultra model number, S25 codename
        assertNull(profile("SM-S931B", "pa1q", "SM8850")) // another chip
        assertEquals(Profiles.S_PLUS_8750, profile("SM-S936B", "pa2q", "SM8750")) // S25+ (0.4.10)
        assertEquals(Profiles.FOLD8, profile("SM-F971B", "h8q", "SM8850")) // Z Fold8 (0.4.10)
        assertNull(profile("SM-F971B", "h8q", "SM8750"))
        assertNull(profile("SM-F976B", "h8q", "SM8850")) // Fold8 Ultra model number, Fold8 codename
        // Galaxy Z Fold7: all three ids.
        assertEquals(Profiles.FOLD7, profile("SM-F966B", "q7q", "SM8750"))
        assertEquals(Profiles.FOLD7, profile("SM-F966U", "Q7Q", "sm8750"))
        assertNull(profile("SM-F966B", "q7q", "SM8850")) // another chip
        assertNull(profile("SM-F976B", "q7q", "SM8750")) // Fold8 Ultra model number, Fold7 codename
        assertNull(profile("SM-F766B", "b7q", "SM8750")) // Z Flip7: no profile
        assertNull(profile("Pixel 10 Pro Fold", "rango", "Tensor G5"))

        val near = Profiles.resolve("SM-S948U", "pa3q", "SM8750")
        assertNull(near.profile)
        assertEquals(Profiles.S_ULTRA_8850, near.nearest)
        assertEquals(listOf("codename pa3q (profile: m3q)", "chip SM8750 (profile: SM8850)"), near.mismatches)

        assertNull(Profiles.resolve("SM-A566B", "a56x", "s5e8855").nearest)
    }

    @Test
    fun builtInProfilesNeverOverlap() {
        val all = Profiles.BUILT_IN
        for (a in all) {
            for (b in all) {
                if (a === b) continue
                assertFalse("${a.id} vs ${b.id}", a.matches(b.modelPrefix + "X", b.codename, b.socModel))
            }
        }
        // Two profiles matching one phone resolve to nothing (fail closed).
        val twice = Profiles.resolve("SM-F976B", "q8q", "SM8850", listOf(Profiles.F8U, Profiles.F8U.copy(id = "copy")))
        assertNull(twice.profile)
        assertTrue(twice.mismatches.single().startsWith("matches 2 profiles"))
    }

    @Test
    fun socModelFromThePlatformWhenThePropIsEmpty() {
        val f = Fixtures.facts(soc = Fixtures.F8U_SOC.copy(model = ""))
        assertEquals("SM8850", f.soc.model)
        assertEquals(Profiles.F8U, Profiles.resolve(f).profile)
    }
}
