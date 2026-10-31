package com.fahadali.oneos.session

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.fahadali.oneos.core.model.ImageFileCheck
import com.fahadali.oneos.core.root.HomeSnapshot

/**
 * Build 2's small state, in the app-private SharedPreferences `oneos`
 * (excluded from backup and device transfer): the picked image (its URI and
 * what Check file found), the choice "image already on this phone", the
 * last launch (session id, wall-clock and boot time, and how it ended once
 * its logs were read), so the next open can tell that the phone restarted
 * since and show it on Home; the warnings already ticked, per phone and
 * system; Hide Google apps; Cool down when hot; which "Welcome back" was
 * seen; and the Home of
 * the last finished check, for the next cold start.
 */
object SessionPrefs {
    private const val PREFS = "oneos"

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Source { GZ, ON_PHONE }

    data class PickedImage(
        val uri: Uri,
        val name: String?,
        val bytes: Long?,
        /** SHA-256 of the whole file from Check file; null until checked. */
        val sha256: String?,
        val hashedBytes: Long?,
    ) {
        val check: ImageFileCheck get() = ImageFileCheck(name, bytes, sha256, hashedBytes)
    }

    fun picked(ctx: Context): PickedImage? {
        val s = p(ctx)
        val uri = s.getString("image_uri", null)?.let(Uri::parse) ?: return null
        return PickedImage(
            uri = uri,
            name = s.getString("image_name", null),
            bytes = s.getLong("image_bytes", -1).takeIf { it >= 0 },
            sha256 = s.getString("image_sha256", null),
            hashedBytes = s.getLong("image_hashed", -1).takeIf { it >= 0 },
        )
    }

    fun setPicked(ctx: Context, uri: Uri, name: String?, bytes: Long?) {
        p(ctx).edit()
            .putString("image_uri", uri.toString())
            .putString("image_name", name)
            .putLong("image_bytes", bytes ?: -1)
            .remove("image_sha256")
            .remove("image_hashed")
            .apply()
    }

    fun setChecked(ctx: Context, uri: Uri, sha256: String, hashed: Long) {
        if (picked(ctx)?.uri != uri) return
        p(ctx).edit().putString("image_sha256", sha256).putLong("image_hashed", hashed).apply()
    }

    fun clearPicked(ctx: Context) {
        p(ctx).edit().remove("image_uri").remove("image_name").remove("image_bytes").remove("image_sha256").remove("image_hashed").apply()
    }

    fun source(ctx: Context): Source = runCatching { Source.valueOf(p(ctx).getString("image_source", null) ?: "GZ") }.getOrDefault(Source.GZ)

    fun setSource(ctx: Context, s: Source) {
        p(ctx).edit().putString("image_source", s.name).apply()
    }

    /** One launch of the session (or the dry run). */
    data class Launch(
        val sessionId: String,
        val dryRun: Boolean,
        val profileId: String,
        val imageId: String,
        val wallMs: Long,
        /** `elapsedRealtime` at the launch: smaller later = the phone restarted. */
        val elapsedMs: Long,
        val logsCollected: Boolean,
        /** How it ended ([com.fahadali.oneos.core.root.OutcomeKind] name), once the logs were read; null before. */
        val outcome: String? = null,
        /** Seconds into the session when it ended, when the logs say. */
        val afterSeconds: Int? = null,
    ) {
        /** Wall clock minus uptime is the boot time; a boot after the launch means the phone restarted since. */
        fun rebootedSince(nowWall: Long = System.currentTimeMillis(), nowElapsed: Long = SystemClock.elapsedRealtime()): Boolean =
            nowWall - nowElapsed > wallMs + BOOT_SLACK_MS || nowElapsed < elapsedMs

        companion object {
            const val BOOT_SLACK_MS = 30_000L
        }
    }

    fun lastLaunch(ctx: Context): Launch? {
        val s = p(ctx)
        val id = s.getString("launch_session", null) ?: return null
        return Launch(
            sessionId = id,
            dryRun = s.getBoolean("launch_dry", false),
            profileId = s.getString("launch_profile", "").orEmpty(),
            imageId = s.getString("launch_image", "").orEmpty(),
            wallMs = s.getLong("launch_wall", 0),
            elapsedMs = s.getLong("launch_elapsed", 0),
            logsCollected = s.getBoolean("launch_logs", false),
            outcome = s.getString("launch_outcome", null),
            afterSeconds = s.getInt("launch_after", -1).takeIf { it >= 0 },
        )
    }

    /** Saved before root runs `launch`: committed now, because the app dies a few seconds later. */
    @SuppressLint("ApplySharedPref")
    fun saveLaunch(ctx: Context, l: Launch) {
        p(ctx).edit()
            .putString("launch_session", l.sessionId)
            .putBoolean("launch_dry", l.dryRun)
            .putString("launch_profile", l.profileId)
            .putString("launch_image", l.imageId)
            .putLong("launch_wall", l.wallMs)
            .putLong("launch_elapsed", l.elapsedMs)
            .putBoolean("launch_logs", false)
            .remove("launch_outcome")
            .remove("launch_after")
            .commit()
    }

