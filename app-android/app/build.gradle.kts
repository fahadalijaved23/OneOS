import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
}

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun sha256(f: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

// --- What build 2 ships in the APK (AA8, AA9) --------------------------------
// The package sets, as the server built them (out/ is not in git): only the
// files the checked-in app-android/packages/<set>.sha256 names, with exactly
// those hashes, or the build fails. Deduplicated by SHA-256 into
// assets/oneos/files/<sha256>; per-set lists in assets/oneos/sets/.
val repoRoot: File = rootProject.projectDir.parentFile
val packagesDir: File = rootProject.file("packages")
// Every set carries OneOSGuest.apk (app-android/guest, the in-guest "Back to
// One UI" app; the ColorOS sets since their 2026-10-25 rebuild), and
// guest_prepare.sh puts it in the guest's /oem/app.
val packageSets: Map<String, Pair<File, List<String>>> = linkedMapOf(
    // Find N6 image, Z Fold (F8U). tethering_payload.img is never mounted.
    "n6-f8u" to (File(repoRoot, "out/overlays_n6_r50") to listOf("tethering_payload.img")),
    // Find X9 Ultra image, S Ultra: no fold grid either.
    "x9u-sultra" to (File(repoRoot, "out/overlays_x9u_r50") to listOf("tethering_payload.img", "OneOSWallpapersFoldGrid.apk")),
    // Find X9 Ultra IN image (0.3.3-0.3.6, again from 0.4.11), the bar phones:
    // the CN set's files (shared by hash) and its cell broadcast module.
    "x9u-in-sultra" to (File(repoRoot, "out/overlays_x9u_in_r51") to listOf("tethering_payload.img", "OneOSWallpapersFoldGrid.apk")),
    // Pixel 11 Fold image (0.3.7), Z Fold (F8U): everything a Pixel session uses,
    // OneOSGuest.apk included, and the fold hinge (scripts/make-fold8-pixel.py --variant f8u).
    "pixel-f8u" to (File(repoRoot, "out/overlays_pixel_f8u") to listOf("tethering_payload.img")),
    // Pixel 11 Pro image (0.4.10), Galaxy S26 Ultra: the same files from that image.
    "pixel-sultra" to (File(repoRoot, "out/overlays_pixel_kodiak") to listOf("tethering_payload.img")),
    // Find N6 image, Galaxy Z Fold8 (0.4.14, pull request #1): the Fold's set and the Fold8 fixes
    // scripts/make-fold8-coloros.py builds from it.
    "n6-f8" to (File(repoRoot, "out/overlays_n6_fold8") to listOf("tethering_payload.img")),
    "pixel-f8" to (File(repoRoot, "out/overlays_pixel_fold8") to listOf("tethering_payload.img")),
    // Pixel 9 Pro XL image (0.4.22), Galaxy S25 Ultra, S25, S25+ and S24 Ultra: the same files from that image.
    "pixel9-sultra" to (File(repoRoot, "out/overlays_pixel_komodo") to listOf("tethering_payload.img")),
    // Infinity-X image (0.4.27), every phone: one set from that image. Its cellbroadcast module
    // is built from source (0.4.28): the guest keeps One UI's, like tethering.
    "infinityx" to (File(repoRoot, "out/overlays_infinityx_r2") to listOf("tethering_payload.img", "cellbroadcast_payload.img")),
    // HyperOS image (docs/hyperos.md), made for the Z Fold8 Ultra and Z Fold8, on every phone from 0.4.31: Xiaomi's guarded jars,
    // the signal poll and the image's payloads.
    "hyper-f8u" to (File(repoRoot, "out/overlays_hyperos_r11") to listOf("tethering_payload.img")),
)
// The series downloads (0.4.25): -Poneos.series=<id> packs only the sets that
// app-android/packages/series.txt lists for it (ApkSeries keeps the same lists,
// ApkSeriesTest), the full edition's version name ends in "-<id>" and
// BuildConfig.SERIES names it. Without the property: every set, SERIES "".
val seriesId: String? = (findProperty("oneos.series") as String?)?.trim()?.takeIf { it.isNotEmpty() }
val seriesSets: Map<String, Set<String>> = File(packagesDir, "series.txt").readLines()
    .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    .associate { l -> l.split(Regex("\\s+")).let { it.first() to it.drop(1).toSet() } }
val packedSets: Set<String> = if (seriesId == null) {
    packageSets.keys
} else {
    val sets = seriesSets[seriesId] ?: throw GradleException("oneos.series: '$seriesId' is not in packages/series.txt (${seriesSets.keys})")
    (sets - packageSets.keys).takeIf { it.isNotEmpty() }?.let { throw GradleException("packages/series.txt: $seriesId names unknown sets $it") }
    sets
}
// Session scripts copied from switcher/assets at build time (plus the expected
// installd table). oneos_tunnel.sh (the server tunnel) and 7zz are never shipped.
val sessionScripts: Map<String, File> = linkedMapOf(
    "oneos_switch.sh" to File(repoRoot, "switcher/assets/oneos_switch.sh"),
    "guest_prepare.sh" to File(repoRoot, "switcher/assets/guest_prepare.sh"),
    "guest_start.sh" to File(repoRoot, "switcher/assets/guest_start.sh"),
    "guest_props.sh" to File(repoRoot, "switcher/assets/guest_props.sh"),
    "return_to_oneui.sh" to File(repoRoot, "switcher/assets/return_to_oneui.sh"),
    "check_host_apex.sh" to File(repoRoot, "switcher/assets/check_host_apex.sh"),
    "bridges.sh" to File(repoRoot, "switcher/assets/bridges.sh"),
    "fold8_coloros.sh" to File(repoRoot, "switcher/assets/fold8_coloros.sh"),
    "fold8_pixel.sh" to File(repoRoot, "switcher/assets/fold8_pixel.sh"),
    "guest_google.sh" to File(repoRoot, "switcher/assets/guest_google.sh"),
    "zygote_exec" to File(repoRoot, "switcher/assets/zygote_exec"),
    // 0.4.32: the Knox edmnativehelper stand-in (switcher/patches/OneOSEdmStub.java,
    // built by build_edm_stub.sh). It ships with the scripts, not in a package set,
    // so every image gets it without rebuilding one. oneos_switch.sh runs it only
    // when ONEOS_GUEST_EDM_STUB=1.
    "OneOSEdmStub.dex" to File(repoRoot, "switcher/assets/OneOSEdmStub.dex"),
    // 0.4.33: Android's Bluetooth audio factory over Samsung's on the phones that
    // only have Samsung's (switcher/native/oneos_btaudio.cpp, built by build.sh).
    // oneos_switch.sh starts it only on such a phone.
    "oneos_btaudio" to File(repoRoot, "switcher/assets/oneos_btaudio"),
    // 0.4.33: the status bar's dimension overlays on bar phones
    // (switcher/patches/OneOSOverlayDimen.java, build_overlay_dimen.sh).
    "OneOSOverlayDimen.dex" to File(repoRoot, "switcher/assets/OneOSOverlayDimen.dex"),
    "host_installd.txt" to File(repoRoot, "switcher/patches/host_installd.txt"),
)
val switcherNotShipped = setOf("oneos_tunnel.sh", "7zz")

/** `<sha256>  <name>` lines (`#` comments skipped), as sha256sum writes them. */
fun readShaList(f: File): Map<String, String> {
    val out = linkedMapOf<String, String>()
    f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { l ->
        val m = Regex("^([0-9a-f]{64})  ([A-Za-z0-9_][A-Za-z0-9._+-]*)$").matchEntire(l)
            ?: throw GradleException("${f.name}: not a sha256sum line: $l")
        check(out.put(m.groupValues[2], m.groupValues[1]) == null) { "${f.name}: ${m.groupValues[2]} listed twice" }
    }
    return out
}

fun renderShaList(entries: Map<String, String>): String =
    entries.entries.sortedBy { it.key }.joinToString("") { "${it.value}  ${it.key}\n" }

val scriptsList: String = renderShaList(sessionScripts.mapValues { (n, f) ->
    if (!f.isFile) throw GradleException("session script missing: $f")
    sha256(f)
})
// The root script is the full edition's only (src/full); OneOS Probe has none.
val rootScript: File = file("src/full/assets/root/oneos_root.sh")

/**
 * Imports the package sets and the session scripts into content-addressed
 * assets, and fails when a built file differs from the checked-in list.
 */
abstract class OneOsFilesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** set id -> "source dir|omitted,omitted". */
    @get:Input
    abstract val sets: MapProperty<String, String>

    /** name -> source file. */
    @get:Input
    abstract val scripts: MapProperty<String, String>

    @get:Input
    abstract val notShipped: SetProperty<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packages: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private fun sha(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun list(f: File): Map<String, String> {
        val out = linkedMapOf<String, String>()
        f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { l ->
            val m = Regex("^([0-9a-f]{64})  ([A-Za-z0-9_][A-Za-z0-9._+-]*)$").matchEntire(l)
                ?: throw GradleException("${f.name}: not a sha256sum line: $l")
            out[m.groupValues[2]] = m.groupValues[1]
        }
        return out
    }

    /** oneos_root.sh `receive`'s limit for one set file (PackagesTest ties the two). */
    private val receiveMax = 268_435_456L

    private fun render(entries: Map<String, String>) =
        entries.entries.sortedBy { it.key }.joinToString("") { "${it.value}  ${it.key}\n" }

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val files = File(out, "oneos/files").apply { mkdirs() }
        val setsDir = File(out, "oneos/sets").apply { mkdirs() }
        val sizes = sortedMapOf<String, Long>()
        fun put(src: File, sum: String) {
            val dst = File(files, sum)
            if (!dst.exists()) src.copyTo(dst)
            sizes[sum] = src.length()
        }
        for ((id, spec) in sets.get()) {
            val dir = File(spec.substringBefore('|'))
            val omitted = spec.substringAfter('|').split(',').filter { it.isNotBlank() }.toSet()
            val listFile = File(packages.get().asFile, "$id.sha256")
            if (!listFile.isFile) throw GradleException("$id: app-android/packages/$id.sha256 is missing")
            if (!dir.isDirectory) throw GradleException("$id: $dir is missing (build the overlays on the server first)")
            val want = list(listFile)
            val present = dir.listFiles()!!.filter { it.isFile }.map { it.name }.toSet()
            val problems = mutableListOf<String>()
            for (n in present - want.keys - omitted) problems += "$n is in $dir but not in packages/$id.sha256"
            for ((n, sum) in want) {
                val f = File(dir, n)
                when {
                    n in omitted -> problems += "$n is listed but left out of $id"
                    !f.isFile -> problems += "$n is missing from $dir"
                    f.length() > receiveMax -> problems += "$n is ${f.length()} bytes, over root's receive limit ($receiveMax)"
                    sha(f) != sum -> problems += "$n differs from packages/$id.sha256 (a rebuilt overlay?)"
                    else -> put(f, sum)
                }
            }
            if (problems.isNotEmpty()) throw GradleException("package set $id:\n  " + problems.joinToString("\n  "))
            File(setsDir, "$id.sha256").writeText(render(want))
        }
        val scriptSums = linkedMapOf<String, String>()
        for ((n, path) in scripts.get()) {
            val f = File(path)
            val sum = sha(f)
            put(f, sum)
            scriptSums[n] = sum
        }
        // A new file in switcher/assets is shipped or excluded on purpose, never silently.
        val switcher = File(scripts.get().getValue("oneos_switch.sh")).parentFile
        val unknown = switcher.listFiles()!!.filter { it.isFile }.map { it.name }.toSet() - scripts.get().keys - notShipped.get()
        if (unknown.isNotEmpty()) throw GradleException("switcher/assets has $unknown: add it to sessionScripts or switcherNotShipped in app/build.gradle.kts")
        File(setsDir, "scripts.sha256").writeText(render(scriptSums))
        File(files, "index.txt").writeText(sizes.entries.joinToString("") { "${it.key} ${it.value}\n" })
    }
}

