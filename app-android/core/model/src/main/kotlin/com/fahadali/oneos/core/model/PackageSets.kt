package com.fahadali.oneos.core.model

import java.util.Locale

/** What a staged file is for; decides its mode and how the scripts use it (guest_prepare.sh). */
enum class FileRole {
    /** services / oplus-services: carry the storage, installd, sysrq and the other guards; pinned by guards.txt. */
    GUARD_JAR,
    /**
     * `vold_patched`: the guest's own vold registered as "gvld" (run 44,
     * docs/inplace-return.md), bound over the guest's /system/bin/vold; pinned
     * by guards.txt together with [GUARD_LIBRARY] and the guarded services.jar
     * (both binaries or none); executable.
     */
    GUARD_EXECUTABLE,
    /** `service.incremental_patched.so`: looks up "gvld"; pinned by guards.txt with [GUARD_EXECUTABLE]. */
    GUARD_LIBRARY,
    /** Other patched framework jars (bound over the guest's). */
    FRAMEWORK_JAR,
    /** `guards.txt`: SHA-256 of the guard files, checked by guest_prepare.sh before anything is bound. */
    GUARDS,
    /** The guest surfaceflinger with the QTI composer skip; executable. */
    SURFACEFLINGER,
    /** An APEX payload loop-mounted read-only for the guest. */
    APEX_PAYLOAD,
    /** A resource overlay copied into the guest (its /oem/overlay). */
    RRO,
    /** `OneOSGuest.apk` (app-android/guest): OneOS inside the guest, Back to One UI; copied into the guest's /oem/app. */
    GUEST_APP,
    /**
     * `oneos-telephony.jar`: the signal poll TeleService's own
     * TelephonyComponentFactory injection loads, from a read-only overlay layer
     * on the guest's system_ext (docs/pixel-guest.md, "Signal bars").
     */
    INJECTION_JAR,
    /**
     * `oneos-telephony.sha256`: the guest's telephony-common.jar and
     * TeleService.apk the jar was built and checked against; guest_prepare.sh
     * uses the jar only while both still match.
     */
    INJECTION_SHA256,
    /**
     * A Galaxy Z Fold8 fix for the Find N6 image (n6-f8, 0.4.14, ported from
     * pull request #1): the patched SystemUI bound over the image's, the
     * hinge-angle relay dex, and OPPO's AOD, UMS and Instant APKs from the
     * same firmware, installed in the guest (switcher/assets/fold8_coloros.sh).
     * In hyper-f8u: socket_exec (guest_start.sh starts hyos_spawner with it)
     * and libaudiohal_aidl_patched.so (bound over the image's). Never run in
     * One UI.
     */
    GUEST_FIX,
}

/** One file of a set, by its flat name in `$STAGE`. */
data class PackageFile(val name: String, val role: FileRole) {
    /**
     * Mode in `$STAGE`: surfaceflinger_patched and vold_patched are executable
     * (as scripts/deploy-phone.sh pushes them; guest_prepare.sh binds them over
     * the guest's binaries), everything else 0644. Root's `file_mode` agrees.
     */
    val mode: String get() = if (role in EXECUTABLE) "0755" else "0644"

    companion object {
        private val EXECUTABLE = setOf(FileRole.SURFACEFLINGER, FileRole.GUARD_EXECUTABLE)

        /** What guards.txt pins: the guard jars and the guest vold's two binaries. */
        val GUARDED: Set<FileRole> = setOf(FileRole.GUARD_JAR, FileRole.GUARD_EXECUTABLE, FileRole.GUARD_LIBRARY)
    }
}

/**
 * The patched files staged next to one image (AA6), as data: flat names and
 * roles only. The SHA-256 of each file is in the checked-in
 * `app-android/packages/<id>.sha256` (the packaging task fails when a built
 * file differs), so this list and that file must name the same files; see
 * [check].
 */
