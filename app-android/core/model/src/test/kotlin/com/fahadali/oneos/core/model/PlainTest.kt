package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plain messages (the redesign, section 3.2): every check and reason has
 * one, short, with one action, in words without the technical terms, and no
 * fixed text names a system (`{system}` comes from the image table).
 */
class PlainTest {
    /** A system that is not in the table yet, to show `{system}` is the table's. */
    private val pixel = Images.FIND_N6.copy(id = "pixel-test", name = "Pixel 11 Fold", variant = null, shortName = "Pixel")

    private val images: List<ImageRef?> = Images.ALL + pixel + null
    private val phones: List<PhoneProfile?> = Profiles.BUILT_IN + null

    /** Every (id, reason, level) a gate or rule can produce (BLOCK and WARN, plus the INFO the Switch dialog shows). */
    private val catalogue: List<Finding> = buildList {
        fun add(id: String, level: Level, vararg reasons: String) = reasons.forEach { add(Finding(id, level, "t", "d", it)) }
        add(
            SwitchGates.ID_ROOT, Level.BLOCK,
            SwitchGates.R_ROOT_UNCHECKED, SwitchGates.R_ROOT_DENIED, SwitchGates.R_ROOT_NO_SU, SwitchGates.R_ROOT_TIMEOUT, SwitchGates.R_ROOT_ERROR,
        )
        add(SwitchGates.ID_PROFILE, Level.BLOCK, SwitchGates.R_PROFILE_NONE, SwitchGates.R_PROFILE_DISABLED)
        add(SwitchGates.ID_PROFILE, Level.WARN, SwitchGates.R_PROFILE_UNTESTED)
        add(SwitchGates.ID_ANDROID, Level.BLOCK, SwitchGates.R_ANDROID_DIFFERS)
        add(SwitchGates.ID_FORM, Level.BLOCK, SwitchGates.R_FORM_MISMATCH)
        add(
            SwitchGates.ID_IMAGE, Level.BLOCK,
            SwitchGates.R_IMAGE_NOT_IN_BUILD, SwitchGates.R_IMAGE_OTHER_SERIES, SwitchGates.R_IMAGE_NONE, SwitchGates.R_IMAGE_NOT_READ, SwitchGates.R_IMAGE_NOTHING,
            SwitchGates.R_IMAGE_OTHER_PHONE, SwitchGates.R_IMAGE_OTHER_VERSION, SwitchGates.R_IMAGE_OTHER_SET, SwitchGates.R_IMAGE_FILES_CHANGED,
            ImageIdentifier.R_SIZE_UNKNOWN, ImageIdentifier.R_UNCOMPRESSED, ImageIdentifier.R_NOT_OURS, ImageIdentifier.R_WRONG_PHONE,
            ImageIdentifier.R_NOT_CHECKED, ImageIdentifier.R_DAMAGED, ImageIdentifier.R_NO_RAW, ImageIdentifier.R_RAW_NOT_OURS,
            ImageIdentifier.R_RAW_WRONG_PHONE, ImageIdentifier.R_RAW_DAMAGED, ImageIdentifier.R_RETIRED,
        )
        add(SwitchGates.ID_INSTALLD, Level.BLOCK, SwitchGates.R_INSTALLD_DIFFERS)
        add(SwitchGates.ID_OLD_SWITCHER, Level.BLOCK, SwitchGates.R_OLD_SWITCHER_INSTALLED, SwitchGates.R_OLD_SWITCHER_UNKNOWN)
        add(
            SwitchGates.ID_STAGE, Level.BLOCK,
            SwitchGates.R_STAGE_NOT_READ, SwitchGates.R_STAGE_MOUNTED, SwitchGates.R_STAGE_BRIDGE, SwitchGates.R_STAGE_UNFINISHED, SwitchGates.R_STAGE_RUNNING,
        )
        add(SwitchGates.ID_SPACE, Level.BLOCK, SwitchGates.R_SPACE_UNKNOWN, SwitchGates.R_SPACE_SETUP, SwitchGates.R_SPACE_SWITCH)
        add(SwitchGates.ID_SCREEN_LOCK, Level.BLOCK, ScreenLockRule.R_SET, ScreenLockRule.R_UNKNOWN)
        add(SwitchGates.ID_SCREEN_LOCK, Level.WARN, ScreenLockRule.R_SET, ScreenLockRule.R_UNKNOWN, ScreenLockRule.R_SET_ALLOWED)
        add(SwitchGates.ID_BATTERY, Level.BLOCK, SwitchGates.R_BATTERY_NOT_READ, SwitchGates.R_BATTERY_LOW, SwitchGates.R_BATTERY_HOT)
        add(SwitchGates.ID_CARRIER, Level.WARN, CarrierRules.R_VOLTE_ONLY, CarrierRules.R_UNKNOWN)
        add(SwitchGates.ID_CARRIER, Level.INFO, CarrierRules.R_2G_3G)
        add(CpuRules.ID_MISSING, Level.WARN, CpuRules.R_MISSING)
        add(CpuRules.ID_APP_FEATURES, Level.WARN, CpuRules.R_APP_FEATURES)
        add(Profiles.ID_NOTE_OLDER_VENDOR, Level.WARN, Profiles.R_OLDER_VENDOR)
        add(Profiles.ID_NOTE_EXPECT_NO_START, Level.WARN, Profiles.R_EXPECT_NO_START)
        add(SwitchGates.ID_LOGS, Level.WARN, SwitchGates.R_LOGS)
    }