val oneosFiles = tasks.register<OneOsFilesTask>("oneosFiles") {
    group = "build"
    description = "Packs the OneOS package sets and session scripts into the APK's assets, checked against app-android/packages."
    for ((dir, _) in packageSets.filterKeys { it in packedSets }.values) sources.from(fileTree(dir))
    for (f in sessionScripts.values) sources.from(f)
    sources.from(fileTree(File(repoRoot, "switcher/assets")))
    sets.set(packageSets.filterKeys { it in packedSets }.mapValues { (_, v) -> "${v.first.path}|${v.second.joinToString(",")}" })
    scripts.set(sessionScripts.mapValues { it.value.path })
    notShipped.set(switcherNotShipped)
    packages.set(packagesDir)
    outputDir.set(layout.buildDirectory.dir("generated/oneos/assets"))
}

// Release signing key: private to the account that builds releases, never in the
// repo (/srv/projects is shared). Every update must be signed with this same key.
val releaseKeys: Properties? =
    File(System.getProperty("user.home"), ".oneos-release/keystore.properties").takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use(::load) }
    }

android {
    namespace = "com.fahadali.oneos"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.fahadali.oneos"
        minSdk = 31
        targetSdk = 36
        // Friend build 2: the first build that stages and switches (docs/app/DESIGN.md, build 2).
        // 0.3.4: the Galaxy S25 profile, and OneOS Probe as its own app (flavor probe).
        // 0.3.5: Old stages (delete the stages Prepare moved aside).
        // 0.3.7: the Pixel 11 Fold image and its set on the Fold; the Find X9 Ultra IN image is gone.
        // 0.4.24: Pixel guests' camera calls with One UI's transaction codes (framework_patched.jar).
        // 0.4.26: a native-only service fails to start in the guest (Chrome stalled every app start).
        // 0.4.27: the Infinity-X system on every phone.
        // 0.4.28: Infinity-X keeps One UI's cellbroadcast module, sees hwservicemanager on HIDL-only phones.
        // 0.4.29: the Galaxy A57 (Exynos) and Z Flip5, in the s25 series; Send to the OneOS team.
        // 0.4.30: Quick switch between the systems set up on the phone; How's it going? in the guest;
        // the Pixel 9 Pro XL image on every s25 phone.
        // 0.4.31: HyperOS (the Xiaomi lhasa image) for everyone: made for the Z Fold8 Ultra and Z Fold8, the
        // other phones may try it (the cover layout runs as the phone's screen); Samsung's IMS stack in the
        // guest for VoLTE (Infinity-X and HyperOS, where the services.jar carve-out is); the guest's dp
        // scale, cutout, status bar height and corner radius from the phone itself on every image; the
        // Galaxy A56, in the s25 series.
        // 0.4.32: HyperOS starts on the Galaxy S25 Ultra, S25 and S24-era phones again (Xiaomi's
        // auto-installer took the guest's system_server down at boot phase 520); the camera opens
        // without the 5 s Knox wait when Advanced's switch is on (Z Flip5); the composer's panel
        // modes are recorded before the guest's SurfaceFlinger starts, so a display failure says why.
        // 0.4.33: the camera opens without Knox's 5 s wait by default (every 0.4.32
        // camera report), Infinity-X's camera is Aperture (OPPO's needs the Find X9
        // Ultra's camera config) and its status bar keeps off the corners, HyperOS's
        // wallpaper app starts on one-panel phones, the composer capture is written
        // where the logs are collected, and two new phones: Z Fold6, A07.
        versionCode = 50
        versionName = "0.4.33"
        // Send to the OneOS team (0.4.29, both editions): the report service's
        // address and its upload-only key, from the team's server (reports/deploy.sh
        // writes them). A build without them shows no Send; Share and Save stay.
        val home = File(System.getProperty("user.home"))
        val reportUrl = File(home, ".oneos-reports-url").takeIf { it.isFile }?.readText()?.trim()
            ?.takeIf { Regex("https://[a-z0-9.-]+").matches(it) }.orEmpty()
        val reportKey = File(home, ".oneos-reports-upload-key").takeIf { it.isFile }?.readText()?.trim()
            ?.takeIf { Regex("[0-9a-f]{32,128}").matches(it) }.orEmpty()
        buildConfigField("String", "REPORT_URL", "\"$reportUrl\"")
        buildConfigField("String", "REPORT_KEY", "\"$reportKey\"")
    }

    // One codebase, two apps (docs/app/DESIGN.md, 0.3.4): "full" is OneOS, the app
    // that switches; "probe" is OneOS Probe, only the device report, the read-only
    // tests and the findings, so anyone can run it and share what it finds. Shared
    // code is in src/main; what only OneOS has is in src/full.
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            // Pins for the root path: the launcher runs $STAGE/bin/oneos_root.sh only
            // with this SHA-256, and the app checks every list it ships against these.
            val rootBytes = rootScript.readBytes()
            buildConfigField("String", "ROOT_SCRIPT_SHA256", "\"${sha256(rootBytes)}\"")
            buildConfigField("long", "ROOT_SCRIPT_BYTES", "${rootBytes.size}L")
            buildConfigField("String", "SCRIPTS_SHA256", "\"${sha256(scriptsList.toByteArray())}\"")
            buildConfigField("String", "SERIES", "\"${seriesId.orEmpty()}\"")
            if (seriesId != null) versionNameSuffix = "-$seriesId"
            for ((id, _) in packageSets) {
                val f = File(packagesDir, "$id.sha256")
                val list = if (f.isFile) renderShaList(readShaList(f)) else ""
                buildConfigField("String", "SET_${id.uppercase().replace('-', '_')}_SHA256", "\"${sha256(list.toByteArray())}\"")
            }
        }
        create("probe") {
            dimension = "edition"
            // Its own app next to OneOS (its own root grant), same release key.
            applicationIdSuffix = ".probe"
            versionNameSuffix = "-probe"
        }
    }

    signingConfigs {
        if (releaseKeys != null) {
            create("release") {
                storeFile = file(releaseKeys.getProperty("storeFile"))
                storePassword = releaseKeys.getProperty("storePassword")
                keyAlias = releaseKeys.getProperty("keyAlias")
                keyPassword = releaseKeys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // What friends get: not debuggable, signed with the OneOS release key.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = true
        // Library modules run their own lintDebug; the app checks its own code.
        checkDependencies = false
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

// A release without the key would be an unsigned APK: refuse instead (both editions).
tasks.matching { it.name == "packageFullRelease" || it.name == "packageProbeRelease" }.configureEach {
    val hasKey = releaseKeys != null
    doFirst {
        check(hasKey) { "No release key: ~/.oneos-release/keystore.properties is missing (see app-android/README.md, section Build)." }
    }
}

// SESL ships its own androidx.* / material classes. The stock artifacts must
// never land next to them (duplicate classes), and picker-color shares
// picker-basic's androidx.picker namespace (manifest merger fails), so both
// are excluded everywhere (docs/app/DESIGN.md, top: "Two build gotchas").
configurations.configureEach {
    exclude(group = "androidx.core", module = "core")
    exclude(group = "androidx.core", module = "core-ktx")
    exclude(group = "androidx.customview", module = "customview")
    exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
    exclude(group = "androidx.viewpager2", module = "viewpager2")
    exclude(group = "androidx.viewpager", module = "viewpager")
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.fragment", module = "fragment")
    exclude(group = "androidx.fragment", module = "fragment-ktx")
    exclude(group = "androidx.preference", module = "preference")
    exclude(group = "androidx.recyclerview", module = "recyclerview")
    exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
    exclude(group = "com.google.android.material", module = "material")
    exclude(group = "io.github.oneuiproject.sesl", module = "picker-color")
}

/**
 * OneOS Probe must stay the read-only app (owner, 0.3.4): checks its packaged
 * APK and merged manifest after every probe build. The app's own assets are
 * the probe runner and its list only (library assets such as SESL's font are
 * fine); nothing of the full edition (the root script, the stage and log
 * lists, the session scripts, the package sets, its code and screens); no
 * text that tells the user to allow "OneOS" for root (OneOS Probe has its own
 * grant, next to OneOS); no permission; under [maxBytes].
 */
abstract class ProbeApkCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkDir: DirectoryProperty

    @get:Internal
    abstract val loader: Property<com.android.build.api.variant.BuiltArtifactsLoader>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    /** File names that only the full edition may carry (session scripts, root lists, set files). */
    @get:Input
    abstract val forbiddenNames: SetProperty<String>

    @get:Input
    abstract val maxBytes: Property<Long>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun run() {
        val apks = loader.get().load(apkDir.get())?.elements?.map { File(it.outputFile) }.orEmpty()
        if (apks.isEmpty()) throw GradleException("no probe APK in ${apkDir.get().asFile}")
        val problems = mutableListOf<String>()
        val lines = mutableListOf<String>()
        for (apk in apks) {
            if (apk.length() > maxBytes.get()) problems += "${apk.name} is ${apk.length()} B, over ${maxBytes.get()} B"
            ZipFile(apk).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                val assets = names.filter { it.startsWith("assets/") }
                for (want in listOf("assets/probe/probe.sh", "assets/probe/commands.list")) {
                    if (want !in names) problems += "${apk.name}: $want is missing"
                }
                for (n in assets) {
                    when {
                        n.startsWith("assets/root/") || n.startsWith("assets/logs/") || n.startsWith("assets/oneos/") ->
                            problems += "${apk.name}: $n is the full edition's"
                        n.startsWith("assets/probe/") && n != "assets/probe/probe.sh" && n != "assets/probe/commands.list" ->
                            problems += "${apk.name}: $n is not the probe runner or its list"
                    }
                }
                // Anywhere in the APK, by name: the session scripts, the lists, the set files.
                for (n in names) {
                    val base = n.substringAfterLast('/')
                    if (base in forbiddenNames.get() || base.endsWith(".guards.txt") || base.endsWith("_payload.img") || base.endsWith("_patched.jar") || base == "surfaceflinger_patched") {
                        problems += "${apk.name}: $n is a full-edition file"
                    }
                }
                // The full edition's code: its session package and its screens.
                val full = listOf("Lcom/fahadali/oneos/session/") + FULL_SCREENS.map { "Lcom/fahadali/oneos/ui/$it;" }
                for (dex in names.filter { Regex("classes[0-9]*\\.dex").matches(it) }) {
                    val text = zip.getInputStream(zip.getEntry(dex)).use { it.readBytes() }.toString(Charsets.ISO_8859_1)
                    for (f in full) if (text.contains(f)) problems += "${apk.name}: $dex has ${f.removePrefix("L").removeSuffix(";")}"
                }
                // The strings it shows (resources.arsc, UTF-8): every root prompt names OneOS Probe.
                // Not the dex: SwitchGates in core/model keeps OneOS's texts, and only OneOS runs it.
                val arsc = zip.getEntry("resources.arsc")?.let { e -> zip.getInputStream(e).use { it.readBytes() }.toString(Charsets.UTF_8) }.orEmpty()
                if (!arsc.contains("OneOS Probe")) problems += "${apk.name}: resources.arsc has no readable app_name"
                for (m in OTHER_APP_ROOT.findAll(arsc)) problems += "${apk.name}: a string says \"${m.value}\" (name OneOS Probe in src/probe)"
                lines += "${apk.name} ${apk.length()} B, assets: ${assets.joinToString(" ")}"
            }
        }
        val m = manifest.get().asFile.readText()
        for (a in FULL_SCREENS) if (m.contains(".ui.$a\"")) problems += "the merged manifest declares $a"
        // Its only permission is INTERNET, for Send to the OneOS team (0.4.29).
        for (p in Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(m).map { it.groupValues[1] }) {
            if (p != "android.permission.INTERNET") problems += "the merged manifest asks for $p"
        }
        if (problems.isNotEmpty()) throw GradleException("OneOS Probe carries what only OneOS may:\n  " + problems.distinct().joinToString("\n  "))
        report.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    companion object {
        val FULL_SCREENS = listOf("ImageActivity", "PrepareActivity", "SwitchActivity", "LastSessionActivity", "GapSplashActivity")

        /** "Allow OneOS", "for OneOS", "visible to OneOS", not followed by " Probe" (EditionsTest has the same). */
        val OTHER_APP_ROOT = Regex("""(?:[Aa]llow(?:ed for)?|for|visible to) OneOS\b(?! Probe)""")
    }
}

androidComponents {
    onVariants { variant ->
        when (variant.flavorName) {
            // Only OneOS carries the package sets and the session scripts.
            "full" -> variant.sources.assets?.addGeneratedSourceDirectory(oneosFiles, OneOsFilesTask::outputDir)
            "probe" -> {
                val name = variant.name.replaceFirstChar { it.uppercase() }
                val check = tasks.register<ProbeApkCheckTask>("check${name}Apk") {
                    group = "verification"
                    description = "Checks that the OneOS Probe APK holds only the read-only app."
                    apkDir.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
                    loader.set(variant.artifacts.getBuiltArtifactsLoader())
                    manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
                    // The session scripts, the root script and its lists, and every file of the package sets.
                    val setNames = packageSets.keys.flatMap { id -> File(packagesDir, "$id.sha256").takeIf { it.isFile }?.let { readShaList(it).keys }.orEmpty() }
                    forbiddenNames.set(sessionScripts.keys + setOf("oneos_root.sh", "status.list", "installd.list", "collect.list") + setNames)
                    maxBytes.set(30L * 1000 * 1000)
                    report.set(layout.buildDirectory.file("reports/probe-apk/${variant.name}.txt"))
                }
                tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(check) }
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:device"))
    implementation(project(":core:root"))
    implementation(project(":core:safety"))

    implementation(libs.sesl.core)
    implementation(libs.sesl.appcompat)
    implementation(libs.sesl.material)
    implementation(libs.sesl.recyclerview)
    implementation(libs.sesl.preference)
    implementation(libs.oneui.design)
    implementation(libs.oneui.icons)
}
