package com.fahadali.oneos.core.root

import com.fahadali.oneos.core.model.CarrierInfo
import com.fahadali.oneos.core.model.CarrierRules
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.PackageSet
import com.fahadali.oneos.core.model.PackageSets
import com.fahadali.oneos.core.model.PhoneProfile
import com.fahadali.oneos.core.model.ProbeStatus
import com.fahadali.oneos.core.model.SessionEnv
import com.fahadali.oneos.core.model.ShaList
import com.fahadali.oneos.core.model.StageMarker
import com.fahadali.oneos.core.model.StageState
import com.fahadali.oneos.core.model.StagedRaw
import com.fahadali.oneos.core.model.SystemFamily

/** One entry at the top of the stage (`stage.list`): `f`, `d`, `l`. */
data class StageEntry(val type: Char, val uid: Int, val mode: String, val size: Long, val name: String)

/** Whose guest data `$STAGE/data` holds (package names in its packages.xml only). */
enum class GuestData {
    NONE,
    EMPTY,
    COLOROS,
    LINEAGEOS,

    /** Pixel's launcher, com.google.android.apps.nexuslauncher (the Pixel image's system_ext/priv-app), and no ColorOS package (0.3.7). */
    PIXEL,

    /**
     * Infinity-X's updater, com.infinity.updater (0.4.27): its data also has
     * OPPO's (com.oplus., com.coloros.) and LineageOS's packages, so this
     * word goes first.
     */
    INFINITYX,
    /** Xiaomi's com.miui. packages (HyperOS), and no ColorOS or Pixel package. */
    HYPEROS,
    OTHER,
    ;

    /** The system that made the data, when its packages tell; null for none, an empty folder, LineageOS's or unknown data. */
    val family: SystemFamily?
        get() = when (this) {
            COLOROS -> SystemFamily.COLOROS
            PIXEL -> SystemFamily.PIXEL
            INFINITYX -> SystemFamily.INFINITYX
            HYPEROS -> SystemFamily.HYPEROS
            else -> null
        }

    /** Another system's data than [image]'s: never kept for it (root's `begin keep` refuses it too). */
    fun otherSystemThan(image: ImageRef): Boolean = family != null && family != image.family
}

enum class StageKind { ABSENT, FOLDER, LINK, FILE }

/**
 * What the read-only stage check (`assets/root/status.list`) found. Every
 * entry must have finished (ok or not present); otherwise [complete] is false
 * and the gates treat the stage as not checked (fail closed).
 */
