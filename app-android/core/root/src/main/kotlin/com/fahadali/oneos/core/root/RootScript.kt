package com.fahadali.oneos.core.root

import com.fahadali.oneos.core.model.PackageSets

/**
 * The OneOS root script (`app/src/full/assets/root/oneos_root.sh`, OneOS
 * only: OneOS Probe ships no root script) and the launcher that runs it
 * (friend build 2, AA10).
 *
 * Every root command that changes something is `su -c "<launcher>"`: a fixed
 * text with only the verb's arguments, the script's pinned SHA-256 and its
 * length filled in. The launcher runs `$STAGE/bin/oneos_root.sh` only when
 * the stage, its `bin` folder and the script are root's (writable by root
 * only, not links) and the script's SHA-256 is the pinned one; never the
 * app-writable copy the read-only lists run from. Only `begin` may install the
 * script first, and `recover` into a stage OneOS already prepared (an older
 * build's script in `bin`, so its unfinished session can still be restored):
 * then the app writes the script to su's stdin, and the launcher checks the
 * same hash before it moves it into place and runs it. `old-list` and `purge`
 * (0.3.5) may install it the same way as `recover`, so old stages can be
 * listed and deleted right after an app update.
 *
 * The stage's path up to 0.3.6 was [LEGACY_STAGE] (OneLineage's name). While
 * there is nothing at [STAGE] and the old path is a folder, the launcher works
 * on the old stage exactly as on the new one, and the script renames it to
 * [STAGE] before its verb, only while it is idle (`migrate_legacy`), with its
 * set-aside copies (`lineage_stage.old-*` to `oneos.old-*`, the same suffix).
 *
 * Arguments are fixed words, decimal numbers and lowercase hex only
 * ([problems]), so nothing in the command needs quoting. `begin` and `finish`
 * name the profile, the image and its file set (root refuses a set that is not
 * that image's on that phone); `launch` carries this launch's
 * `ONEOS_GUEST_HIDE_GMS`, `ONEOS_HEAT_CAP`, `ONEOS_GUEST_SEC_IMS` and (0.4.32)
 * `ONEOS_GUEST_EDM_STUB` (0 or 1 each).
 */
object RootScript {
    const val ASSET = "root/oneos_root.sh"
    const val STAGE = "/data/local/tmp/oneos"

    /** The stage's path up to 0.3.6: renamed to [STAGE] by the root script (migrate_legacy). */
    const val LEGACY_STAGE = "/data/local/tmp/lineage_stage"

    /** What the launcher's checks look at: the phone, or a fake root in the tests. */
    data class Layout(
        val stage: String,
        /** The stage's path up to 0.3.6, used while there is nothing at [stage]. */
        val legacy: String,
        val sh: String,
        val proc: String,
        /** The uid that must own the stage (0 on the phone). */
        val uid: String,
        /** chown argument for what the launcher creates. */
        val owner: String,
    ) {
        companion object {
            val PHONE = Layout(STAGE, LEGACY_STAGE, "/system/bin/sh", "/proc", "0", "0:0")
        }
    }

    enum class Stdin {
        /** stdin is closed at once. */
        NONE,

        /** begin and recover: the script itself (installed when the stage has no valid copy). */
        SCRIPT,

        /** receive, table, env: exactly the announced number of bytes. */
        DATA,
    }

    enum class Verb(val word: String, val stdin: Stdin) {
        BEGIN("begin", Stdin.SCRIPT),
        RECEIVE("receive", Stdin.DATA),
        KEEP_RAW("keep-raw", Stdin.NONE),
        TABLE("table", Stdin.DATA),
        ENV("env", Stdin.DATA),
        FINISH("finish", Stdin.NONE),
        LAUNCH("launch", Stdin.NONE),
        RECOVER("recover", Stdin.SCRIPT),

        /** Read only: the stages begin aside left (0.3.5). */
        OLD_LIST("old-list", Stdin.SCRIPT),

        /** Deletes one old stage after its checks (0.3.5). */
        PURGE("purge", Stdin.SCRIPT),

        /** Uses a set-aside stage (`oneos.old-*`) again; the stage moves aside (0.4.10). */
        RESTORE("restore", Stdin.SCRIPT),

        /** Deletes every copy, the stage, the log capture and the session's helper files (0.4.25). */
        REMOVE("remove", Stdin.SCRIPT),
    }