data class PackageSet(
    val id: String,
    /** [ImageRef.id] the overlays were built from; never mix sets and images. */
    val imageId: String,
    /** Server folder the packaging task imports, relative to the repo root. */
    val sourceDir: String,
    val files: List<PackageFile>,
    /** Files in [sourceDir] this set leaves out on purpose. */
    val omitted: List<String>,
    /** Total bytes of [files] (the Pixel set of 2026-10-24, the ColorOS sets of 2026-10-25), for the free-space gate. */
    val approxBytes: Long,
    /**
     * The set stages OneOS's in-guest app (Back to One UI in the other
     * system), so the Switch dialog may say so: guest_prepare.sh copies a
     * staged `OneOSGuest.apk` into the guest's /oem/app (`ONEOS_GUEST_APP`,
     * default on, which session.env cannot set) and oneos_switch.sh's watcher
     * takes its request. True exactly when the set carries
     * [PackageSets.GUEST_APP] (the Pixel set since 0.3.7, the ColorOS sets since 0.4.8).
     */
    val hasReturnApp: Boolean = false,
) {
    val names: List<String> get() = files.map { it.name }

    fun file(name: String): PackageFile? = files.firstOrNull { it.name == name }

    /**
     * Problems with a `<sha256>  <name>` list for this set (the checked-in
     * packages/<id>.sha256 or a built folder's sha256sum); empty = it names
     * exactly [files], once each, with well-formed hashes.
     */
    fun check(shaList: String): List<String> {
        val parsed = ShaList.parse(shaList)
        val p = parsed.problems.toMutableList()
        val listed = parsed.entries.keys
        for (n in names) if (n !in listed) p += "$id: $n missing"
        for (n in listed) {
            when {
                n in omitted -> p += "$id: $n is left out of this set"
                file(n) == null -> p += "$id: $n is not in this set"
            }
        }
        return p
    }

    /**
     * Problems with this set's guards.txt against the set's own hashes: it
     * must list every guard file ([PackageFile.GUARDED]: the guard jars and
     * the guest vold's vold_patched and service.incremental_patched.so),
     * nothing else, with the file's SHA-256
     * (what oneos_switch.sh and guest_prepare.sh check before binding them).
     * A set with the GmsCore payload also needs guards.txt's comment lines to
     * name the guards Google services need ([PackageSets.GMS_GUARDS]), or
     * every switch with it would be refused.
     */
    fun checkGuards(guardsText: String, shaList: String): List<String> {
        val guards = ShaList.parse(guardsText)
        val shas = ShaList.parse(shaList).entries
        val p = guards.problems.map { "guards.txt: $it" }.toMutableList()
        val jars = files.filter { it.role in PackageFile.GUARDED }.map { it.name }
        for (j in jars) {
            val g = guards.entries[j]
            when {
                g == null -> p += "guards.txt: $j missing"
                shas[j] == null -> p += "$j has no hash in the set list"
                g != shas[j] -> p += "guards.txt: $j is ${g.take(12)}..., the set has ${shas[j]!!.take(12)}..."
            }
        }
        for (n in guards.entries.keys) if (n !in jars) p += "guards.txt: $n is not a guard jar of $id"
        if (file(PackageSets.GMS_PAYLOAD) != null) {
            // As the scripts read them: any comment line, whole words (grep -w).
            val words = guardsText.lineSequence().map { it.removeSuffix("\r") }.filter { it.startsWith("#") }
                .flatMap { it.split(Regex("[^A-Za-z0-9_]+")) }.toSet()
            for (t in PackageSets.GMS_GUARDS) if (t !in words) p += "guards.txt does not name the $t guard Google services need"
        }
        return p
    }
}

object PackageSets {
    const val ID_N6_F8U = "n6-f8u"
    const val ID_N6_F8 = "n6-f8"
    const val ID_X9U_SULTRA = "x9u-sultra"
    const val ID_X9U_IN_SULTRA = "x9u-in-sultra"
    const val ID_PIXEL_F8U = "pixel-f8u"
    const val ID_PIXEL_F8 = "pixel-f8"
    const val ID_PIXEL_SULTRA = "pixel-sultra"
    const val ID_HYPER_F8U = "hyper-f8u"
    const val ID_PIXEL9_SULTRA = "pixel9-sultra"
    const val ID_INFINITYX = "infinityx"