    private val facts = listOf(
        PlainFacts(),
        PlainFacts(rootApp = "KernelSU", freeBytes = 15_000_000_000, needBytes = 23_000_000_000, batteryC = 44f, phoneAndroid = "16", installedReusable = false),
        PlainFacts(rootApp = "Magisk", freeBytes = 1_000_000_000, needBytes = 123_000_000_000),
    )

    /** Technical words and system names a plain text never has (the system's own name is taken out first). */
    private val forbidden = Regex("""ColorOS|OPPO|Lineage|Pixel|Find N6|Find X9|SHA|stage|installd|su -c|\buid\b|system\.raw|\.img|profile id""")

    private fun clean(m: PlainMessage, image: ImageRef?): String {
        var t = "${m.title}\n${m.line}\n${m.actionLabel.orEmpty()}"
        if (image != null) t = t.replace(image.nameWithVariant, "{system}").replace(image.name, "{system}")
        return t
    }

    private fun check(m: PlainMessage, image: ImageRef?, what: String) {
        assertTrue("$what: title '${m.title}' is ${m.title.length} long", m.title.length <= PlainMessage.MAX_TITLE)
        assertTrue("$what: line '${m.line}' is ${m.line.length} long", m.line.length <= PlainMessage.MAX_LINE)
        assertTrue(what, m.title.isNotBlank() && m.line.isNotBlank())
        forbidden.find(clean(m, image))?.let { throw AssertionError("$what: '${it.value}' in '${m.title} / ${m.line}'") }
        // A button only where there is something to tap.
        assertEquals(what, m.actionLabel == null, m.action == FixAction.NONE || m.action == FixAction.ACKNOWLEDGE)
    }

    @Test
    fun everyCheckAndReasonHasAShortPlainMessage() {
        for (f in catalogue) {
            for (image in images) {
                for (phone in phones) {
                    for (pf in facts) {
                        val what = "${f.id}/${f.reason}/${f.level} ${image?.id} ${phone?.id}"
                        assertTrue(what, Plain.knows(f, image, phone))
                        val m = Plain.of(f, image, phone, pf)
                        check(m, image, what)
                        assertEquals(what, f.level, m.level)
                    }
                }
            }
        }
    }