data class StageStatus(
    val complete: Boolean,
    val problems: List<String>,
    val kind: StageKind,
    val ownerUid: Int?,
    val mode: String?,
    val entries: List<StageEntry>,
    val markerText: String?,
    val marker: StageMarker?,
    val sumsText: String?,
    /** SHA-256 by name relative to the stage (flat files and `bin/oneos_root.sh`). */
    val hashes: Map<String, String>,
    /** `system.raw` now: size and `dev:inode:size:mtime`. */
    val rawSize: Long?,
    val rawIdentity: String?,
    /** system.raw.id: `<size> <sha256> <dev:inode:size:mtime>`. */
    val rawId: List<String>?,
    val sessionEnv: String?,
    val tableSha: String?,
    val mountinfoRead: Boolean,
    val mounts: List<String>,
    val bridges: List<String>,
    val loops: List<String>,
    val bootId: String?,
    val persistActive: Boolean,
    val persist: Map<String, String>,
    val guest: GuestData,
    val oldStages: List<String>,
    val tools: List<String>,
    val props: Map<String, String>,
    /** 0.4.30: the system each copy OneOS moved aside holds (oneos.old-<time> to its marker's image id). */
    val oldImages: Map<String, String> = emptyMap(),
) {
    val exists: Boolean get() = kind != StageKind.ABSENT

    /** A folder owned by root and writable by root only. */
    val safe: Boolean
        get() = kind == StageKind.FOLDER && ownerUid == 0 && mode != null && !groupOrOtherWritable(mode)

    /** OneOS's own stage: safe, with a well-formed marker. */
    val ours: Boolean get() = safe && marker != null

    /**
     * A preparation of OneOS's that did not finish (stopped, or a step was
     * refused): root's folder with OneOS's root script in `bin`, no marker.
     */
    val partial: Boolean get() = safe && marker == null && entries.any { it.name == "bin/oneos_root.sh" && it.uid == 0 }

    /** A stage OneOS did not prepare (or one that is not root's any more). */
    val foreign: Boolean get() = exists && !ours && !partial

    /** Only what a stage needs to start with: nothing, or the launcher's `bin` and OneOS's own temp folders. */
    val empty: Boolean get() = !exists || entries.all { it.name == "bin" || it.name.startsWith("bin/") || it.name == "incoming" || it.name == "attic" }

    val rootScriptSha: String? get() = hashes["bin/oneos_root.sh"]

    /**
     * What `begin` may do with this stage to prepare [imageId] (null: not
     * identified yet), the suggested one first: an empty stage starts new;
     * OneOS's own stage (also one whose preparation did not finish), or one
     * with a ColorOS or Pixel guest, is kept ("Keep its apps and data") or
     * moved aside; anything else (a LineageOS guest, another app's leftovers)
     * is only moved aside. Guest data stays with the image that made it
     * (0.3.3: a phone may take more than one): OneOS's stage with another
     * image's data is only moved aside ([otherImageData]; root refuses to keep
     * it), and so is data of another system than [imageId]'s (0.3.7, a
     * stage's ColorOS data for the Pixel image or the other way round;
     * [GuestData.otherSystemThan]; root refuses that too). A link or a file in
     * the stage's place is refused.
     */
    fun beginModes(imageId: String?): List<RootScript.BeginMode> {
        val image = imageId?.let { Images.byId(it) }
        return when {
            kind == StageKind.LINK || kind == StageKind.FILE -> emptyList()
            !exists || empty -> listOf(RootScript.BeginMode.NEW)
            otherImageData(imageId) != null -> listOf(RootScript.BeginMode.ASIDE)
            image != null && guest.otherSystemThan(image) -> listOf(RootScript.BeginMode.ASIDE)
            ours -> listOf(RootScript.BeginMode.KEEP, RootScript.BeginMode.ASIDE)
            partial && guest != GuestData.LINEAGEOS -> listOf(RootScript.BeginMode.KEEP, RootScript.BeginMode.ASIDE)
            guest == GuestData.COLOROS || guest == GuestData.PIXEL || guest == GuestData.INFINITYX || guest == GuestData.HYPEROS ->
                listOf(RootScript.BeginMode.KEEP, RootScript.BeginMode.ASIDE)
            else -> listOf(RootScript.BeginMode.ASIDE)
        }
    }

    /**
     * The image whose guest data OneOS's stage holds (its marker's), when
     * that is not [imageId]: keeping it would boot [imageId] on another
     * build's /data. Null for the same image, no guest data, or no image yet.
     */
    fun otherImageData(imageId: String?): String? =
        marker?.imageId?.takeIf { ours && guest != GuestData.NONE && imageId != null && it != imageId }

    /** OneOS's own session never returned (a hard reset): its persist snapshot must be restored first. */
    val unfinishedSession: Boolean
        get() = ours && persistActive && persist["boot_id"] != null && persist["boot_id"] != bootId

    /**
     * A session runs now: something mounted in the stage, its image on a loop
     * device, or OneOS's own unfinished session marker. A foreign stage's
     * persist_session.active never counts (DA12): it is moved aside, unread.
     */
    val sessionActive: Boolean get() = mounts.isNotEmpty() || loops.isNotEmpty() || (ours && persistActive)

    val carrier: CarrierInfo get() = CarrierRules.from(props)

    /**
     * The SHA-256 root checked when it staged system.raw (system.raw.id), only
     * while that record still describes the file: same size, same
     * dev:inode:size:mtime. Null otherwise.
     */
    val rawVerifiedSha256: String?
        get() {
            val id = rawId ?: return null
            if (id.size != 3 || rawSize == null || id[0] != rawSize.toString() || id[2] != rawIdentity) return null
            return id[1].takeIf { HEX64.matches(it) }
        }

    /** The stage's system.raw for "Use the image already on this phone" (ImageIdentifier.identifyStaged). */
    val stagedRaw: StagedRaw get() = StagedRaw(rawSize, rawVerifiedSha256)

    /**
     * The image and set OneOS's marker names, when [profile] takes that image
     * with exactly that set (the staged image decides the set); null otherwise,
     * also for a 0.3.0-0.3.2 marker, which names no set.
     */
    fun staged(profile: PhoneProfile?, images: List<ImageRef> = Images.ALL, sets: List<PackageSet> = PackageSets.ALL): Pair<ImageRef, PackageSet>? {
        val m = marker?.takeIf { ours } ?: return null
        if (profile == null || m.profileId != profile.id) return null
        val c = profile.choice(m.imageId)?.takeIf { it.packageSetId == m.setId } ?: return null
        val image = Images.byId(c.imageId, images) ?: return null
        val set = PackageSets.byId(c.packageSetId, sets)?.takeIf { it.imageId == image.id } ?: return null
        return image to set
    }

    /**
     * The stage as the gates see it ([StageState]); null when the check did
     * not complete or could not read the mount table (fail closed). The files
     * count as verified only for the image and set the marker names ([staged]).
     */
    fun stageState(profile: PhoneProfile?): StageState? {
        if (!complete || !mountinfoRead) return null
        val staged = staged(profile)
        return StageState(
            mounts = mounts,
            activeBridges = bridges,
            sessionActive = sessionActive,
            marker = if (ours) marker else null,
            filesVerified = profile != null && staged != null && verify(profile, staged.first, staged.second).isEmpty(),
            unfinishedSession = unfinishedSession,
        )
    }

    /**
     * What differs from a prepared stage for [profile] with [image] and [set]:
     * the marker names exactly those, the files of files.sha256 (exactly the
     * scripts and the set) and their hashes now, no leftover file the scripts
     * would bind, system.raw.id against the image and the file's identity now,
     * session.env, the installd table. Root checks all of it again at launch;
     * this is what the Switch screen shows.
     */
    fun verify(profile: PhoneProfile, image: ImageRef, set: PackageSet): List<String> {
        val p = mutableListOf<String>()
        val m = marker?.takeIf { ours } ?: return listOf("the stage is not OneOS's")
        if (m.profileId != profile.id || m.imageId != image.id || m.setId != set.id) {
            p += "the marker names ${m.imageId} with ${m.setId ?: "no file set"} for ${m.profileId}, not ${image.id} with ${set.id} for ${profile.id}"
        }
        if (profile.choice(image.id)?.packageSetId != set.id || set.imageId != image.id) p += "${set.id} is not the ${image.shortName} set for ${profile.id}"
        val sums = sumsText?.let(ShaList::parse) ?: return listOf("files.sha256 is missing")
        p += sums.problems.map { "files.sha256: $it" }
        val want = RootScript.SCRIPTS + set.names
        for (n in want) {
            val s = sums.entries[n]
            when {
                s == null -> p += "files.sha256 does not list $n"
                hashes[n] == null -> p += "$n is missing"
                hashes[n] != s -> p += "$n changed since it was prepared"
            }
        }
        (sums.entries.keys - want.toSet()).forEach { p += "files.sha256 lists $it" }
        for (e in entries) {
            if (e.type == 'd' || e.name.contains('/')) continue
            if (LEFTOVER.matches(e.name) && e.name !in want) p += "${e.name} is in the stage but was not prepared"
        }
        val id = rawId
        when {
            rawSize == null -> p += "system.raw is missing"
            id == null || id.size != 3 -> p += "system.raw.id is missing"
            id[0] != image.rawBytes.toString() || id[1] != image.rawSha256 -> p += "system.raw is not the ${image.shortName} image"
            id[2] != rawIdentity -> p += "system.raw changed since it was verified"
        }
        val env = sessionEnv
        if (env == null) {
            p += "session.env is missing"
        } else {
            p += SessionEnv.validate(env, image = image).map { "session.env: $it" }
            if (!env.lines().contains("ONEOS_PROFILE=${profile.id}")) p += "session.env is not for ${profile.id}"
        }
        if (tableSha == null) p += "host_installd.table is missing"
        return p
    }

    companion object {
        /**
         * Flat names the session scripts bind, run or read (oneos_switch.sh's ONEOS_APP check, and more):
         * root's check_files list, with the Pixel set's oneos-telephony.jar and .sha256 (0.3.7).
         */
        val LEFTOVER = Regex("""^(.*_patched.*|.*_payload\.img|.*\.apk|.*\.jar|.*\.so|.*\.sh|Fold8AngleRelay\.dex|zygote_exec|oneos_btaudio|socket_exec|clean_apex_info_list\.xml|oneos-.*)$""")

        /** The list's entry ids and their output files. */
        val OUTPUTS: Map<String, String> = linkedMapOf(
            "stage.dir" to "stage/dir.txt",
            "stage.list" to "stage/list.txt",
            "stage.marker" to "stage/marker.txt",
            "stage.sums" to "stage/files.sha256",
            "stage.hashes" to "stage/hashes.txt",
            "stage.raw" to "stage/raw.txt",
            "stage.env" to "stage/session.env",
            "stage.table" to "stage/table.txt",
            "stage.mounts" to "stage/mounts.txt",
            "stage.bridges" to "stage/bridges.txt",
            "stage.loops" to "stage/loops.txt",
            "stage.session" to "stage/session.txt",
            "stage.guest" to "stage/guest.txt",
            "stage.old" to "stage/old.txt",
            "stage.old_images" to "stage/old_images.txt",
            "stage.tools" to "stage/tools.txt",
            "stage.props" to "stage/props.txt",
        )

        private fun groupOrOtherWritable(mode: String): Boolean {
            val m = mode.takeLast(3)
            if (m.length != 3 || m.any { it !in '0'..'7' }) return true
            return (m[1] - '0') and 2 != 0 || (m[2] - '0') and 2 != 0
        }

        /**
         * [statuses]: entry id to its result; [text]: output file to its
         * contents (null when the entry wrote none).
         */
        fun parse(statuses: Map<String, ProbeStatus>, text: (String) -> String?): StageStatus {
            val problems = mutableListOf<String>()
            for (id in OUTPUTS.keys) {
                val st = statuses[id]
                if (st != ProbeStatus.OK && st != ProbeStatus.NOT_PRESENT) problems += "$id ${st?.label ?: "did not run"}"
            }
            fun out(id: String): String? = if (statuses[id] == ProbeStatus.OK) text(OUTPUTS.getValue(id)) else null
            fun lines(id: String): List<String> = out(id)?.lines()?.map { it.trimEnd('\r') }?.filter { it.isNotBlank() }.orEmpty()

            val dir = lines("stage.dir").firstOrNull().orEmpty().split(' ')
            val kind = when {
                statuses["stage.dir"] == ProbeStatus.NOT_PRESENT -> StageKind.ABSENT
                dir.firstOrNull() == "dir" -> StageKind.FOLDER
                dir.firstOrNull() == "link" -> StageKind.LINK
                dir.firstOrNull() == "file" -> StageKind.FILE
                else -> {
                    problems += "stage.dir unreadable"
                    StageKind.FILE
                }
            }
            val entries = lines("stage.list").mapNotNull { l ->
                val p = l.split(' ', limit = 5)
                if (p.size != 5 || p[0].length != 1) return@mapNotNull null
                val uid = p[1].toIntOrNull() ?: return@mapNotNull null
                StageEntry(p[0][0], uid, p[2], p[3].toLongOrNull() ?: -1, p[4])
            }
            val markerText = out("stage.marker")
            val hashes = linkedMapOf<String, String>()
            for (l in lines("stage.hashes")) {
                val m = HASH_LINE.matchEntire(l) ?: continue
                hashes[m.groupValues[2]] = m.groupValues[1]
            }
            var rawSize: Long? = null
            var rawIdentity: String? = null
            var rawId: List<String>? = null
            for (l in lines("stage.raw")) {
                val p = l.split(' ')
                when (p.firstOrNull()) {
                    "now" -> if (p.size == 3) {
                        rawSize = p[1].toLongOrNull()
                        rawIdentity = p[2]
                    }
                    "id" -> rawId = p.drop(1)
                }
            }
            val mountLines = lines("stage.mounts")
            val session = lines("stage.session")
            val persist = session.filter { it.contains('=') && !it.startsWith("boot=") }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            val guestLines = lines("stage.guest")
            val guest = when {
                statuses["stage.guest"] != ProbeStatus.OK -> GuestData.NONE
                guestLines.any { it == "packages $INFINITYX_WORD" } -> GuestData.INFINITYX
                guestLines.any { it == "packages org.lineageos." } -> GuestData.LINEAGEOS
                guestLines.any { it == "packages com.oplus." || it == "packages com.coloros." } -> GuestData.COLOROS
                guestLines.any { it == "packages $PIXEL_WORD" } -> GuestData.PIXEL
                guestLines.any { it == "packages $HYPEROS_WORD" } -> GuestData.HYPEROS
                guestLines.any { it == "packages none" } -> GuestData.EMPTY
                else -> GuestData.OTHER
            }
            return StageStatus(
                complete = problems.isEmpty(),
                problems = problems,
                kind = kind,
                ownerUid = dir.getOrNull(1)?.toIntOrNull(),
                mode = dir.getOrNull(3),
                entries = entries,
                markerText = markerText,
                marker = markerText?.let(StageMarker::parse),
                sumsText = out("stage.sums"),
                hashes = hashes,
                rawSize = rawSize,
                rawIdentity = rawIdentity,
                rawId = rawId,
                sessionEnv = out("stage.env"),
                tableSha = lines("stage.table").firstOrNull()?.substringBefore(' ')?.takeIf { it.length == 64 },
                mountinfoRead = mountLines.contains("root ok"),
                mounts = mountLines.filter { it.startsWith("init ") || it.startsWith("self ") }.map { it.substringAfter(' ') }.distinct(),
                bridges = lines("stage.bridges"),
                loops = lines("stage.loops"),
                bootId = session.firstOrNull { it.startsWith("boot=") }?.removePrefix("boot=")?.takeIf { it.isNotBlank() },
                persistActive = session.contains("active"),
                persist = persist,
                guest = guest,
                oldStages = lines("stage.old"),
                tools = lines("stage.tools"),
                props = lines("stage.props").filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') },
                oldImages = lines("stage.old_images").mapNotNull { l ->
                    val name = l.substringBefore(' ')
                    val image = l.substringAfter(' ', "").trim()
                    if (name.startsWith("oneos.old-") && Regex("[a-z0-9-]{1,40}").matches(image)) name to image else null
                }.toMap(),
            )
        }

        private val HEX64 = Regex("^[0-9a-f]{64}$")

        /** The package status.list (and root's `begin keep`) takes for the Pixel guest's data: Pixel's launcher. */
        const val PIXEL_WORD = "com.google.android.apps.nexuslauncher"
        const val HYPEROS_WORD = "com.miui."

        /** Infinity-X's updater (its system_ext/priv-app/Updater). */
        const val INFINITYX_WORD = "com.infinity.updater"

        /** `sha256sum` lines of the stage (status.list reads the old path while the stage is still there). */
        private val HASH_LINE = Regex("""^([0-9a-f]{64}) [ *](?:${Regex.escape(RootScript.STAGE)}|${Regex.escape(RootScript.LEGACY_STAGE)})/(\S+)$""")
    }
}