    /** Never mounted (guest_prepare.sh only keeps the host module); left out of every set. */
    const val TETHERING = "tethering_payload.img"

    /** Fold-only wallpaper grid (guest_prepare.sh binds it only for the fold form). */
    const val WALLPAPER_GRID_RRO = "OneOSWallpapersFoldGrid.apk"

    /**
     * Google's mainline cellbroadcast module (the Pixel image; the CN builds have
     * CellBroadcastService as an app and no payload). guest_prepare.sh mounts it
     * when it is staged, and refuses an app session whose image has the module
     * but no staged payload, except a module built from source
     * (com.android.cellbroadcast.*, Infinity-X): One UI's stays then, the
     * android.uid.networkstack group has One UI's signer ([PackageSets.INFINITYX]).
     */
    const val CELLBROADCAST = "cellbroadcast_payload.img"

    /** The Pixel image's GmsCore (its product gmssystem APEX); guest_prepare.sh presents it ([ImageRef.presentsGms]). */
    const val GMS_PAYLOAD = "gmssystem_payload.img"

    /** OneOS inside the guest (app-android/guest): Back to One UI from its screen or tile ([PackageSet.hasReturnApp]). */
    const val GUEST_APP = "OneOSGuest.apk"

    /**
     * The guards guards.txt's comment lines must name before a session
     * presents Google services (guest_prepare.sh and oneos_switch.sh refuse
     * otherwise): the phone's gatekeeper, FRP partition and apexd stay One
     * UI's, and guest apps get app ids from 15000.
     */
    val GMS_GUARDS: List<String> = listOf("gatekeeper", "pdb", "apexd", "appid15000")

    /** The APEX payloads every set carries (guest_prepare.sh mounts each one). */
    private val COMMON_PAYLOADS: List<String> = listOf(
        "art", "i18n", "runtime", "sdkext", "crashrecovery", "extservices", "permission", "wifi",
        "appsearch", "mediaprovider", "adservices", "rkpd", "healthfitness", "ondevicepersonalization",
        "configinfrastructure",
    )

    /**
     * The ColorOS sets' files (root's SET_COMMON). Since 0.4.8 (rebuilt
     * 2026-10-25 with every guard the Pixel set has) also the guest vold as
     * "gvld" for ColorOS 17 CP2A.260605.016 (vold_patched,
     * service.incremental_patched.so; oplus-services_patched.jar looks up
     * gvld too) and OneOS's in-guest app.
     */
    private val COMMON: List<PackageFile> = listOf(
        PackageFile("services_patched.jar", FileRole.GUARD_JAR),
        PackageFile("oplus-services_patched.jar", FileRole.GUARD_JAR),
        PackageFile("vold_patched", FileRole.GUARD_EXECUTABLE),
        PackageFile("service.incremental_patched.so", FileRole.GUARD_LIBRARY),
        PackageFile(GUEST_APP, FileRole.GUEST_APP),
        PackageFile("ims-ext-common_patched.jar", FileRole.FRAMEWORK_JAR),
        PackageFile("oplus-telephony-common_patched.jar", FileRole.FRAMEWORK_JAR),
        PackageFile("surfaceflinger_patched", FileRole.SURFACEFLINGER),
        PackageFile("guards.txt", FileRole.GUARDS),
    ) + COMMON_PAYLOADS.map { PackageFile("${it}_payload.img", FileRole.APEX_PAYLOAD) }

    val N6_F8U = PackageSet(
        id = ID_N6_F8U,
        imageId = Images.ID_FIND_N6,
        sourceDir = "out/overlays_n6_r50",
        files = COMMON + PackageFile(WALLPAPER_GRID_RRO, FileRole.RRO),
        omitted = listOf(TETHERING),
        approxBytes = 267_168_254L,
        hasReturnApp = true,
    )