    /**
     * What `begin aside` names a stage it moves away (`oneos.old-<epoch>[-<pid>]`),
     * and what `purge` renames one to before deleting it (`oneos.trash-<epoch>`);
     * also their names up to 0.3.6 (`lineage_stage.old-*`, `lineage_stage.trash-*`),
     * listed and deleted under them until the root script renames them.
     */
    val OLD_STAGE_NAME = Regex("^(oneos|lineage_stage)\\.(old|trash)-[0-9]{1,12}(-[0-9]{1,10})?$")

    /** `begin` modes: an empty stage, adopt the stage and its guest data, or move it aside first. */
    enum class BeginMode(val word: String) { NEW("new"), KEEP("keep"), ASIDE("aside") }

    /** Session scripts from switcher/assets and the expected installd table, staged with every set. */
    val SCRIPTS: List<String> = listOf(
        "oneos_switch.sh", "guest_prepare.sh", "guest_start.sh", "guest_props.sh", "return_to_oneui.sh",
        "check_host_apex.sh", "bridges.sh", "fold8_coloros.sh", "fold8_pixel.sh", "guest_google.sh", "zygote_exec", "OneOSEdmStub.dex", "oneos_btaudio", "OneOSOverlayDimen.dex", "host_installd.txt",
    )

    const val RAW_NAME = "system.raw"

    /** Every name `receive` may get: the image, the scripts and the files of the built-in sets. */
    val RECEIVE_NAMES: Set<String> = (listOf(RAW_NAME) + SCRIPTS + PackageSets.ALL.flatMap { it.names }).toSet()

    private val TOKEN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    private val ID = Regex("^[a-z0-9][a-z0-9-]{0,31}$")
    private val DEC = Regex("^[0-9]{1,18}$")
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val HEX16 = Regex("^[0-9a-f]{16}$")

    /** Problems with these arguments for [verb]; empty = the launcher may carry them. */
    fun problems(verb: Verb, args: List<String>): List<String> {
        val p = mutableListOf<String>()
        args.filterNot(TOKEN::matches).forEach { p += "argument '${it.take(40)}' is not a plain word" }
        fun need(n: Int) {
            if (args.size != n) p += "${verb.word} takes $n arguments, got ${args.size}"
        }
        fun arg(i: Int, re: Regex, what: String) {
            val a = args.getOrNull(i) ?: return
            if (!re.matches(a)) p += "${verb.word}: '$a' is not $what"
        }
        when (verb) {
            Verb.BEGIN -> {
                need(5)
                args.getOrNull(0)?.let { m -> if (BeginMode.entries.none { it.word == m }) p += "begin: mode '$m'" }
                arg(1, ID, "a profile id")
                arg(2, ID, "an image id")
                arg(3, ID, "a file set id")
                arg(4, DEC, "a versionCode")
            }
            Verb.RECEIVE -> {
                need(3)
                args.getOrNull(0)?.let { if (it !in RECEIVE_NAMES) p += "receive: '$it' is not a staged file" }
                arg(1, DEC, "a size")
                arg(2, HEX64, "a SHA-256")
            }
            Verb.KEEP_RAW -> {
                need(2)
                arg(0, DEC, "a size")
                arg(1, HEX64, "a SHA-256")
            }
            Verb.TABLE, Verb.ENV -> {
                need(1)
                arg(0, DEC, "a size")
            }
            Verb.FINISH -> {
                need(4)
                arg(0, ID, "a profile id")
                arg(1, ID, "an image id")
                arg(2, ID, "a file set id")
                arg(3, DEC, "a versionCode")
            }
            Verb.LAUNCH -> {
                need(7)
                args.getOrNull(0)?.let { if (it != "session" && it != "dry") p += "launch: mode '$it'" }
                arg(1, DEC, "a versionCode")
                arg(2, HEX16, "a session id")
                args.getOrNull(3)?.let { if (it != "0" && it != "1") p += "launch: hide Google services '$it' is not 0 or 1" }
                args.getOrNull(4)?.let { if (it != "0" && it != "1") p += "launch: heat cap '$it' is not 0 or 1" }
                args.getOrNull(5)?.let { if (it != "0" && it != "1") p += "launch: Samsung IMS '$it' is not 0 or 1" }
                args.getOrNull(6)?.let { if (it != "0" && it != "1") p += "launch: Knox edm stub '$it' is not 0 or 1" }
            }
            Verb.RECOVER, Verb.OLD_LIST, Verb.REMOVE -> need(0)
            Verb.PURGE -> {
                need(1)
                args.getOrNull(0)?.let { if (!OLD_STAGE_NAME.matches(it)) p += "purge: '${it.take(40)}' is not an old stage" }
            }
            Verb.RESTORE -> {
                need(1)
                args.getOrNull(0)?.let {
                    if (!OLD_STAGE_NAME.matches(it) || !it.startsWith("oneos.old-")) p += "restore: '${it.take(40)}' is not a set-aside stage"
                }
            }
        }
        return p
    }

