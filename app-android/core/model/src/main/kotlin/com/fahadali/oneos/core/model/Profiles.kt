package com.fahadali.oneos.core.model

import java.util.Locale

/**
 * One image a profile may run, with the file set staged next to it (built
 * from that image by scripts/make-overlays.sh). The staged image decides the
 * set: never another set with that image, never this set with another image.
 */
data class ImageChoice(
    /** [ImageRef.id]. */
    val imageId: String,
    /** [PackageSet.id] staged next to that image on this phone. */
    val packageSetId: String,
    /** A session has run on this phone with this image; false = the untested tick at Switch. */
    val tested: Boolean,
)

/**
 * A phone this build has OS images and file sets for (build 2, AA3). A
 * phone matches only when its model number starts with [modelPrefix] AND
 * `ro.product.device` is [codename] AND its SoC model is [socModel]. The probe
 * builds matched model OR codename (the old TestedDevice), which would have
 * let an Exynos variant or a renamed model through.
 */
data class PhoneProfile(
    /** Stable id: `ONEOS_PROFILE` in session.env, the stage marker, logs. */
    val id: String,
    /** "Galaxy S26 Ultra". */
    val label: String,
    /** `SM-S948`: every regional variant (U, B, N, W, 0) starts with it. */
    val modelPrefix: String,
    /** `ro.product.device`, e.g. `m3q`. */
    val codename: String,
    /** `ro.soc.model` (or the model of `ro.board.platform` when the prop is empty), e.g. `SM8850`. */
    val socModel: String,
    /** BAR profiles get the phone form in session.env and no fold steps. */
    val form: FormFactor,
    /** The images this phone may run, each with its set (0.3.3: more than one); the first is its usual one. */
    val choices: List<ImageChoice>,
    /** False: known, but this build refuses to switch ([disabledReason] is the BLOCK). */
    val enabled: Boolean = true,
    val disabledReason: String? = null,
    /** Extra switch-time warnings for this phone (no tick needed). */
    val notes: List<Finding> = emptyList(),
) {
    fun matches(model: String, device: String, soc: String): Boolean =
        model.trim().uppercase(Locale.ROOT).startsWith(modelPrefix) &&
            device.trim().equals(codename, ignoreCase = true) &&
            soc.trim().equals(socModel, ignoreCase = true)

    fun matches(f: DeviceFacts): Boolean = matches(f.model, f.device, f.soc.model)

    /** The choice for [imageId]; null = this phone does not take that image. */
    fun choice(imageId: String): ImageChoice? = choices.firstOrNull { it.imageId == imageId }

    /** The images of [choices] this build has, in order. */
    fun images(all: List<ImageRef> = Images.ALL): List<ImageRef> = choices.mapNotNull { Images.byId(it.imageId, all) }

    /** The choice "Choose a system" preselects: the first one that has run on this phone, else the first. */
    val recommended: ImageChoice? get() = choices.firstOrNull { it.tested } ?: choices.firstOrNull()

    /** Some image has run on this phone (the device report's "Tested phone"). */
    val testedWithAnyImage: Boolean get() = choices.any { it.tested }

    /** "SM-S948* / m3q / SM8850". */
    val ids: String get() = "$modelPrefix* / $codename / $socModel"
}

/** [profile] is the one profile all three ids match; null when none (or, fail closed, more than one) does. */
data class ProfileResolution(
    val profile: PhoneProfile?,
    /** When nothing matched: the profile with the same model prefix (else codename), for the message. */
    val nearest: PhoneProfile?,
    /** What differs from [nearest], e.g. "codename pa3q (profile: m3q)". */
    val mismatches: List<String>,
)

/** The phones build 2 knows (PLAN.md "Profiles"). Built in; fix packs take this over later. */
object Profiles {
    const val ID_F8U = "f8u"
    const val ID_S_ULTRA_8850 = "s-ultra-8850"
    const val ID_S_ULTRA_8750 = "s-ultra-8750"
    const val ID_S_8750 = "s-8750"
    const val ID_FOLD7 = "f7"
    const val ID_S_PLUS_8750 = "s-plus-8750"
    const val ID_FOLD8 = "f8"
    const val ID_S_8850 = "s-8850"
    const val ID_S_8850_JP = "s-8850-jp"
    const val ID_S_PLUS_8850 = "s-plus-8850"
    const val ID_S_ULTRA_8650 = "s-ultra-8650"
    const val ID_S_8550 = "s-8550"
    const val ID_S_PLUS_8550 = "s-plus-8550"
    const val ID_S_PLUS_9965 = "s-plus-9965"
    const val ID_FLIP7 = "flip7"
    const val ID_A56 = "a56"
    const val ID_A57 = "a57"
    const val ID_FLIP5 = "flip5"
    const val ID_FOLD6 = "f6"
    const val ID_A07 = "a07"