    /**
     * Find N6 image on the Galaxy Z Fold8 (0.4.14, ported from pull request
     * #1): the Fold's set and the [FileRole.GUEST_FIX] files
     * scripts/make-fold8-coloros.py builds from it (cover status bar and Quick
     * Settings, hinge angle, wallpaper handoff, AOD, widgets, taskbar).
     */
    val N6_F8 = PackageSet(
        id = ID_N6_F8,
        imageId = Images.ID_FIND_N6,
        sourceDir = "out/overlays_n6_fold8",
        files = N6_F8U.files + listOf(
            "Fold8SystemUI.apk", "Fold8AngleRelay.dex", "Fold8Aod.apk", "Fold8UMS.apk", "Fold8Instant.apk",
        ).map { PackageFile(it, FileRole.GUEST_FIX) },
        omitted = listOf(TETHERING),
        approxBytes = 662_821_337L,
        hasReturnApp = true,
    )

    val X9U_SULTRA = PackageSet(
        id = ID_X9U_SULTRA,
        imageId = Images.ID_FIND_X9U,
        sourceDir = "out/overlays_x9u_r50",
        files = COMMON,
        omitted = listOf(TETHERING, WALLPAPER_GRID_RRO),
        approxBytes = 267_147_970L,
        hasReturnApp = true,
    )

    /** Find X9 Ultra IN image, the bar phones (0.4.11): the CN set's files and its cell broadcast module. */
    val X9U_IN_SULTRA = PackageSet(
        id = ID_X9U_IN_SULTRA,
        imageId = Images.ID_FIND_X9U_IN,
        sourceDir = "out/overlays_x9u_in_r51",
        files = COMMON + PackageFile(CELLBROADCAST, FileRole.APEX_PAYLOAD),
        omitted = listOf(TETHERING, WALLPAPER_GRID_RRO),
        approxBytes = 327_686_370L,
        hasReturnApp = true,
    )

    /** The Pixel sets' files (root's SET_PIXEL): the same for every Pixel image. */
    private val PIXEL_FILES: List<PackageFile> = listOf(
        PackageFile("services_patched.jar", FileRole.GUARD_JAR),
        // 0.4.24: the image's framework.jar with One UI's camera transaction codes (pixel_camera_abi.py).
        PackageFile("framework_patched.jar", FileRole.GUARD_JAR),
        PackageFile("vold_patched", FileRole.GUARD_EXECUTABLE),
        PackageFile("service.incremental_patched.so", FileRole.GUARD_LIBRARY),
        PackageFile("guards.txt", FileRole.GUARDS),
        PackageFile("oneos-telephony.jar", FileRole.INJECTION_JAR),
        PackageFile("oneos-telephony.sha256", FileRole.INJECTION_SHA256),
        PackageFile("OneOSPhoneInjection.apk", FileRole.RRO),
        PackageFile("OneOSPixelStatusBar.apk", FileRole.RRO),
        PackageFile(GUEST_APP, FileRole.GUEST_APP),
    ) + (COMMON_PAYLOADS + listOf("cellbroadcast", "bt", "nfcservices", "telephonycore", "conscrypt", "profiling", "gmssystem"))
        .map { PackageFile("${it}_payload.img", FileRole.APEX_PAYLOAD) }

    /**
     * The fold hinge for a Pixel 11 Fold guest (pull request #1): Pixel's
     * SystemUIGoogle with the adapter that reads Samsung's folding-angle sensor
     * through the system-UID relay (fold8_pixel.sh), the relay, and an RRO
     * turning on the unfold transition and its hinge-angle gate.
     */
    private fun pixelHinge(unfoldRro: String): List<PackageFile> =
        listOf("Fold8PixelSystemUI.apk", "Fold8AngleRelay.dex", unfoldRro).map { PackageFile(it, FileRole.GUEST_FIX) }