    /**
     * The launcher text for `su -c`. [scriptSha256] and [scriptBytes] are the
     * pinned values (BuildConfig), never read from a file at run time.
     */
    fun launcher(verb: Verb, args: List<String>, scriptSha256: String, scriptBytes: Long, layout: Layout = Layout.PHONE): String {
        val p = problems(verb, args)
        require(p.isEmpty()) { p.joinToString("; ") }
        require(HEX64.matches(scriptSha256)) { "bad script hash" }
        require(scriptBytes in 1..1_048_576) { "bad script size" }
        for (v in listOf(layout.stage, layout.legacy, layout.sh, layout.proc)) require(PATH.matches(v)) { "bad path $v" }
        require(Regex("^[0-9]{1,10}$").matches(layout.uid) && Regex("^[0-9]{1,10}:[0-9]{1,10}$").matches(layout.owner)) { "bad owner" }
        return TEMPLATE
            .replace("@ARGS@", (listOf(verb.word) + args).joinToString(" "))
            .replace("@STAGE@", layout.stage)
            .replace("@LEGACY@", layout.legacy)
            .replace("@SH@", layout.sh)
            .replace("@PROC@", layout.proc)
            .replace("@UID@", layout.uid)
            .replace("@OWNER@", layout.owner)
            .replace("@SHA@", scriptSha256)
            .replace("@LEN@", scriptBytes.toString())
    }

    private val PATH = Regex("^/[A-Za-z0-9._/-]+$")