    /** The SM8750 and SM8650 phones' note ([PhoneProfile.notes]) and its [Finding.reason]. */
    const val ID_NOTE_OLDER_VENDOR = "profile-vendor-older"
    const val R_OLDER_VENDOR = "older-vendor"

    /** The SM8550 phones' note: the system will most likely not start there. */
    const val ID_NOTE_EXPECT_NO_START = "profile-expect-no-start"
    const val R_EXPECT_NO_START = "expect-no-start"

    /**
     * Both S Ultra profiles and the Galaxy S25: the Find X9 Ultra CN build,
     * tested on all three (owner, 2026-10-25; 0.3.3-0.3.6 also took the IN build,
     * gone in 0.3.7). The set is
     * named for the S Ultra but holds nothing per phone beyond the bar form (no
     * fold grid).
     */
    /**
     * 0.4.11: the Find X9 Ultra IN (global) build on every bar phone that
     * takes the CN build, listed first (owner: "IN build is just better").
     * Untested, so a phone where the CN build has run still preselects that.
     */
    private val X9U_IN_CHOICE = ImageChoice(Images.ID_FIND_X9U_IN, PackageSets.ID_X9U_IN_SULTRA, tested = false)

    /** The same image where a tester's session has reached the home screen (docs/compatibility.md). */
    private val X9U_IN_TESTED = X9U_IN_CHOICE.copy(tested = true)