    /**
     * Pixel 11 Fold image, Z Fold (F8U) (0.3.7), from out/overlays_pixel_f8u
     * (out/overlays_pixel_yogi plus the hinge, scripts/make-fold8-pixel.py --variant f8u):
     * exactly what a Pixel session uses (guest_prepare.sh). Guarded:
     * services_patched.jar with the guest vold as "gvld" (vold_patched,
     * service.incremental_patched.so). The signal poll (oneos-telephony.jar,
     * its .sha256 and OneOSPhoneInjection.apk), Pixel's status bar padding RRO
     * and OneOSGuest.apk. 22 APEX payloads: the 15 of every set, Google's
     * cellbroadcast, bt and nfcservices modules, the image's own telephonycore,
     * conscrypt and profiling (it has no platform framework-telecom.jar), and
     * GmsCore. No oplus jars, surfaceflinger or fold grid: the image has no
     * oplus-services.jar, Google's surfaceflinger is not patched, the grid is
     * for ColorOS's wallpaper app.
     */
    val PIXEL_F8U = PackageSet(
        id = ID_PIXEL_F8U,
        imageId = Images.ID_PIXEL_YOGI,
        sourceDir = "out/overlays_pixel_f8u",
        files = PIXEL_FILES + pixelHinge("OneOSPixelUnfold.apk"),
        omitted = listOf(TETHERING),
        approxBytes = 536_494_620L,
        hasReturnApp = true,
    )

    /**
     * Pixel 11 Pro image, Galaxy S26 Ultra (0.4.10), from out/overlays_pixel_kodiak:
     * the Pixel set's files, built from that image (all the guards, the guest
     * vold as gvld, the signal poll, the status bar padding RRO, GmsCore).
     */
    val PIXEL_SULTRA = PackageSet(
        id = ID_PIXEL_SULTRA,
        imageId = Images.ID_PIXEL_KODIAK,
        sourceDir = "out/overlays_pixel_kodiak",
        files = PIXEL_FILES,
        omitted = listOf(TETHERING),
        approxBytes = 485_702_461L,
        hasReturnApp = true,
    )

    /** Pixel's base payloads plus the hinge, with the Fold8's unfold RRO (its measured 48dp bar and AOD too). */
    val PIXEL_F8 = PackageSet(
        id = ID_PIXEL_F8,
        imageId = Images.ID_PIXEL_YOGI,
        sourceDir = "out/overlays_pixel_fold8",
        files = PIXEL_FILES + pixelHinge("Fold8PixelUnfold.apk"),
        omitted = listOf(TETHERING),
        approxBytes = 536_494_620L,
        hasReturnApp = true,
    )

    /**
     * Pixel 9 Pro XL image, Galaxy S25 Ultra, S25, S25+ and S24 Ultra (0.4.22), from
     * out/overlays_pixel_komodo: the Pixel set's files, built from that image (all
     * the guards, the guest vold as gvld, the signal poll, the status bar padding
     * RRO, GmsCore). Its services.jar keeps the OneOS classes in a dex of their
     * own (scripts/make-overlays.sh: classes2 was past 65536 method references).
     */
    val PIXEL9_SULTRA = PackageSet(
        id = ID_PIXEL9_SULTRA,
        imageId = Images.ID_PIXEL_KOMODO,
        sourceDir = "out/overlays_pixel_komodo",
        files = PIXEL_FILES,
        omitted = listOf(TETHERING),
        approxBytes = 490_870_923L,
        hasReturnApp = true,
    )

