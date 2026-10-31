package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PackageSetsTest {
    private val n6 = PackageSets.N6_F8U
    private val x9u = PackageSets.X9U_SULTRA
    private val pixel = PackageSets.PIXEL_F8U
    private val xin = PackageSets.X9U_IN_SULTRA

    /** sha256sum of out/overlays_n6_r50, out/overlays_x9u_r50, out/overlays_x9u_in_r51 (2026-10-25) and out/overlays_pixel_yogi (2026-10-24) without the omitted files. */
    private fun shas(set: PackageSet) = Bundles.text("packages/${set.id}.sha256")

    private fun guards(set: PackageSet) = Bundles.text("packages/${set.id}.guards.txt")

    /** The 15 APEX payloads every set carries. */
    private val commonPayloads = listOf(
        "art", "i18n", "runtime", "sdkext", "crashrecovery", "extservices", "permission", "wifi",
        "appsearch", "mediaprovider", "adservices", "rkpd", "healthfitness", "ondevicepersonalization",
        "configinfrastructure",
    ).map { "${it}_payload.img" }

    @Test
    fun setsAsData() {
        assertEquals(25, n6.files.size)
        assertEquals(24, x9u.files.size)
        assertEquals(35, pixel.files.size) // 0.4.24: framework_patched.jar (One UI's camera transaction codes)
        assertEquals(Images.ID_FIND_N6, n6.imageId)
        assertEquals(Images.ID_FIND_X9U, x9u.imageId)
        assertEquals(Images.ID_PIXEL_YOGI, pixel.imageId)
        assertEquals("out/overlays_n6_r50", n6.sourceDir)
        assertEquals("out/overlays_x9u_r50", x9u.sourceDir)
        assertEquals("out/overlays_x9u_in_r51", xin.sourceDir)
        assertEquals(Images.ID_FIND_X9U_IN, xin.imageId)
        assertEquals("out/overlays_pixel_f8u", pixel.sourceDir)
        assertEquals(
            listOf(n6, x9u, xin, pixel, PackageSets.PIXEL_SULTRA, PackageSets.N6_F8, PackageSets.PIXEL_F8, PackageSets.PIXEL9_SULTRA, PackageSets.INFINITYX, PackageSets.HYPER_F8U),
            PackageSets.ALL,
        )
        // Infinity-X (0.4.27): both guard jars, the camera codes (Java and native), the guest vold, the
        // AOSP signal poll, the IMS backoff, the guest app, the guards and 21 payloads, GmsCore's among them.
        // Not its cellbroadcast module (0.4.28): One UI's stays, as tethering does.
        val ifx = PackageSets.INFINITYX
        assertEquals(Images.ID_INFINITYX, ifx.imageId)
        assertEquals("out/overlays_infinityx_r2", ifx.sourceDir)
        assertEquals(34, ifx.files.size)
        // 0.4.33: the status bar's corner padding, as on the Pixel images.
        assertTrue(ifx.file("OneOSPixelStatusBar.apk")?.role == FileRole.RRO)
        assertEquals(21, ifx.files.count { it.role == FileRole.APEX_PAYLOAD })
        assertEquals(listOf(PackageSets.TETHERING, PackageSets.CELLBROADCAST), ifx.omitted)
        assertTrue(ifx.file(PackageSets.CELLBROADCAST) == null)
        assertEquals(
            listOf("services_patched.jar", "oplus-services_patched.jar", "framework_patched.jar", "vold_patched",
                "service.incremental_patched.so", "libcamera_client_patched.so"),
            ifx.files.filter { it.role in PackageFile.GUARDED }.map { it.name },
        )
        assertTrue(ifx.file(PackageSets.GMS_PAYLOAD) != null && ifx.hasReturnApp)
        // HyperOS (0.4.31 on every phone; made for the Z Fold8 Ultra and Z Fold8): services.jar, Xiaomi's guard jars,
        // framework.jar and the guest vold (r7), the native camera client (r10), the 15 common payloads and bt.
        val hyper = PackageSets.HYPER_F8U
        assertEquals(Images.ID_HYPEROS_LHASA, hyper.imageId)
        assertEquals("out/overlays_hyperos_r11", hyper.sourceDir)
        assertEquals(34, hyper.files.size)
        assertEquals(listOf("socket_exec", "libaudiohal_aidl_patched.so", "MiWallpaper_patched.apk"),
            hyper.files.filter { it.role == FileRole.GUEST_FIX }.map { it.name })
        assertEquals(
            listOf("services_patched.jar", "miui-services_patched.jar", "apprecovery.proinstaller_patched.jar", "framework_patched.jar",
                "miui-wifi-service_patched.jar", "miui-telephony-common_patched.jar", "vold_patched", "service.incremental_patched.so",
                "libcamera_client_patched.so"),
            hyper.files.filter { it.role in PackageFile.GUARDED }.map { it.name },
        )
        assertTrue(hyper.hasReturnApp && "vold_patched" in hyper.names)
        // The Z Fold8's Find N6 set (0.4.14, pull request #1): the Fold's files and five Fold8 fixes.
        val f8 = PackageSets.N6_F8
        assertEquals(Images.ID_FIND_N6, f8.imageId)
        assertEquals("out/overlays_n6_fold8", f8.sourceDir)
        assertEquals(n6.files, f8.files.filter { it.role != FileRole.GUEST_FIX })
        assertEquals(
            listOf("Fold8SystemUI.apk", "Fold8AngleRelay.dex", "Fold8Aod.apk", "Fold8UMS.apk", "Fold8Instant.apk"),
            f8.files.filter { it.role == FileRole.GUEST_FIX }.map { it.name },
        )
        // Its base files are the Fold's, byte for byte (built from out/overlays_n6_r50).
        val fold = ShaList.parse(shas(n6)).entries
        val fold8 = ShaList.parse(shas(f8)).entries
        for ((name, hash) in fold) assertEquals(name, hash, fold8[name])
        // The Pixel Fold sets (pull request #1, the F8U's since 0.4.21): the Pixel files, the patched
        // SystemUI, the angle relay and each one's unfold RRO; the S26 Ultra's set has none of them.
        val hinge = listOf("Fold8PixelSystemUI.apk", "Fold8AngleRelay.dex")
        assertEquals(hinge + "OneOSPixelUnfold.apk", pixel.files.filter { it.role == FileRole.GUEST_FIX }.map { it.name })
        assertEquals(hinge + "Fold8PixelUnfold.apk", PackageSets.PIXEL_F8.files.filter { it.role == FileRole.GUEST_FIX }.map { it.name })
        assertEquals(PackageSets.PIXEL_SULTRA.files, pixel.files.filter { it.role != FileRole.GUEST_FIX })
        assertEquals(PackageSets.PIXEL_SULTRA.files, PackageSets.PIXEL_F8.files.filter { it.role != FileRole.GUEST_FIX })
        // The Pixel 9 Pro XL's set (0.4.22): the same files, from its own image.
        assertEquals(PackageSets.PIXEL_SULTRA.files, PackageSets.PIXEL9_SULTRA.files)
        assertEquals(Images.ID_PIXEL_KOMODO, PackageSets.PIXEL9_SULTRA.imageId)
        // One SystemUI build and one relay for both: the APK packs each once.
        val f8uSums = ShaList.parse(shas(pixel)).entries
        val f8Sums = ShaList.parse(shas(PackageSets.PIXEL_F8)).entries
        for (n in hinge) assertEquals(n, f8uSums[n], f8Sums[n])
        assertEquals(fold8["Fold8AngleRelay.dex"], f8uSums["Fold8AngleRelay.dex"])
        // Owner: leave out only dead weight.
        assertEquals(listOf("tethering_payload.img"), n6.omitted)
        assertEquals(listOf("tethering_payload.img", "OneOSWallpapersFoldGrid.apk"), x9u.omitted)
        assertEquals(listOf("tethering_payload.img"), pixel.omitted)
        for (s in listOf(n6, x9u)) assertTrue(s.id, PackageSets.CELLBROADCAST !in s.names)
        for (s in PackageSets.ALL) {
            assertEquals(s.names.size, s.names.toSet().size)
            assertTrue(s.omitted.none { it in s.names })
            assertTrue(s.names.none { it == Images.STAGE_NAME })
            assertTrue(s.id, s.names.containsAll(commonPayloads + "guards.txt" + "services_patched.jar"))
            assertEquals(s.id, FileRole.GUARDS, s.file("guards.txt")!!.role)
            // Executable: surfaceflinger_patched and vold_patched only (root's file_mode, deploy-phone.sh's push).
            for (f in s.files) assertEquals("${s.id} ${f.name}", if (f.name in setOf("surfaceflinger_patched", "vold_patched")) "0755" else "0644", f.mode)
            // The guest vold's two binaries: both or none (oneos_switch.sh and guest_prepare.sh refuse one alone).
            assertEquals(s.id, s.file("vold_patched") != null, s.file("service.incremental_patched.so") != null)
            // Back to One UI in the guest exactly when the set stages the in-guest app (guest_prepare.sh installs it).
            assertEquals(s.id, PackageSets.GUEST_APP in s.names, s.hasReturnApp)
        }
        // The ColorOS sets (0.4.8, rebuilt 2026-10-25): both guard jars, the guest vold
        // as gvld (ColorOS 17 CP2A.260605.016) and the in-guest app.
        for (s in listOf(n6, x9u)) {
            assertEquals(listOf("services_patched.jar", "oplus-services_patched.jar"), s.files.filter { it.role == FileRole.GUARD_JAR }.map { it.name })
            assertEquals(
                s.id,
                listOf("services_patched.jar", "oplus-services_patched.jar", "vold_patched", "service.incremental_patched.so"),
                s.files.filter { it.role in PackageFile.GUARDED }.map { it.name },
            )
            assertEquals(s.id, 15, s.files.count { it.role == FileRole.APEX_PAYLOAD })
            assertEquals(FileRole.SURFACEFLINGER, s.file("surfaceflinger_patched")!!.role)
            assertEquals(FileRole.GUEST_APP, s.file(PackageSets.GUEST_APP)!!.role)
            assertTrue(s.hasReturnApp)
        }
        assertEquals(n6.names - PackageSets.WALLPAPER_GRID_RRO, x9u.names)
        // The IN build's set (0.4.11): the CN set's files and its cell broadcast module, with
        // both guard jars and its own guest vold as gvld (its own pins in make-overlays.sh).
        assertEquals(x9u.names + PackageSets.CELLBROADCAST, xin.names)
        assertEquals(x9u.omitted, xin.omitted)
        assertEquals(16, xin.files.count { it.role == FileRole.APEX_PAYLOAD })
        assertEquals(
            listOf("services_patched.jar", "oplus-services_patched.jar", "vold_patched", "service.incremental_patched.so"),
            xin.files.filter { it.role in PackageFile.GUARDED }.map { it.name },
        )
        assertTrue(xin.hasReturnApp)
        val inShas = ShaList.parse(shas(xin)).entries
        // Its vold is not the CN one (the same code laid out 0x2000 later), so neither is its patch.
        assertTrue(inShas["vold_patched"] != ShaList.parse(shas(x9u)).entries["vold_patched"])
        assertEquals(ShaList.parse(shas(x9u)).entries[PackageSets.GUEST_APP], inShas[PackageSets.GUEST_APP])
        assertEquals(FileRole.RRO, n6.file("OneOSWallpapersFoldGrid.apk")!!.role)
        // 18 of the files are byte-identical in both ColorOS sets (deduplicated in the APK):
        // 14 payloads, surfaceflinger, the in-guest app and the guest vold's two binaries
        // (the images share one vold and service.incremental.so).
        val a = ShaList.parse(shas(n6)).entries
        val b = ShaList.parse(shas(x9u)).entries
        assertEquals(18, a.count { (n, h) -> b[n] == h })
        for (n in listOf("vold_patched", "service.incremental_patched.so", PackageSets.GUEST_APP)) assertEquals(n, a[n], b[n])
        // The Pixel set's files are its own but the in-guest app, the same build in every set.
        val c = ShaList.parse(shas(pixel)).entries
        val colorOs = (a.values + b.values).toSet()
        assertEquals(setOf(PackageSets.GUEST_APP), c.filterValues { it in colorOs }.keys)
        assertEquals(c[PackageSets.GUEST_APP], a[PackageSets.GUEST_APP])
    }

    /** 0.3.7: exactly the files a Pixel session uses (guest_prepare.sh), with their roles. */
    @Test
    fun pixelSetIsWhatAPixelSessionUses() {
        val roles = pixel.files.associate { it.name to it.role }
        assertEquals(FileRole.GUARD_JAR, roles["services_patched.jar"])
        assertEquals(FileRole.GUARD_JAR, roles["framework_patched.jar"])
        assertEquals(FileRole.GUARD_EXECUTABLE, roles["vold_patched"])
        assertEquals(FileRole.GUARD_LIBRARY, roles["service.incremental_patched.so"])
        assertEquals(FileRole.INJECTION_JAR, roles["oneos-telephony.jar"])
        assertEquals(FileRole.INJECTION_SHA256, roles["oneos-telephony.sha256"])
        assertEquals(FileRole.RRO, roles["OneOSPhoneInjection.apk"])
        assertEquals(FileRole.RRO, roles["OneOSPixelStatusBar.apk"])
        assertEquals(FileRole.GUEST_APP, roles[PackageSets.GUEST_APP])
        assertEquals(listOf("services_patched.jar", "framework_patched.jar", "vold_patched", "service.incremental_patched.so"), pixel.files.filter { it.role in PackageFile.GUARDED }.map { it.name })
        assertEquals("0755", pixel.file("vold_patched")!!.mode)
        assertEquals("0644", pixel.file("service.incremental_patched.so")!!.mode)
        // 22 payloads: every set's 15, Google's cellbroadcast/bt/nfcservices, the image's own
        // telephonycore/conscrypt/profiling (no platform framework-telecom.jar) and GmsCore.
        val payloads = pixel.files.filter { it.role == FileRole.APEX_PAYLOAD }.map { it.name }
        assertEquals(22, payloads.size)
        assertEquals(
            (commonPayloads + listOf("cellbroadcast", "bt", "nfcservices", "telephonycore", "conscrypt", "profiling", "gmssystem").map { "${it}_payload.img" }).toSet(),
            payloads.toSet(),
        )
        assertTrue(PackageSets.GMS_PAYLOAD in pixel.names)
        // Never mounted (the host keeps its tethering module): left out.
        assertTrue(PackageSets.TETHERING !in pixel.names)
        // Nothing of ColorOS: no oplus jars, no patched surfaceflinger (Google's is not patched), no fold grid.
        for (n in listOf("oplus-services_patched.jar", "oplus-telephony-common_patched.jar", "ims-ext-common_patched.jar", "surfaceflinger_patched", PackageSets.WALLPAPER_GRID_RRO)) {
            assertTrue(n, n !in pixel.names)
        }
        assertTrue(pixel.hasReturnApp)
        // The free-space gate's number is the files' total (out/overlays_pixel_f8u, 2026-10-26).
        assertEquals(536_494_620L, pixel.approxBytes)
        // Each image presents its Google services exactly when its sets stage GmsCore.
        for (p in Profiles.BUILT_IN) {
            for (choice in p.choices) {
                val image = Images.byId(choice.imageId)!!
                val set = PackageSets.byId(choice.packageSetId)!!
                assertEquals("${p.id} ${set.id}", image.presentsGms, set.file(PackageSets.GMS_PAYLOAD) != null)
            }
        }
    }

    @Test
    fun shaListsNameExactlyTheSetsFiles() {
        for (s in PackageSets.ALL) assertEquals(s.id, emptyList<String>(), s.check(shas(s)))
        // The Pixel list and a ColorOS one are not interchangeable.
        val pixelOnN6 = pixel.check(shas(n6))
        assertTrue(pixelOnN6.contains("pixel-f8u: oneos-telephony.jar missing"))
        assertTrue(pixelOnN6.contains("pixel-f8u: gmssystem_payload.img missing"))
        assertTrue(pixelOnN6.contains("pixel-f8u: oplus-services_patched.jar is not in this set"))
        assertTrue(pixelOnN6.contains("pixel-f8u: OneOSWallpapersFoldGrid.apk is not in this set"))
        val n6OnPixel = n6.check(shas(pixel))
        assertTrue(n6OnPixel.contains("n6-f8u: surfaceflinger_patched missing"))
        assertTrue(n6OnPixel.contains("n6-f8u: oneos-telephony.jar is not in this set"))
        assertTrue(n6OnPixel.contains("n6-f8u: OneOSPixelStatusBar.apk is not in this set"))
        assertTrue(x9u.check(shas(pixel)).contains("x9u-sultra: cellbroadcast_payload.img is not in this set"))
        val pixelTether = shas(pixel) + "${"a".repeat(64)}  tethering_payload.img\n"
        assertEquals(listOf("pixel-f8u: tethering_payload.img is left out of this set"), pixel.check(pixelTether))
        // Swapped lists fail: the fold grid is not in the S Ultra set.
        assertTrue(x9u.check(shas(n6)).contains("x9u-sultra: OneOSWallpapersFoldGrid.apk is left out of this set"))
        assertTrue(n6.check(shas(x9u)).contains("n6-f8u: OneOSWallpapersFoldGrid.apk missing"))

        val extra = shas(x9u) + "${"a".repeat(64)}  framework_patched.jar\n"
        assertEquals(listOf("x9u-sultra: framework_patched.jar is not in this set"), x9u.check(extra))
        val tether = shas(x9u) + "${"a".repeat(64)}  tethering_payload.img\n"
        assertEquals(listOf("x9u-sultra: tethering_payload.img is left out of this set"), x9u.check(tether))
        val dup = shas(x9u) + shas(x9u).lines().first() + "\n"
        assertEquals(listOf("${shas(x9u).lines().first().substringAfter("  ")} listed twice"), x9u.check(dup))
    }

    @Test
    fun guardsMatchTheGuardJars() {
        for (s in PackageSets.ALL) assertEquals(s.id, emptyList<String>(), s.checkGuards(guards(s), shas(s)))
        // N6 guards with the X9U jars: never mix builds. (The two images share one
        // vold and service.incremental.so, so only the guard jars differ.)
        val mixed = x9u.checkGuards(guards(n6), shas(x9u))
        assertEquals(2, mixed.size)
        assertTrue(mixed.all { it.startsWith("guards.txt: ") && it.contains("the set has") })
        // A guards.txt that lists something else, or misses a jar.
        assertTrue(x9u.checkGuards(guards(x9u) + "${"b".repeat(64)}  framework_patched.jar\n", shas(x9u)).single().contains("not a guard jar"))
        assertTrue(x9u.checkGuards(guards(x9u).lines().filterNot { it.endsWith("oplus-services_patched.jar") }.joinToString("\n"), shas(x9u)).single().contains("missing"))
        // The ColorOS sets carry the guards Google services need too (rebuilt 2026-10-25).
        for (s in listOf(n6, x9u)) for (t in PackageSets.GMS_GUARDS) assertTrue("${s.id} $t", Regex("\\b$t\\b").containsMatchIn(guards(s)))
    }

    /** 0.3.7: the Pixel set's guards.txt pins services_patched.jar and both gvld binaries; comment lines are fine. */
    @Test
    fun pixelGuardsPinTheGuestVoldToo() {
        val g = guards(pixel)
        val sums = shas(pixel)
        assertEquals(setOf("services_patched.jar", "framework_patched.jar", "vold_patched", "service.incremental_patched.so"), ShaList.parse(g).entries.keys)
        assertTrue(g.lines().first().startsWith("#"))
        assertEquals(emptyList<String>(), pixel.checkGuards(g, sums))
        // Only one of the two gvld binaries (a half-built set): refused, as the session scripts do.
        for (n in listOf("vold_patched", "service.incremental_patched.so")) {
            val half = g.lines().filterNot { it.endsWith("  $n") }.joinToString("\n")
            assertEquals(n, listOf("guards.txt: $n missing"), pixel.checkGuards(half, sums))
        }
        // A vold_patched that is not the set's.
        val wrong = g.replace(ShaList.parse(g).entries.getValue("vold_patched"), "c".repeat(64))
        assertTrue(pixel.checkGuards(wrong, sums).single().startsWith("guards.txt: vold_patched is cccccccccccc..., the set has "))
        // Something else listed.
        assertTrue(pixel.checkGuards(g + "${"b".repeat(64)}  OneOSGuest.apk\n", sums).single().contains("OneOSGuest.apk is not a guard jar of pixel-f8u"))
        // Without the comment lines naming the Google services guards: each one is missing (a switch would be refused).
        val bare = g.lines().filterNot { it.startsWith("#") }.joinToString("\n")
        assertEquals(PackageSets.GMS_GUARDS.map { "guards.txt does not name the $it guard Google services need" }, pixel.checkGuards(bare, sums))
        assertEquals(1, pixel.checkGuards(g.replace("appid15000", "appid"), sums).size)
        // Never mixed: a ColorOS set's guards do not vouch for the Pixel files, nor the other way round.
        val colorOsOnPixel = pixel.checkGuards(guards(n6), sums)
        assertTrue(colorOsOnPixel.any { it.startsWith("guards.txt: vold_patched is ") })
        assertTrue(colorOsOnPixel.any { it.startsWith("guards.txt: service.incremental_patched.so is ") })
        assertTrue(colorOsOnPixel.contains("guards.txt: oplus-services_patched.jar is not a guard jar of pixel-f8u"))
        assertTrue(colorOsOnPixel.any { it.startsWith("guards.txt: services_patched.jar is ") })
        val pixelOnN6 = n6.checkGuards(g, shas(n6))
        assertTrue(pixelOnN6.any { it.startsWith("guards.txt: vold_patched is ") })
        assertTrue(pixelOnN6.any { it.startsWith("guards.txt: service.incremental_patched.so is ") })
        assertTrue(pixelOnN6.contains("guards.txt: oplus-services_patched.jar missing"))
        assertTrue(x9u.checkGuards(g, shas(x9u)).isNotEmpty())
    }

    @Test
    fun shaListParsing() {
        val h = "0123456789abcdef".repeat(4)
        val ok = ShaList.parse("# comment\n$h  a.jar\n${h.uppercase()} *b_payload.img\n\n")
        assertEquals(mapOf("a.jar" to h, "b_payload.img" to h), ok.entries)
        assertTrue(ok.problems.isEmpty())
        assertEquals("$h  a.jar\n$h  b_payload.img\n", ShaList.render(ok.entries))

        for (bad in listOf("$h a.jar b", "$h  ../x", "$h  dir/x", "$h  .hidden", "${h.dropLast(1)}  a.jar", "$h\ta.jar")) {
            assertFalse(bad, ShaList.parse(bad).problems.isEmpty())
            assertTrue(bad, ShaList.parse(bad).entries.isEmpty())
        }
    }

    /** B2's checked-in lists (app-android/packages), when they exist, name exactly these sets. */
    @Test
    fun checkedInPackageListsMatchTheSets() {
        val dir = System.getProperty("oneos.packagesDir")?.let(::File)
        assumeTrue("app-android/packages not there yet", dir != null && dir.isDirectory)
        for (s in PackageSets.ALL) {
            val f = File(dir, "${s.id}.sha256")
            assumeTrue("${f.name} not there yet", f.isFile)
            assertEquals(s.id, emptyList<String>(), s.check(f.readText()))
        }
        // The IN sets' lists are gone with the image (0.3.7).
        assertEquals(PackageSets.ALL.map { "${it.id}.sha256" }.toSet(), dir!!.list()!!.filter { it.endsWith(".sha256") }.toSet())
    }

    @Test
    fun spaceToStage() {
        // AA17: about 22.5 GB for the S Ultra (X9U image + files + 11 GiB).
        val need = SpaceRule.toStage(Images.FIND_X9U, x9u)
        assertEquals("22.5 GB", Units.bytes(need))
        // The Pixel image is smaller: its own raw size and files.
        val pixelNeed = SpaceRule.toStage(Images.PIXEL_YOGI, pixel)
        assertEquals(6_559_223_808L + 536_494_620L + 11L * SpaceRule.GIB, pixelNeed)
        assertEquals("18.9 GB", Units.bytes(pixelNeed))
        assertEquals(11L * SpaceRule.GIB, SpaceRule.LAUNCH_BYTES)
        assertEquals(Images.FIND_X9U.rawBytes + SpaceRule.LAUNCH_BYTES, SpaceRule.toStage(Images.FIND_X9U, null))
    }
}