    private val S_ULTRA_CHOICES = listOf(
        X9U_IN_CHOICE,
        ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = true),
    )

    /**
     * 0.4.22: the Pixel 9 Pro XL image on the phones without SVE (SM8750, SM8650),
     * untested. Its system code has none (the Pixel 11 images need it), so these
     * phones get a Pixel too.
     */
    private val PIXEL9_CHOICE = ImageChoice(Images.ID_PIXEL_KOMODO, PackageSets.ID_PIXEL9_SULTRA, tested = false)

    /**
     * 0.4.27: Infinity-X (the Find X9 Ultra build) on every phone, untested,
     * listed last so no phone's preselected system changes. Its system code
     * has no SVE, so the SM8750 and older phones take it too; one set for all
     * of them (a fold gets the fold layout from the session scripts).
     */
    private val INFINITYX_CHOICE = ImageChoice(Images.ID_INFINITYX, PackageSets.ID_INFINITYX, tested = false)

    /**
     * 0.4.31: the phones whose testers' sessions reached the home screen on it
     * (reports of 2026-10-29/30, docs/compatibility.md): the Galaxy S26 Ultra,
     * S25 Ultra, S25, S24 Ultra and Z Flip5. Calls and SMS are the open ones
     * there, which is what the IMS work is for.
     */
    private val INFINITYX_TESTED = INFINITYX_CHOICE.copy(tested = true)

    /**
     * HyperOS (0.4.31, owner: "Allow all users to try"), made for the Z Fold8 Ultra
     * and Z Fold8: on every phone, last, after Infinity-X. One set everywhere; on a
     * bar phone its cover-screen layout runs as the phone's screen.
     */
    private val HYPEROS_CHOICE = ImageChoice(Images.ID_HYPEROS_LHASA, PackageSets.ID_HYPER_F8U, tested = false)

    private fun olderVendor(vendorApi: String, kernel: String) = Finding(
        ID_NOTE_OLDER_VENDOR, Level.WARN, "Older vendor software",
        "This phone has vendor API $vendorApi and kernel $kernel; the other systems' display path has only run on " +
            "vendor API 202504 (Snapdragon 8 Elite Gen 5).",
        R_OLDER_VENDOR,
    )

    /** The SM8750 phones (S25 Ultra, S25, S25+, Z Fold7): vendor API 202404 and kernel 6.6. */
    private val OLDER_VENDOR = olderVendor("202404", "6.6")

    /**
     * The SM8550 phones (Galaxy S23, S23+): vendor API 33 (Android 13) and
     * kernel 5.15, and only the HIDL display composer (their probe reports
     * list no android.hardware.graphics.composer3.IComposer, which the S24
     * Ultra and newer have and the guests' SurfaceFlinger talks to).
     * Offered at the owner's request (2026-10-25) so
     * a failed start comes back as logs: the black-screen auto-return
     * (oneos_switch.sh, ONEOS_RETURN_BLACK) brings One UI back.
     */
    private val EXPECT_NO_START = Finding(
        ID_NOTE_EXPECT_NO_START, Level.WARN, "Will most likely not start",
        "This phone has vendor API 33 (Android 13) and kernel 5.15; the other systems have only run on " +
            "vendor API 202404 and newer. If the screen stays black, OneOS returns to One UI by itself; " +
            "share the session logs afterwards.",
        R_EXPECT_NO_START,
    )

    /** The Exynos phones' note: the first sessions on that chip. */
    const val ID_NOTE_EXYNOS = "profile-exynos"
    const val R_EXYNOS = "exynos"

    /**
     * 0.4.17: the first Exynos phones. The guest system takes One UI's vendor
     * through Android's standard interfaces, the same as on Snapdragon, and the
     * session scripts find Samsung's Exynos display composer
     * (vendor.hwcomposer-3) and backlight (/sys/class/backlight/panel)
     * themselves. The S26+ (Exynos) and Z Flip7 sessions of 0.4.17 to 0.4.20
     * brought the Exynos host fixes (docs/exynos-hosts.md: ro.boot.hardware
     * for the Qualcomm-built guests, Samsung's software watchdog); the next
     * Exynos logs show how far a session gets with them.
     */
    private val EXYNOS = Finding(
        ID_NOTE_EXYNOS, Level.WARN, "First runs on Exynos",
        "Exynos phones have had only a few test sessions so far. The screen, phone calls, Wi-Fi or sound may not work; " +
            "if the screen stays black, OneOS returns to One UI by itself. Share the session logs afterwards.",
        R_EXYNOS,
    )

    /** The MediaTek phones' note: the first sessions on that chip (0.4.33, the Galaxy A07). */
    const val ID_NOTE_MEDIATEK = "profile-mediatek"
    const val R_MEDIATEK = "mediatek"

    /**
     * 0.4.33: the first MediaTek phone. The guest system takes One UI's vendor
     * through Android's standard interfaces, as on Snapdragon and Exynos, and
     * the session scripts find the display composer by its init service name
     * (vendor.hwcomposer-3-1 on the A07). Unlike every phone before it there is
     * no /sys/class/backlight panel node, so the brightness slider may do
     * nothing until a session shows how its backlight is driven.
     */
    private val MEDIATEK = Finding(
        ID_NOTE_MEDIATEK, Level.WARN, "First runs on MediaTek",
        "MediaTek phones have not run OneOS before. The screen, brightness, phone calls, Wi-Fi or sound may not work; " +
            "if the screen stays black, OneOS returns to One UI by itself. Share the session logs afterwards.",
        R_MEDIATEK,
    )

    /** The Switch tick for [ID_NOTE_EXPECT_NO_START]. */
    fun expectNoStartAck(system: String = Images.OTHER_SYSTEM): String =
        "I understand $system will most likely not start on this phone, and I'll share the session logs after"

    val F8U = PhoneProfile(
        id = ID_F8U,
        label = "Z Fold (F8U)",
        modelPrefix = "SM-F976",
        codename = "q8q",
        socModel = "SM8850",
        form = FormFactor.FOLD,
        choices = listOf(
            ImageChoice(Images.ID_FIND_N6, PackageSets.ID_N6_F8U, tested = true),
            // 0.3.7: the Pixel 11 Fold image with its set, on this phone only.
            // Tested: runs 41-44 booted it here (staged over adb by
            // scripts/deploy-phone.sh, not yet from the app).
            ImageChoice(Images.ID_PIXEL_YOGI, PackageSets.ID_PIXEL_F8U, tested = true),
            INFINITYX_CHOICE, HYPEROS_CHOICE,
        ),
    )

    val S_ULTRA_8850 = PhoneProfile(
        id = ID_S_ULTRA_8850,
        label = "Galaxy S26 Ultra",
        modelPrefix = "SM-S948",
        codename = "m3q",
        socModel = "SM8850",
        form = FormFactor.BAR,
        // 0.4.10: the Pixel 11 Pro image too, untested. SM8850 has the SVE that
        // Pixel's system code needs; the SM8750 phones do not.
        choices = S_ULTRA_CHOICES + ImageChoice(Images.ID_PIXEL_KODIAK, PackageSets.ID_PIXEL_SULTRA, tested = false) + INFINITYX_TESTED + HYPEROS_CHOICE,
    )

    val S_ULTRA_8750 = PhoneProfile(
        id = ID_S_ULTRA_8750,
        label = "Galaxy S25 Ultra",
        modelPrefix = "SM-S938",
        codename = "pa3q",
        socModel = "SM8750",
        form = FormFactor.BAR,
        // Off in 0.3.0 (the S26 Ultra was to go first); on from 0.3.1 (owner: the
        // friend's S25 Ultra is on Android 17 and ready). Untested: ticks as the S26 Ultra.
        choices = S_ULTRA_CHOICES + PIXEL9_CHOICE + INFINITYX_TESTED + HYPEROS_CHOICE,
        notes = listOf(OLDER_VENDOR),
    )

    /**
     * Galaxy S25 (base), from 0.3.4: a friend's probe findings of 2026-10-23
     * (SM-S931B, pa1q, SM8750, One UI 9.0, Android 17, one 1080 x 2340
     * panel, no hinge, installd table identical to switcher/patches/
     * host_installd.txt, 85 methods). Not an S Ultra, so its explicit profile
     * is what puts it in scope
     * ([CompatEvaluator.inScope]); it takes the S Ultra images and sets, untested.
     */
    val S_8750 = PhoneProfile(
        id = ID_S_8750,
        label = "Galaxy S25",
        modelPrefix = "SM-S931",
        codename = "pa1q",
        socModel = "SM8750",
        form = FormFactor.BAR,
        choices = S_ULTRA_CHOICES + PIXEL9_CHOICE + INFINITYX_TESTED + HYPEROS_CHOICE,
        notes = listOf(OLDER_VENDOR),
    )

    /**
     * Galaxy Z Fold7, from 0.4.8: a probe report of 2026-10-25 (SM-F966B, q7q,
     * SM8750, One UI 9.0, Android 17, vendor API 202404, kernel 6.6). Laid
     * out like the Z Fold8 Ultra: inner panel on port 147 and cover on 148
     * (1968 x 2184 and 1080 x 2520, density 420), sec_touchscreen and
     * sec_touchscreen2 on those ports, device states 0 CLOSE and 3 OPEN, a
     * "flip" lid switch, and an installd table identical to
     * switcher/patches/host_installd.txt (85 methods). It takes the Find N6
     * image with the Fold's set, untested. No Pixel image: Pixel's system
     * code uses SVE in every process (RefBase's constructor), which SM8750
     * does not have.
     */
    val FOLD7 = PhoneProfile(
        id = ID_FOLD7,
        label = "Galaxy Z Fold7",
        modelPrefix = "SM-F966",
        codename = "q7q",
        socModel = "SM8750",
        form = FormFactor.FOLD,
        choices = listOf(ImageChoice(Images.ID_FIND_N6, PackageSets.ID_N6_F8U, tested = false), INFINITYX_CHOICE, HYPEROS_CHOICE),
        notes = listOf(OLDER_VENDOR),
    )

    /**
     * Galaxy S25+, from 0.4.10: a probe report of 2026-10-25 (SM-S936B, pa2q,
     * SM8750, One UI 9.0, Android 17, vendor API 202404, one panel on port
     * 147, density 450, installd table identical to
     * switcher/patches/host_installd.txt). The Galaxy S25's images and sets,
     * untested; of the Pixel images only the Pixel 9 Pro XL's (no SVE).
     */
    val S_PLUS_8750 = PhoneProfile(
        id = ID_S_PLUS_8750,
        label = "Galaxy S25+",
        modelPrefix = "SM-S936",
        codename = "pa2q",
        socModel = "SM8750",
        form = FormFactor.BAR,
        choices = listOf(X9U_IN_CHOICE, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), PIXEL9_CHOICE, INFINITYX_CHOICE, HYPEROS_CHOICE),
        notes = listOf(OLDER_VENDOR),
    )

    /**
     * Galaxy Z Fold8, from 0.4.10: a probe report of 2026-10-25 (SM-F971B, h8q,
     * SM8850, One UI 9.0, Android 17, vendor API 202504 like the Z Fold8 Ultra,
     * kernel 6.12). The Z Fold8 Ultra's ports and touch screens (inner 147 on
     * sec_touchscreen, cover 148 on sec_touchscreen2), a flip lid switch and an
     * identical installd table; other panels (about 1848 x 2448 and 1248 x
     * 1972, density 420) and one more device state, 1 TENT, which the fold
     * layout now takes from the phone (guest_start.sh). It takes the Fold's
     * images and sets, untested; the Pixel display configs are the Z Fold8
     * Ultra's only, so its Pixel keeps the image's own brightness.
     */
    val FOLD8 = PhoneProfile(
        id = ID_FOLD8,
        label = "Galaxy Z Fold8",
        modelPrefix = "SM-F971",
        codename = "h8q",
        socModel = "SM8850",
        form = FormFactor.FOLD,
        // 0.4.14: the Find N6 image with its own set, n6-f8 (pull request #1; the
        // owner's friend has run ColorOS on his SM-F971B, so it counts as tested).
        choices = listOf(
            ImageChoice(Images.ID_FIND_N6, PackageSets.ID_N6_F8, tested = true),
            ImageChoice(Images.ID_PIXEL_YOGI, PackageSets.ID_PIXEL_F8, tested = true),
            INFINITYX_CHOICE, HYPEROS_CHOICE,
        ),
    )

    /** The Galaxy S26's images: the S Ultra's, both untested. */
    private val S_8850_CHOICES = listOf(
        X9U_IN_CHOICE,
        ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false),
        ImageChoice(Images.ID_PIXEL_KODIAK, PackageSets.ID_PIXEL_SULTRA, tested = false),
        INFINITYX_CHOICE, HYPEROS_CHOICE,
    )

    /**
     * Galaxy S26 (base), from 0.4.11: a probe report of 2026-10-25 from the
     * Japanese carrier model (SC-51G, m1q, SM8850, One UI 9.0, Android 17,
     * vendor API 202504 and kernel 6.12 like the Z Fold8 Ultra, one panel on
     * port 147, density 480, SVE and SME, installd 85 methods). One profile
     * per model number: SM-S942 everywhere else, [S_8850_JP] for SC-51G. The
     * Exynos S26 has another chip and matches neither.
     */
    val S_8850 = PhoneProfile(
        id = ID_S_8850,
        label = "Galaxy S26",
        modelPrefix = "SM-S942",
        codename = "m1q",
        socModel = "SM8850",
        form = FormFactor.BAR,
        choices = S_8850_CHOICES,
    )

    val S_8850_JP = S_8850.copy(id = ID_S_8850_JP, label = "Galaxy S26 (docomo)", modelPrefix = "SC-51G")

    /**
     * Galaxy S26+, from 0.4.12: a probe report of 2026-10-25 (SM-S947U, m2q,
     * SM8850, One UI 9.0, Android 17, vendor API 202504 and kernel 6.12, one
     * 1440 x 3120 panel on port 147 at density 600, a centre camera hole,
     * Android's Bluetooth audio HAL and AIDL composer like the S26 Ultra, SVE
     * and SME, installd 85 methods). The Galaxy S26's images and sets, untested.
     */
    val S_PLUS_8850 = PhoneProfile(
        id = ID_S_PLUS_8850,
        label = "Galaxy S26+",
        modelPrefix = "SM-S947",
        codename = "m2q",
        socModel = "SM8850",
        form = FormFactor.BAR,
        choices = S_8850_CHOICES,
    )

    /**
     * Galaxy S24 Ultra, from 0.4.11: a probe report of 2026-10-25 (SM-S928B,
     * e3q, SM8650, One UI 9.0, Android 17, vendor API 34, kernel 6.1, one
     * panel on port 147, density 450, no SVE or SME, installd 85 methods).
     * The S Ultra's images and sets, untested; of the Pixel images only the
     * Pixel 9 Pro XL's (no SVE).
     */
    val S_ULTRA_8650 = PhoneProfile(
        id = ID_S_ULTRA_8650,
        label = "Galaxy S24 Ultra",
        modelPrefix = "SM-S928",
        codename = "e3q",
        socModel = "SM8650",
        form = FormFactor.BAR,
        choices = listOf(X9U_IN_CHOICE, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), PIXEL9_CHOICE, INFINITYX_TESTED, HYPEROS_CHOICE),
        notes = listOf(olderVendor("34 (Android 14)", "6.1")),
    )

    /**
     * Galaxy S23 and S23+, from 0.4.11: probe reports of 2026-10-25
     * (SM-S911B dm1q and SM-S916U dm2q, SM8550, One UI 9.0, Android 17, one
     * panel on port 147, no SVE or SME, installd 85 methods). The S Ultra's
     * image and set with [EXPECT_NO_START]. 0.4.30: also the Pixel 9 Pro XL
     * image (no SVE needed), with the S23s, the Z Flip7, the A57 and the Z
     * Flip5: Infinity-X started on an S23 once the HIDL-only phones got the
     * hwservicemanager marker (guest_prepare.sh), which Pixel lacks as well.
     */
    val S_8550 = PhoneProfile(
        id = ID_S_8550,
        label = "Galaxy S23",
        modelPrefix = "SM-S911",
        codename = "dm1q",
        socModel = "SM8550",
        form = FormFactor.BAR,
        choices = listOf(X9U_IN_CHOICE, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), PIXEL9_CHOICE, INFINITYX_CHOICE, HYPEROS_CHOICE),
        notes = listOf(EXPECT_NO_START),
    )

    val S_PLUS_8550 = PhoneProfile(
        id = ID_S_PLUS_8550,
        label = "Galaxy S23+",
        modelPrefix = "SM-S916",
        codename = "dm2q",
        socModel = "SM8550",
        form = FormFactor.BAR,
        choices = listOf(X9U_IN_CHOICE, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), PIXEL9_CHOICE, INFINITYX_CHOICE, HYPEROS_CHOICE),
        notes = listOf(EXPECT_NO_START),
    )

    /**
     * Galaxy S26+ with Exynos, from 0.4.17: a probe report of 2026-10-25
     * (SM-S947B, m2s, s5e9965 Exynos 2600, One UI 9.0, Android 17 build
     * CP2A.260605.016, one 1440 x 3120 panel on port 129 at density 600,
     * sec_touchscreen, backlight /sys/class/backlight/panel, composer
     * vendor.hwcomposer-3, SVE, SVE2, SME and SME2, installd table identical to
     * switcher/patches/host_installd.txt). The Galaxy S26's images and sets
     * (both Find X9 Ultra builds and the Pixel 11 Pro, whose system code needs
     * the SVE this chip has), untested, with [EXYNOS].
     */
    val S_PLUS_9965 = PhoneProfile(
        id = ID_S_PLUS_9965,
        label = "Galaxy S26+ (Exynos)",
        modelPrefix = "SM-S947",
        codename = "m2s",
        socModel = "s5e9965",
        form = FormFactor.BAR,
        choices = S_8850_CHOICES,
        notes = listOf(EXYNOS),
    )

    /**
     * Galaxy Z Flip7, from 0.4.17: a probe report of 2026-10-25 (SM-F766U, b7s,
     * s5e9955 Exynos 2500, One UI 9.0, Android 17 build CP2A.260605.016, main
     * panel 1080 x 2520 on port 129 at density 480 and a 948 x 1048 cover on
     * 130, sec_touchscreen and sec_touchscreen2, a "flip" lid switch,
     * backlights panel and panel1, composer vendor.hwcomposer-3, SVE and SVE2
     * but no SME, installd table identical to host_installd.txt). It runs the
     * guest as a phone on the main panel (the bar settings; no cover layout
     * yet), with the Find X9 Ultra builds, untested, and [EXYNOS]. The ColorOS
     * images also start on the SM8750 phones, which have no SME either.
     */
    val FLIP7 = PhoneProfile(
        id = ID_FLIP7,
        label = "Galaxy Z Flip7",
        modelPrefix = "SM-F766",
        codename = "b7s",
        socModel = "s5e9955",
        form = FormFactor.FLIP,
        choices = listOf(X9U_IN_CHOICE, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), PIXEL9_CHOICE, INFINITYX_CHOICE, HYPEROS_CHOICE),
        notes = listOf(EXYNOS),
    )

    /**
     * Galaxy A57, from 0.4.29: a probe report of 2026-10-29 (SM-A576B, a57x,
     * s5e8865 Exynos, One UI 9.0, Android 17 build CP2A.260605.016, vendor API
     * 202504, kernel 6.12, one 1080 x 2340 panel on port 129 at density 450,
     * sec_touchscreen, backlight /sys/class/backlight/panel (max 9180),
     * composer vendor.hwcomposer-3 and the AIDL audio HAL, init.vendor.sysfw.rc
     * like the S26+ (Exynos), SVE and SVE2 but no SME, installd table identical
     * to host_installd.txt). The first A-series phone and the first mid-range
     * chip: the Z Flip7's images (the Find X9 Ultra builds and Infinity-X, whose
     * system code needs neither SVE nor SME), untested, with [EXYNOS].
     */
    val A57 = PhoneProfile(
        id = ID_A57,
        label = "Galaxy A57",
        modelPrefix = "SM-A576",
        codename = "a57x",
        socModel = "s5e8865",
        form = FormFactor.BAR,
        choices = listOf(X9U_IN_CHOICE, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = true), PIXEL9_CHOICE, INFINITYX_CHOICE, HYPEROS_CHOICE),
        notes = listOf(EXYNOS),
    )

    /**
     * Galaxy A56, from 0.4.31: a probe report of 2026-10-30 (SM-A566B, a56x,
     * s5e8855 Exynos, One UI 9.0, Android 17 build CP2A.260605.016, vendor API
     * 202404, one 1080 x 2340 panel at density 450, sec_touchscreen, composer
     * android.hardware.graphics.composer3-service.exynos, SVE and SVE2 but no
     * SME, installd table identical to host_installd.txt). The A57's smaller
     * sibling, one Exynos generation down (s5e8855 against s5e8865) and the same
     * in everything the switch reads, so it takes the A57's images, untested,
     * with [EXYNOS].
     */
    val A56 = PhoneProfile(
        id = ID_A56,
        label = "Galaxy A56",
        modelPrefix = "SM-A566",
        codename = "a56x",
        socModel = "s5e8855",
        form = FormFactor.BAR,
        choices = listOf(X9U_IN_CHOICE, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), PIXEL9_CHOICE, INFINITYX_CHOICE, HYPEROS_CHOICE),
        notes = listOf(EXYNOS),
    )

    /**
     * Galaxy Z Flip5, from 0.4.29: a probe report of 2026-10-28 (SM-F731B, b5q,
     * SM8550 like the Galaxy S23s, One UI 9.0, Android 17 build
     * CP2A.260605.016, vendor API 33 and kernel 5.15, main panel 1080 x 2640 on
     * port 131 at density 480 and a 748 x 720 cover on 132, sec_touchscreen
     * and sec_touchscreen2, a "flip" lid switch, backlights panel0-backlight
     * and panel1-backlight (max 765), the HIDL display composer and audio HAL
     * (no composer3, no audio.core), no SVE, installd table identical to
     * host_installd.txt). The S23s' images (both Find X9 Ultra builds and
     * Infinity-X), run as a phone on the main screen like the Z Flip7 (no
     * cover layout), with their [EXPECT_NO_START] note: the same chip and
     * vendor, which no system has started on yet.
     */
    val FLIP5 = PhoneProfile(
        id = ID_FLIP5,
        label = "Galaxy Z Flip5",
        modelPrefix = "SM-F731",
        codename = "b5q",
        socModel = "SM8550",
        form = FormFactor.FLIP,
        choices = listOf(X9U_IN_TESTED, ImageChoice(Images.ID_FIND_X9U, PackageSets.ID_X9U_SULTRA, tested = false), PIXEL9_CHOICE, INFINITYX_TESTED, HYPEROS_CHOICE),
        notes = listOf(EXPECT_NO_START),
    )

    /**
     * Galaxy Z Fold6, from 0.4.33: a probe report of 2026-10-30 (SM-F956B, q6q,
     * SM8650 like the Galaxy S24 Ultra, One UI 9.0, Android 17 build
     * CP2A.260605.016, vendor API 34 and kernel 6.1, density 420, installd table
     * identical to host_installd.txt; the tester asked for a build). A fold like
     * the Z Fold7: the Find N6 build (its fold layout) and Infinity-X, untested.
     * Not HyperOS: on the S24 Ultra, the same chip, its SurfaceFlinger aborts at
     * the first display (0.4.32, four sessions), so it would only show black.
     */
    val FOLD6 = PhoneProfile(
        id = ID_FOLD6,
        label = "Galaxy Z Fold6",
        modelPrefix = "SM-F956",
        codename = "q6q",
        socModel = "SM8650",
        form = FormFactor.FOLD,
        choices = listOf(ImageChoice(Images.ID_FIND_N6, PackageSets.ID_N6_F8U, tested = false), INFINITYX_CHOICE),
        notes = listOf(olderVendor("34 (Android 14)", "6.1")),
    )

    /**
     * Galaxy A07, from 0.4.33: a probe report of 2026-10-30 (SM-A075F, a07,
     * MediaTek MT6789, One UI 9.0, Android 17 build CP2A.260605.016, vendor API
     * 31 and kernel 6.12, one 720 x 1600 panel at density 300 (60/90 Hz), touch
     * chipone-tddi, composer3 and audio.core AIDL HALs, Cortex-A55/A76 with no
     * SVE or SME, installd table of 85 methods like every One UI 9 phone). The
     * owner asked for the A series on Infinity-X; this one is on Android 17
     * already, so the version gate allows it. Infinity-X only (its system code
     * needs neither SVE nor SME: the S25 Ultra runs it), untested, with
     * [MEDIATEK]. The A16 and A17 on One UI 8.5 (Android 16) are still refused
     * by the version gate.
     */
    val A07 = PhoneProfile(
        id = ID_A07,
        label = "Galaxy A07",
        modelPrefix = "SM-A075",
        codename = "a07",
        socModel = "MT6789",
        form = FormFactor.BAR,
        choices = listOf(INFINITYX_CHOICE),
        notes = listOf(MEDIATEK, olderVendor("31 (Android 12)", "6.12")),
    )

    val BUILT_IN: List<PhoneProfile> = listOf(
        F8U, S_ULTRA_8850, S_ULTRA_8750, S_8750, FOLD7, S_PLUS_8750, FOLD8,
        S_8850, S_8850_JP, S_ULTRA_8650, S_8550, S_PLUS_8550, S_PLUS_8850,
        S_PLUS_9965, FLIP7, A56, A57, FLIP5, FOLD6, A07,
    )

    /** `ro.build.version.oneui` values seen on profiled phones (all One UI 9.0). */
    val ONE_UI_SEEN: Set<Int> = setOf(90000)

    fun byId(id: String, profiles: List<PhoneProfile> = BUILT_IN): PhoneProfile? = profiles.firstOrNull { it.id == id }

    fun resolve(f: DeviceFacts, profiles: List<PhoneProfile> = BUILT_IN): ProfileResolution =
        resolve(f.model, f.device, f.soc.model, profiles)

    fun resolve(model: String, device: String, soc: String, profiles: List<PhoneProfile> = BUILT_IN): ProfileResolution {
        val hits = profiles.filter { it.matches(model, device, soc) }
        if (hits.size == 1) return ProfileResolution(hits.single(), null, emptyList())
        if (hits.size > 1) {
            return ProfileResolution(null, null, listOf("matches ${hits.size} profiles (${hits.joinToString { it.id }})"))
        }
        val m = model.trim().uppercase(Locale.ROOT)
        val nearest = profiles.firstOrNull { m.startsWith(it.modelPrefix) }
            ?: profiles.firstOrNull { device.trim().equals(it.codename, ignoreCase = true) }
            ?: return ProfileResolution(null, null, emptyList())
        val diff = mutableListOf<String>()
        if (!m.startsWith(nearest.modelPrefix)) diff += "model ${model.trim()} (profile: ${nearest.modelPrefix}*)"
        if (!device.trim().equals(nearest.codename, ignoreCase = true)) {
            diff += "codename ${device.trim().ifEmpty { "unknown" }} (profile: ${nearest.codename})"
        }
        if (!soc.trim().equals(nearest.socModel, ignoreCase = true)) {
            diff += "chip ${soc.trim().ifEmpty { "unknown" }} (profile: ${nearest.socModel})"
        }
        return ProfileResolution(null, nearest, diff)
    }
}

/** Android release names for the SDK levels OneOS meets. */
object AndroidRelease {
    fun name(sdk: Int): String = when (sdk) {
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
        else -> "SDK $sdk"
    }
}