    /**
     * Infinity-X 4.1, the Find X9 Ultra build, on every phone (0.4.27), from
     * out/overlays_infinityx: an AOSP-built system with OPPO's framework jars, so
     * it takes from both sides. Guarded: services_patched.jar and
     * oplus-services_patched.jar with every guard (and the wipe guard, which its
     * duress-PIN wipe needs), the guest vold as gvld for its own vold build, and
     * One UI's camera transaction codes in framework_patched.jar and
     * libcamera_client_patched.so. The AOSP signal poll (no
     * oplus-telephony-common.jar), the IMS extension backoff, OneOSGuest.apk and
     * 21 APEX payloads: the 15 of every set, its bt and nfcservices modules, its
     * telephonycore, conscrypt and profiling (no platform framework-telecom.jar,
     * as Pixel) and GmsCore (its gmssystem APEX, Pixel's layout). No
     * surfaceflinger (no QTI composer client in it). Its cellbroadcast module is
     * left out (0.4.28): built from source, it is signed with the build's
     * networkstack key, and with One UI's tethering and NetworkStack in the
     * guest system_server died on "Signature mismatch on system package
     * com.android.cellbroadcastservice for shared user android.uid.networkstack"
     * (S25 Ultra session 26ea59af). The guest keeps One UI's, as for tethering.
     */
    val INFINITYX = PackageSet(
        id = ID_INFINITYX,
        imageId = Images.ID_INFINITYX,
        sourceDir = "out/overlays_infinityx_r2",
        files = listOf(
            PackageFile("services_patched.jar", FileRole.GUARD_JAR),
            PackageFile("oplus-services_patched.jar", FileRole.GUARD_JAR),
            PackageFile("framework_patched.jar", FileRole.GUARD_JAR),
            PackageFile("vold_patched", FileRole.GUARD_EXECUTABLE),
            PackageFile("service.incremental_patched.so", FileRole.GUARD_LIBRARY),
            PackageFile("libcamera_client_patched.so", FileRole.GUARD_LIBRARY),
            PackageFile("guards.txt", FileRole.GUARDS),
            PackageFile("ims-ext-common_patched.jar", FileRole.FRAMEWORK_JAR),
            PackageFile("oneos-telephony.jar", FileRole.INJECTION_JAR),
            PackageFile("oneos-telephony.sha256", FileRole.INJECTION_SHA256),
            PackageFile("OneOSPhoneInjection.apk", FileRole.RRO),
            PackageFile(GUEST_APP, FileRole.GUEST_APP),
            // 0.4.33: the status bar's corner padding, as on the Pixel images (r2).
            PackageFile("OneOSPixelStatusBar.apk", FileRole.RRO),
        ) + (COMMON_PAYLOADS + listOf("bt", "nfcservices", "telephonycore", "conscrypt", "profiling", "gmssystem"))
            .map { PackageFile("${it}_payload.img", FileRole.APEX_PAYLOAD) },
        omitted = listOf(TETHERING, CELLBROADCAST),
        approxBytes = 531_537_753L,
        hasReturnApp = true,
    )