    /** The logs of [sessionId] were collected, and how it ended (kept for Home's "Last time" row). */
    fun setLogsCollected(ctx: Context, sessionId: String?, outcome: String? = null, afterSeconds: Int? = null) {
        if (sessionId == null || lastLaunch(ctx)?.sessionId != sessionId) return
        p(ctx).edit()
            .putBoolean("launch_logs", true)
            .putString("launch_outcome", outcome)
            .putInt("launch_after", afterSeconds ?: -1)
            .apply()
    }

    fun forgetLaunch(ctx: Context) {
        p(ctx).edit().remove("launch_session").remove("launch_dry").remove("launch_profile").remove("launch_image")
            .remove("launch_wall").remove("launch_elapsed").remove("launch_logs").remove("launch_outcome").remove("launch_after").apply()
    }

    /**
     * The warnings the user ticked (owner, 2026-10-24: asked once per phone and
     * system), as [com.fahadali.oneos.core.model.Gate.tickKey]s, which hold the
     * text: a changed text, or another system, asks again.
     */
    fun acks(ctx: Context, profileId: String, imageId: String): Set<String> =
        p(ctx).getStringSet(ackKey(profileId, imageId), null)?.toSet().orEmpty()

    fun addAcks(ctx: Context, profileId: String, imageId: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val all = acks(ctx, profileId, imageId) + keys
        p(ctx).edit().putStringSet(ackKey(profileId, imageId), all.toSet()).apply()
    }

    private fun ackKey(profileId: String, imageId: String) = "acks|$profileId|$imageId"

    /** Hide Google apps in the other system (Advanced; only an image that has them uses it). Off by default. */
    fun hideGms(ctx: Context): Boolean = p(ctx).getBoolean("hide_gms", false)

    fun setHideGms(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean("hide_gms", on).apply()
    }

    /** This phone's Samsung IMS app in the other system, for VoLTE (Advanced; ONEOS_GUEST_SEC_IMS). Off by default. */
    fun secIms(ctx: Context): Boolean = p(ctx).getBoolean("sec_ims", false)

    fun setSecIms(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean("sec_ims", on).apply()
    }

    /**
     * Answer Knox's edmnativehelper while the other system runs, so a camera open
     * is not 5 s late (Advanced; `ONEOS_GUEST_EDM_STUB`). On by default since
     * 0.4.33: in the 0.4.32 reports every camera open in Infinity-X, Pixel 9 Pro
     * XL and ColorOS sessions waited the full 5 s for it (Z Flip5 48 times, S23
     * 16, S24/S25 Ultra, A56, A57) and CameraX apps gave up at 3 s
     * (ERROR_CAMERA_OPEN_TIMEOUT), so the camera "did not work" while Open Camera,
     * which waits longer, did. The stand-in answers "allowed", what One UI's own
     * service answers on a phone without a Knox camera policy; it can be turned
     * off in Advanced.
     */
    fun edmStub(ctx: Context): Boolean = p(ctx).getBoolean("edm_stub", true)

    fun setEdmStub(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean("edm_stub", on).apply()
    }

    /**
     * Switch with a screen lock set (Advanced, 0.4.32; owner's call). Off by
     * default. One UI's data is encrypted behind the lock, so a session that
     * leaves the phone unable to accept the credential is a factory reset, not
     * an inconvenience: a tester lost a phone that way on 2026-10-23, before the
     * weaver and gatekeeper guards. An unreadable lock state still blocks.
     */
    fun allowScreenLock(ctx: Context): Boolean = p(ctx).getBoolean("allow_screen_lock", false)

    fun setAllowScreenLock(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean("allow_screen_lock", on).apply()
    }

    /**
     * Cool down when hot (Advanced; owner 2026-10-27), `ONEOS_HEAT_CAP` for every
     * launch. On by default: One UI's heat control (SSRM) does not run in the
     * other system, so the session caps the CPU from 45 °C skin (oneos_switch.sh).
     */
    fun heatCap(ctx: Context): Boolean = p(ctx).getBoolean("heat_cap", true)

    fun setHeatCap(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean("heat_cap", on).apply()
    }

    /**
     * The Home of the last finished check (HomeState.snapshot), shown
     * read-only on the next cold start while that start's check runs
     * (Readiness.coldStartHome); null when none or not readable.
     */
    fun homeSnapshot(ctx: Context): HomeSnapshot? = HomeSnapshot.parse(p(ctx).getString("home_snapshot", null))

    fun setHomeSnapshot(ctx: Context, s: HomeSnapshot) {
        p(ctx).edit().putString("home_snapshot", s.encode()).apply()
    }

    /** Home showed "Welcome back" for [sessionId] (then it becomes the "Last time" row). */
    fun welcomeSeenFor(ctx: Context, sessionId: String): Boolean = p(ctx).getString("welcome_seen", null) == sessionId

    fun setWelcomeSeen(ctx: Context, sessionId: String) {
        p(ctx).edit().putString("welcome_seen", sessionId).apply()
    }
}