    @Test
    fun theActionsFitTheProblem() {
        fun of(id: String, reason: String, image: ImageRef? = Images.FIND_N6, pf: PlainFacts = PlainFacts()) =
            Plain.of(Finding(id, Level.BLOCK, "", "", reason), image, Profiles.F8U, pf)
        assertEquals(FixAction.OPEN_LOCK_SETTINGS, of(SwitchGates.ID_SCREEN_LOCK, ScreenLockRule.R_SET).action)
        assertEquals("Turn off your screen lock", of(SwitchGates.ID_SCREEN_LOCK, ScreenLockRule.R_SET).title)
        assertEquals(FixAction.RESTORE, of(SwitchGates.ID_STAGE, SwitchGates.R_STAGE_UNFINISHED).action)
        assertEquals(FixAction.NONE, of(SwitchGates.ID_STAGE, SwitchGates.R_STAGE_MOUNTED).action)
        assertEquals(FixAction.OPEN_OLD_SWITCHER_INFO, of(SwitchGates.ID_OLD_SWITCHER, SwitchGates.R_OLD_SWITCHER_INSTALLED).action)
        assertEquals(FixAction.UPDATE, of(SwitchGates.ID_IMAGE, SwitchGates.R_IMAGE_OTHER_VERSION).action)
        assertEquals("Finish updating ColorOS 17", of(SwitchGates.ID_IMAGE, SwitchGates.R_IMAGE_OTHER_VERSION).title)
        assertTrue(of(SwitchGates.ID_IMAGE, SwitchGates.R_IMAGE_OTHER_VERSION).line.contains("No download"))
        assertFalse(of(SwitchGates.ID_IMAGE, SwitchGates.R_IMAGE_OTHER_VERSION, pf = PlainFacts(installedReusable = false)).line.contains("No download"))
        assertEquals(FixAction.REPAIR, of(SwitchGates.ID_IMAGE, SwitchGates.R_IMAGE_FILES_CHANGED).action)
        assertEquals(FixAction.CHOOSE_SYSTEM, of(SwitchGates.ID_IMAGE, SwitchGates.R_IMAGE_NOTHING).action)
        assertEquals(FixAction.DOWNLOAD_AGAIN, of(SwitchGates.ID_IMAGE, ImageIdentifier.R_DAMAGED).action)
        assertEquals(FixAction.SEND_PHONE_DETAILS, of(SwitchGates.ID_INSTALLD, SwitchGates.R_INSTALLD_DIFFERS).action)
        assertEquals("One UI was updated", of(SwitchGates.ID_INSTALLD, SwitchGates.R_INSTALLD_DIFFERS).title)
        // Root: open the root app that was found, else try again.
        val denied = of(SwitchGates.ID_ROOT, SwitchGates.R_ROOT_DENIED, pf = PlainFacts(rootApp = "KernelSU"))
        assertEquals(FixAction.OPEN_ROOT_APP, denied.action)
        assertEquals("Open KernelSU", denied.actionLabel)
        assertEquals(FixAction.TRY_AGAIN, of(SwitchGates.ID_ROOT, SwitchGates.R_ROOT_DENIED).action)
        // KernelSU hides su from apps it has not allowed: with a root app found, no su is a grant to give.
        assertEquals("Allow root access", of(SwitchGates.ID_ROOT, SwitchGates.R_ROOT_NO_SU, pf = PlainFacts(rootApp = "KernelSU")).title)
        assertEquals("OneOS needs a rooted phone", of(SwitchGates.ID_ROOT, SwitchGates.R_ROOT_NO_SU).title)
        // Space: what is missing, from the facts.
        val space = of(SwitchGates.ID_SPACE, SwitchGates.R_SPACE_SETUP, pf = PlainFacts(freeBytes = 15_000_000_000, needBytes = 22_100_000_000))
        assertEquals("Free up 7.1 GB to set up ColorOS 17", space.title)
        assertEquals("It needs about 22.1 GB; you have 15.0 GB.", space.line)
        assertEquals(FixAction.FREE_UP_SPACE, space.action)
        // The phone list comes from the profiles: every name while they fit one line (0.4.28),
        // the families once they do not (0.4.29, with the A57 and Z Flip5).
        assertEquals(
            "OneOS works on 18 Galaxy phones so far: Z Fold, S, Z Flip and A models; the README lists them.",
            of(SwitchGates.ID_PROFILE, SwitchGates.R_PROFILE_NONE).line,
        )
        assertEquals("the Galaxy Z Fold (F8U), S26 Ultra and S25", Plain.phoneList(listOf(Profiles.F8U, Profiles.S_ULTRA_8850, Profiles.S_8750)))
        assertEquals("the Galaxy S26", Plain.phoneList(listOf(Profiles.S_8850, Profiles.S_8850_JP)))
        // Ticks: the same text as the gate's (part of the tick's key).
        val calls = Plain.of(Finding(SwitchGates.ID_CARRIER, Level.WARN, "", "", CarrierRules.R_VOLTE_ONLY), pixel, Profiles.F8U)
        assertEquals(FixAction.ACKNOWLEDGE, calls.action)
        assertEquals(CarrierRules.noCallsAck("Pixel 11 Fold"), calls.line)
    }