    /**
     * HyperOS image (docs/hyperos.md), made for the Z Fold (F8U) and from 0.4.31 on every phone, from
     * out/overlays_hyperos_r10: services_patched.jar and Xiaomi's two guarded
     * jars (miui-services, apprecovery.proinstaller), framework.jar with One
     * UI's camera status format, miui-wifi-service.jar with checked HAL
     * extension casts, miui-telephony-common.jar without an IMS phone, the signal poll, the QTI IMS bind backoff,
     * socket_exec (starts hyos_spawner, which runs the launcher; r9: with --seqpacket, unused since run 12),
     * libaudiohal_aidl_patched.so (the guest audioserver without an audio
     * HAL adapter vendor extension), OneOSGuest.apk and the 16 APEX payloads
     * guest_prepare.sh mounts from this image (the 15 of every set and bt).
     * r7: the guest vold as "gvld" (vold_patched, service.incremental_patched.so;
     * services and miui-services look up gvld), so Back to One UI returns in place.
     * r8: services.jar's second built-in panel (the fold's cover) neither private nor
     * own-content-only (patches/qti_builtin_display_public.py).
     * r10 (0.4.31): One UI's camera transaction codes in framework_patched.jar
     * (patches/pixel_camera_abi.py) and libcamera_client_patched.so, the native
     * camera client with them (patches/pixel_camera_native.py); OneOSGuest.apk 0.2.
     */
    val HYPER_F8U = PackageSet(
        id = ID_HYPER_F8U,
        imageId = Images.ID_HYPEROS_LHASA,
        sourceDir = "out/overlays_hyperos_r11",
        files = listOf(
            PackageFile("services_patched.jar", FileRole.GUARD_JAR),
            PackageFile("miui-services_patched.jar", FileRole.GUARD_JAR),
            PackageFile("apprecovery.proinstaller_patched.jar", FileRole.GUARD_JAR),
            PackageFile("framework_patched.jar", FileRole.GUARD_JAR),
            PackageFile("miui-wifi-service_patched.jar", FileRole.GUARD_JAR),
            PackageFile("miui-telephony-common_patched.jar", FileRole.GUARD_JAR),
            PackageFile("vold_patched", FileRole.GUARD_EXECUTABLE),
            PackageFile("service.incremental_patched.so", FileRole.GUARD_LIBRARY),
            PackageFile("libcamera_client_patched.so", FileRole.GUARD_LIBRARY),
            PackageFile("guards.txt", FileRole.GUARDS),
            PackageFile("oneos-telephony.jar", FileRole.INJECTION_JAR),
            PackageFile("oneos-telephony.sha256", FileRole.INJECTION_SHA256),
            PackageFile("OneOSPhoneInjection.apk", FileRole.RRO),
            PackageFile("ims-ext-common_patched.jar", FileRole.FRAMEWORK_JAR),
            PackageFile(GUEST_APP, FileRole.GUEST_APP),
            PackageFile("socket_exec", FileRole.GUEST_FIX),
            PackageFile("libaudiohal_aidl_patched.so", FileRole.GUEST_FIX),
            // 0.4.33: the wallpaper app starts on a one-panel phone (r11).
            PackageFile("MiWallpaper_patched.apk", FileRole.GUEST_FIX),
        ) + (COMMON_PAYLOADS + "bt").map { PackageFile("${it}_payload.img", FileRole.APEX_PAYLOAD) },
        omitted = listOf(TETHERING),
        approxBytes = 437_391_809L,
        hasReturnApp = true,
    )

    val ALL: List<PackageSet> = listOf(N6_F8U, X9U_SULTRA, X9U_IN_SULTRA, PIXEL_F8U, PIXEL_SULTRA, N6_F8, PIXEL_F8, PIXEL9_SULTRA, INFINITYX, HYPER_F8U)

    fun byId(id: String, sets: List<PackageSet> = ALL): PackageSet? = sets.firstOrNull { it.id == id }
}

/**
 * `<sha256>  <name>` lines, as sha256sum prints them (a `*` before the name,
 * binary mode, is accepted). Used for packages/<set>.sha256, guards.txt and
 * `$STAGE/files.sha256`. Lines starting with `#` are comments; names are flat
 * (no `/`, no leading `.`, no spaces).
 */
object ShaList {
    data class Parsed(val entries: Map<String, String>, val problems: List<String>)

    private val LINE = Regex("""^([0-9a-fA-F]{64}) [ *](\S+)$""")
    private val NAME = Regex("""^[A-Za-z0-9_][A-Za-z0-9._+-]*$""")

    fun parse(text: String): Parsed {
        val entries = linkedMapOf<String, String>()
        val problems = mutableListOf<String>()
        text.lineSequence().forEachIndexed { i, raw ->
            val line = raw.removeSuffix("\r")
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val m = LINE.matchEntire(line)
            if (m == null) {
                problems += "line ${i + 1} is not '<sha256>  <name>'"
                return@forEachIndexed
            }
            val name = m.groupValues[2]
            if (!NAME.matches(name)) {
                problems += "line ${i + 1}: '$name' is not a flat file name"
                return@forEachIndexed
            }
            if (name in entries) problems += "$name listed twice"
            entries[name] = m.groupValues[1].lowercase(Locale.ROOT)
        }
        return Parsed(entries, problems)
    }

    /** sha256sum layout, sorted by name, one trailing newline. */
    fun render(entries: Map<String, String>): String =
        entries.entries.sortedBy { it.key }.joinToString("") { "${it.value}  ${it.key}\n" }
}