    /**
     * The launcher. Runs the installed script when it is root's and has the
     * pinned hash; otherwise only `begin` and `remove` (0.4.25) continue, and `recover` when the
     * stage has a marker and the stage and its `bin` are root's folders
     * already (0.3.3: a 0.3.2 stage's script differs from this build's pin,
     * and its unfinished session must still be restorable; nothing is made
     * for it): refuse when anything is mounted at or under the stage, make the
     * stage (and its `bin`) root's folder, read exactly @LEN@ bytes from
     * stdin, check their hash, and only then put them in place and run them.
     * A `bin` that is not root's folder is renamed aside, never deleted.
     * While nothing is at @STAGE@ and @LEGACY@ (the stage's path up to 0.3.6)
     * is a folder, not a link, all of this is done in @LEGACY@ instead; the
     * script then renames it to @STAGE@ (migrate_legacy).
     */
    const val TEMPLATE = $$"""umask 022
set -- @ARGS@
S=@STAGE@
L=@LEGACY@
if [ ! -e "$S" ] && [ ! -L "$S" ] && [ -d "$L" ] && [ ! -L "$L" ]; then
    S=$L
fi
F=$S/bin/oneos_root.sh
W=@SHA@
mine() {
    [ -L "$1" ] && return 1
    [ -e "$1" ] || return 1
    case "$(stat -c '%u %A' "$1" 2>/dev/null)" in
        "@UID@ "?????w*|"@UID@ "????????w*) return 1 ;;
        "@UID@ "*) return 0 ;;
    esac
    return 1
}
if mine "$S" && mine "$S/bin" && mine "$F" && [ "$(sha256sum "$F" 2>/dev/null | cut -d' ' -f1)" = "$W" ]; then
    exec @SH@ "$F" "$@"
fi
case "$1" in
    begin|remove) ;;
    recover)
        if [ ! -f "$S/.oneos_stage" ] || [ ! -d "$S/bin" ] || ! mine "$S" || ! mine "$S/bin"; then
            echo "@@REFUSED OneOS's root script is not in the stage: nothing to restore"
            exit 2
        fi
        ;;
    old-list|purge|restore)
        if [ ! -f "$S/.oneos_stage" ] || [ ! -d "$S/bin" ] || ! mine "$S" || ! mine "$S/bin"; then
            echo "@@REFUSED OneOS has no prepared stage on this phone: prepare one first"
            exit 2
        fi
        ;;
    *)
        echo "@@REFUSED OneOS's root script is not in the stage, or it changed: prepare again"
        exit 2
        ;;
esac
for m in @PROC@/1/mountinfo @PROC@/self/mountinfo; do
    awk '$5 == "/" { f = 1 } END { exit !f }' "$m" 2>/dev/null || { echo "@@REFUSED could not read $m"; exit 2; }
done
if awk -v s="$S" '$5 == s || index($5, s "/") == 1 { f = 1 } END { exit !f }' @PROC@/1/mountinfo @PROC@/self/mountinfo; then
    echo "@@REFUSED something is mounted in the stage - restart the phone first"
    exit 2
fi
if [ -L "$S" ] || { [ -e "$S" ] && [ ! -d "$S" ]; }; then
    echo "@@REFUSED $S is not a folder"
    exit 2
fi
[ -d "$S" ] || mkdir "$S" || { echo "@@FAILED could not make the stage"; exit 4; }
chown @OWNER@ "$S" && chmod 0755 "$S" || { echo "@@FAILED could not make the stage root's"; exit 4; }
if [ -e "$S/bin" ] || [ -L "$S/bin" ]; then
    if [ -L "$S/bin" ] || [ ! -d "$S/bin" ] || ! mine "$S/bin"; then
        mv "$S/bin" "$S/bin.old-$(date +%s)-$$" || { echo "@@FAILED could not move the old bin aside"; exit 4; }
    fi
fi
[ -d "$S/bin" ] || mkdir "$S/bin" || { echo "@@FAILED could not make the stage's bin"; exit 4; }
chown @OWNER@ "$S/bin" && chmod 0755 "$S/bin" || { echo "@@FAILED could not make bin root's"; exit 4; }
rm -f "$F.new"
head -c @LEN@ > "$F.new" || { rm -f "$F.new"; echo "@@FAILED could not write the root script"; exit 4; }
if [ "$(sha256sum "$F.new" 2>/dev/null | cut -d' ' -f1)" != "$W" ]; then
    rm -f "$F.new"
    echo "@@REFUSED the root script did not arrive intact"
    exit 2
fi
chown @OWNER@ "$F.new" && chmod 0755 "$F.new" && mv -f "$F.new" "$F" || { echo "@@FAILED could not install the root script"; exit 4; }
exec @SH@ "$F" "$@"
"""

    /** Exit codes of the script and the launcher. */
    const val EXIT_OK = 0
    const val EXIT_REFUSED = 2
    const val EXIT_USAGE = 3
    const val EXIT_FAILED = 4
}

/**
 * What the script printed: `@@STEP`, `@@INFO`, `@@RESULT`, `@@OK`,
 * `@@REFUSED`, `@@FAILED`; any other line is kept as log text.
 */
class RootReply {
    val steps = mutableListOf<String>()
    val infos = mutableListOf<String>()
    val results = mutableListOf<String>()
    val log = mutableListOf<String>()
    var ok: String? = null
        private set
    var refused: String? = null
        private set
    var failed: String? = null
        private set

    /** Returns the step text when the line was `@@STEP`, else null. */
    fun accept(line: String): String? {
        val l = line.trimEnd('\r')
        fun rest(tag: String) = l.removePrefix(tag).trim().take(500)
        when {
            l.startsWith("@@STEP ") -> return rest("@@STEP").also { steps += it }
            l.startsWith("@@INFO ") -> infos += rest("@@INFO")
            l.startsWith("@@RESULT ") -> results += rest("@@RESULT")
            l.startsWith("@@OK") -> ok = rest("@@OK")
            l.startsWith("@@REFUSED") -> refused = rest("@@REFUSED")
            l.startsWith("@@FAILED") -> failed = rest("@@FAILED")
            else -> if (log.size < MAX_LOG) log += l.take(500)
        }
        return null
    }

    /** One line for the screen: why it did not work, or null when it did. */
    fun problem(exitCode: Int?): String? = when {
        exitCode == RootScript.EXIT_OK && ok != null -> null
        refused != null -> refused
        failed != null -> "failed: $failed"
        exitCode == null -> "stopped before it finished"
        else -> "exit $exitCode" + (log.lastOrNull()?.let { ": $it" } ?: "")
    }

    private companion object {
        const val MAX_LOG = 200
    }
}