    /** The gates themselves, on the four phones and every way they can refuse: each BLOCK and WARN has a reason Plain knows. */
    @Test
    fun everyRealGateRefusalHasAReason() {
        val reports = mutableListOf<GateReport>()
        for (phone in Bundles.ALL) {
            val profile = Profiles.resolve(phone.facts).profile!!
            for (c in profile.choices) {
                val image = Images.byId(c.imageId)!!
                val gz = ImageFileCheck(image.gzName, image.gzBytes, image.gzSha256, image.gzBytes)
                val marker = StageMarker(4, profile.id, image.id, c.packageSetId, 1)
                val good = GateInput(
                    facts = phone.facts, screenLockSet = false, oldSwitcherInstalled = false, installd = phone.installd,
                    stage = StageState(emptyList(), emptyList(), false, marker, true), imageFile = gz, appVersionCode = 4,
                )
                val bad = listOf(
                    good,
                    good.copy(screenLockSet = true, oldSwitcherInstalled = true),
                    good.copy(screenLockSet = null, oldSwitcherInstalled = null, installd = null, stage = null, freeBytes = null, carrier = null),
                    good.copy(facts = phone.facts.copy(battery = BatteryInfo(10, false, 30f), root = RootSignals()), freeBytes = 1),
                    good.copy(facts = phone.facts.copy(battery = BatteryInfo(90, true, 45f), sdk = 36, form = phone.facts.form.copy(form = FormFactor.FLIP))),
                    good.copy(stage = StageState(listOf("/x"), emptyList(), true, marker, false)),
                    good.copy(stage = StageState(emptyList(), listOf("/data/app"), false, marker, false, unfinishedSession = true)),
                    good.copy(stage = StageState(emptyList(), emptyList(), false, null, false), imageFile = gz.copy(sha256 = null)),
                    good.copy(stage = StageState(emptyList(), emptyList(), false, marker.copy(versionCode = 3), true), imageFile = gz.copy(sha256 = "0".repeat(64))),
                    good.copy(stage = StageState(emptyList(), emptyList(), false, marker.copy(setId = "x9u-sultra"), true), imageFile = ImageFileCheck("a", 7)),
                    good.copy(stage = StageState(emptyList(), emptyList(), false, marker.copy(profileId = "other"), true), imageFile = null),
                    good.copy(stage = StageState(emptyList(), emptyList(), false, marker, false), uncollectedLogs = true),
                )
                for (i in bad) for (phase in GatePhase.entries) reports += SwitchGates.evaluate(phase, i)
            }
        }
        reports += SwitchGates.evaluate(GatePhase.SWITCH, GateInput(Fixtures.facts(model = "SM-X000"), false, false, null, null))
        var seen = 0
        for (r in reports) {
            for (g in r.gates) {
                val f = g.finding
                if (f.level != Level.BLOCK && f.level != Level.WARN) continue
                seen++
                val what = "${r.profile?.id} ${r.phase} ${f.id} '${f.title}'"
                assertNotNull(what, f.reason)
                assertTrue(what, Plain.knows(f, r.image, r.profile))
                check(Plain.of(f, r, null), r.image, what)
            }
        }
        assertTrue(seen > 100)
    }

    @Test
    fun rootRefusalsAtSwitchTime() {
        fun r(p: String) = Plain.switchRefused(p, Images.FIND_N6, Profiles.F8U)
        assertEquals("Your root app is missing a tool", r("no busybox at the root manager's paths").title)
        assertEquals("Your root app is missing a tool", r("no resetprop at the root manager's paths").title)
        assertEquals("OneOS can't read the volume keys", r("no Volume Up key found (getevent)").title)
        assertEquals(FixAction.FREE_UP_SPACE, r("less than 11 GiB free on /data").action)
        assertEquals("Charge your phone", r("battery at 12% and not charging").title)
        assertEquals("Let your phone cool down", r("battery too warm").title)
        assertEquals(FixAction.OPEN_OLD_SWITCHER_INFO, r("the old switcher (com.wewaffles.onelineage) is installed: uninstall it").action)
        assertEquals(FixAction.RESTORE, r("the last session never returned: restore One UI's settings first (Last session)").action)
        assertEquals(FixAction.UPDATE, r("prepared by app build 8, this is build 9: prepare again").action)
        val other = r("pm does not answer")
        assertEquals("Couldn't switch", other.title)
        assertEquals(FixAction.SEND_REPORT, other.action)
        for (p in listOf("no busybox", "no Volume Up key", "less than 11 GiB free on /data", "battery too warm", "x", "pm does not answer")) {
            check(r(p), Images.FIND_N6, p)
        }
    }

    @Test
    fun setupFailures() {
        fun s(m: String, stopped: Boolean = false) = Plain.setupFailed(m, stopped, pixel)
        assertEquals("Setup stopped", s("Stopped.", stopped = true).title)
        assertEquals("Pixel 11 Fold isn't ready. Set it up again whenever you like.", s("x", stopped = true).line)
        assertEquals("The download is damaged", s("The image: the .gz SHA-256 differs").title)
        assertEquals("The download is damaged", s("The image: unpacked to 5 bytes, not 10").title)
        assertEquals("This copy of OneOS is damaged", s("art_payload.img in the app is damaged").title)
        assertEquals("One UI changed during setup", s("installd table: host_installd.table: services.jar is not the jar the table was derived from (One UI updated?)").title)
        assertEquals("Couldn't read the download", s("IOException: cannot open the picked file").title)
        val other = s("Stage folder: exit 3: something")
        assertEquals("Setup didn't finish", other.title)
        assertEquals(FixAction.SEND_REPORT, other.action)
        for (m in listOf("x", "the .gz SHA-256 differs", "in the app is damaged", "services.jar is not the jar", "IOException")) {
            check(s(m), pixel, m)
            check(s(m, stopped = true), pixel, m)
        }
        assertNull(Plain.defaultLabel(FixAction.NONE))
    }
}
