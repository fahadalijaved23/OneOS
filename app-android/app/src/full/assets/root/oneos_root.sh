#!/system/bin/sh
# OneOS root script, app 0.3.7 (versionCode 11): every change the OneOS app
# makes to the phone as root is one of the verbs below, and nothing else.
#
# The app never runs this file from its own storage. Its launcher
# (RootScript.kt, a fixed text) runs only $STAGE/bin/oneos_root.sh (while
# there is no $STAGE, the old stage's: $LEGACY/bin/oneos_root.sh, which
# migrate_legacy then renames), owned by root and writable by root only, and
# only when its SHA-256 equals the hash pinned in the app's BuildConfig. `begin` may first install it there, and
# `recover` into a stage OneOS prepared (an older build's script in bin, so its
# unfinished session can still be restored): the launcher reads it from stdin
# and checks the same hash before it runs.
# Arguments are fixed words, decimal numbers and hex SHA-256 only.
#
#   begin <new|keep|aside> <profile> <image> <set> <versionCode>
#           new: the stage must be empty. keep: adopt the stage and its guest
#           data (the guest's /data), only for the image that made that data
#           and never another system's (image_family).
#           aside: rename the stage to oneos.old-<epoch> and start an
#           empty one. <set> must be the file set of <image> on <profile>
#           (profile_set, set_image).
#   receive <name> <bytes> <sha256>   stdin: exactly <bytes> bytes of <name>
#   keep-raw <bytes> <sha256>         use the system.raw already in the stage
#   table <bytes>                     stdin: host_installd.table
#   env <bytes>                       stdin: session.env
#   finish <profile> <image> <set> <versionCode>
#   launch <session|dry> <versionCode> <sessionId> <hideGms> <heatCap> <secIms> <edmStub>
#                                     secIms 0|1: this launch's
#                                     ONEOS_GUEST_SEC_IMS (this phone's
#                                     Samsung IMS app in the guest)
#                                     edmStub 0|1: this launch's
#                                     ONEOS_GUEST_EDM_STUB (answer Knox's
#                                     edmnativehelper, so a camera open is
#                                     not 5 s late)
#                                     hideGms 0|1: this launch's
#                                     ONEOS_GUEST_HIDE_GMS (1 only for an
#                                     image in IMAGES_GMS); heatCap 0|1: its
#                                     ONEOS_HEAT_CAP (Cool down when hot)
#   recover                           restore One UI's persist props after a
#                                     session that never returned (hard reset)
#   old-list                          read only: every oneos.old-* and .trash-*
#                                     folder (and lineage_stage.old-*, .trash-*,
#                                     their names up to 0.3.6), its size, image
#                                     and state
#   purge <name>                      delete one of those (0.3.5), after checks
#   restore <name>                    use a set-aside stage again (0.4.10): the
#                                     stage moves aside, oneos.old-<name> back
#   remove                            delete everything OneOS put on the phone
#                                     (0.4.25): every copy, the stage, the log
#                                     capture and the session's helper files
#
# Writes: only under $STAGE. Documented exceptions, nothing else:
#   - begin aside renames $STAGE to its sibling $STAGE.old-<epoch>;
#   - restore renames $STAGE to $STAGE.old-<epoch> and one
#     oneos.old-<epoch>[-<pid>] that has a stage marker to $STAGE, only when
#     neither has anything mounted at or under it in any readable mount table,
#     no loop device uses a file in either, no bridge is up and neither holds
#     a session still open or one that never returned (a session earlier in
#     this boot that returned is fine); if the second rename fails, the first
#     is undone;
#   - remove moves $DATA/local/tmp/oneos_logs and the session's six helper
#     files there (adb_watchdog.sh/.log, ims_watchdog.sh/.log,
#     ui_activator.log, restore_app.log) into the stage, purges every copy,
#     then renames the stage to $STAGE.old-<epoch> and purges it, all after
#     purge's and restore's checks for every one of them;
#   - purge renames one oneos.old-<epoch>[-<pid>] to oneos.trash-<epoch>
#     and deletes it, only when it is root's, not a link, nothing is mounted
#     at or under it in any readable mount table, no loop device uses a file
#     in it, no bridge is up and it holds no session of this boot (checked
#     before the rename and again after it);
#   - launch moves itself out of the app's cgroup (/sys/fs/cgroup/cgroup.procs)
#     and starts oneos_switch.sh, the session, in init's mount namespace;
#   - recover runs the staged guest_props.sh recover (resetprop);
#   - before its verb, every run renames the stage and its copies from their
#     names up to app 0.3.6 (lineage_stage, lineage_stage.old-*, .trash-*) to
#     the OneOS names, each only while it is idle (migrate_legacy).
# Apart from purge (and remove, through it), nothing is ever deleted except this script's own files: incoming/*.part,
# incoming/plan and incoming/received, and an old system.raw that a new one
# replaces (an image, never user data). Everything else that is in the way is
# moved to $STAGE/attic/<epoch>/. Every refusal comes before the first change
# of its verb (`@@REFUSED <why>`, exit 2); bad arguments exit 3, a failed
# write exits 4 (`@@FAILED <what>`). The renames of migrate_legacy are no
# verb's: they come first, and stay when the verb then refuses.
#
# Tests (StageScriptTest, under dash and busybox ash) set ONEOS_TEST_ROOT to a
# fake root folder: every path below gets that prefix, and launch refuses after
# its checks and its session.env line, before anything starts. As uid 0 the
# prefix is refused outright.

umask 022
T="${ONEOS_TEST_ROOT:-}"
if [ -n "$T" ]; then
    [ "$(id -u)" != 0 ] || { echo "@@REFUSED ONEOS_TEST_ROOT is for tests, never as root"; exit 3; }
    ROOT_UID=$(id -u)
    ROOT_OWNER="$(id -u):$(id -g)"
else
    PATH="/system/bin:/system/xbin:$PATH"
    ROOT_UID=0
    ROOT_OWNER=0:0
fi
STAGE="$T/data/local/tmp/oneos"
# The stage's path up to app 0.3.6 (OneLineage's name): renamed to $STAGE by
# migrate_legacy, never written otherwise.
LEGACY="$T/data/local/tmp/lineage_stage"
# This script, as the launcher ran it ($STAGE/bin/oneos_root.sh, or the old
# stage's before migrate_legacy renames it).
SELF=$0
DATA="$T/data"
PROC="$T/proc"
SYSFS="$T/sys"
ADB="$T/data/adb"

# --- What this build stages (kept equal to the app's model by StageScriptTest) ---
APP_PACKAGE=com.fahadali.oneos
SPLASH_COMPONENT=com.fahadali.oneos/.ui.GapSplashActivity
OLD_SWITCHER=com.wewaffles.onelineage
IMAGE_SDK=37
# SHA-256 of the method lines of switcher/patches/host_installd.txt: the
# installd codes the guard jars carry (InstalldExpected.SHA256).
INSTALLD_SHA=791e52c441b62944f88244a2610b9433c869c9c3916a49f43d14552de82280f2
# Session scripts from switcher/assets plus the expected installd table.
SCRIPTS="oneos_switch.sh guest_prepare.sh guest_start.sh guest_props.sh return_to_oneui.sh check_host_apex.sh bridges.sh fold8_coloros.sh fold8_pixel.sh guest_google.sh zygote_exec OneOSEdmStub.dex oneos_btaudio OneOSOverlayDimen.dex host_installd.txt"
SET_COMMON="services_patched.jar oplus-services_patched.jar vold_patched service.incremental_patched.so OneOSGuest.apk ims-ext-common_patched.jar oplus-telephony-common_patched.jar surfaceflinger_patched guards.txt art_payload.img i18n_payload.img runtime_payload.img sdkext_payload.img crashrecovery_payload.img extservices_payload.img permission_payload.img wifi_payload.img appsearch_payload.img mediaprovider_payload.img adservices_payload.img rkpd_payload.img healthfitness_payload.img ondevicepersonalization_payload.img configinfrastructure_payload.img"
# The Pixel sets (the Fold's 0.3.7, the S26 Ultra's 0.4.10, the Pixel 9 Pro XL's 0.4.22): services.jar with the guest vold as gvld, the signal
# poll, Pixel's status bar RRO, the in-guest OneOS app and its own payloads.
SET_PIXEL="services_patched.jar framework_patched.jar vold_patched service.incremental_patched.so guards.txt oneos-telephony.jar oneos-telephony.sha256 OneOSPhoneInjection.apk OneOSPixelStatusBar.apk OneOSGuest.apk art_payload.img i18n_payload.img runtime_payload.img sdkext_payload.img crashrecovery_payload.img extservices_payload.img permission_payload.img wifi_payload.img appsearch_payload.img mediaprovider_payload.img adservices_payload.img rkpd_payload.img healthfitness_payload.img ondevicepersonalization_payload.img configinfrastructure_payload.img cellbroadcast_payload.img bt_payload.img nfcservices_payload.img telephonycore_payload.img conscrypt_payload.img profiling_payload.img gmssystem_payload.img"
# Infinity-X (0.4.27): both guard jars, the camera codes, the guest vold, the AOSP
# signal poll, the IMS backoff, the in-guest app and its own payloads.
SET_INFINITYX="services_patched.jar oplus-services_patched.jar framework_patched.jar vold_patched service.incremental_patched.so libcamera_client_patched.so guards.txt ims-ext-common_patched.jar oneos-telephony.jar oneos-telephony.sha256 OneOSPhoneInjection.apk OneOSGuest.apk OneOSPixelStatusBar.apk art_payload.img i18n_payload.img runtime_payload.img sdkext_payload.img crashrecovery_payload.img extservices_payload.img permission_payload.img wifi_payload.img appsearch_payload.img mediaprovider_payload.img adservices_payload.img rkpd_payload.img healthfitness_payload.img ondevicepersonalization_payload.img configinfrastructure_payload.img bt_payload.img nfcservices_payload.img telephonycore_payload.img conscrypt_payload.img profiling_payload.img gmssystem_payload.img"
# The HyperOS set (docs/hyperos.md; every phone from 0.4.31): services.jar and Xiaomi's two guarded jars, the signal poll, the
# QTI IMS backoff, the in-guest OneOS app and the image's payloads. No gvld pins yet.
SET_HYPEROS="services_patched.jar miui-services_patched.jar apprecovery.proinstaller_patched.jar framework_patched.jar miui-wifi-service_patched.jar miui-telephony-common_patched.jar vold_patched service.incremental_patched.so libcamera_client_patched.so guards.txt oneos-telephony.jar oneos-telephony.sha256 OneOSPhoneInjection.apk ims-ext-common_patched.jar OneOSGuest.apk socket_exec libaudiohal_aidl_patched.so MiWallpaper_patched.apk art_payload.img i18n_payload.img runtime_payload.img sdkext_payload.img crashrecovery_payload.img extservices_payload.img permission_payload.img wifi_payload.img appsearch_payload.img mediaprovider_payload.img adservices_payload.img rkpd_payload.img healthfitness_payload.img ondevicepersonalization_payload.img configinfrastructure_payload.img bt_payload.img"
# Host /data trees of switcher/assets/bridges.sh (relative to /data).
BRIDGE_HOSTS="app app-staging user user_de dalvik-cache misc/profiles data media misc/user misc_ce/0/sdksandbox misc_de/0/sdksandbox misc_ce/0/rollback misc_de/0/rollback resource-cache"
ENV_KEYS="ONEOS_APP ONEOS_PROFILE ONEOS_SPLASH_COMPONENT ONEOS_WALLPAPER_PROBE ONEOS_RECORD ONEOS_LOGCAT ONEOS_GUEST_FORM ONEOS_GUEST_FOLD ONEOS_COVER_TSP ONEOS_GUEST_FOLD_CONTINUE ONEOS_GUEST_UNFOLD_PORTRAIT ONEOS_GUEST_COMPACT_WINDOW ONEOS_GUEST_WALLPAPER_GRID ONEOS_GUEST_HIDE_GMS ONEOS_HEAT_CAP ONEOS_GUEST_SEC_IMS ONEOS_GUEST_EDM_STUB ONEOS_RETURN_MODE"
# Images whose Google services a session may hide (ONEOS_GUEST_HIDE_GMS=1;
# ImageRef.gmsHideable): the Find X9 Ultra IN build (again from 0.4.11). The
# Pixel images have them too, but their sessions present their own GmsCore,
# and oneos_switch.sh refuses to hide that.
IMAGES_GMS="coloros17-findx9u-in"

# <id>: <model prefix> <codename> <SoC> <form>. Enabled profiles only (the
# S25 Ultra from 0.3.1, the Galaxy S25 from 0.3.4, the Z Fold7 from 0.4.8,
# the Galaxy S25+ and the Z Fold8 from 0.4.10, the Exynos Galaxy S26+ and Z
# Flip7 from 0.4.17). A flip gets the phone settings (env_value: not fold).
profile_info() {
    case "$1" in
        f8u) echo "SM-F976 q8q SM8850 fold" ;;
        s-ultra-8850) echo "SM-S948 m3q SM8850 bar" ;;
        s-ultra-8750) echo "SM-S938 pa3q SM8750 bar" ;;
        s-8750) echo "SM-S931 pa1q SM8750 bar" ;;
        f7) echo "SM-F966 q7q SM8750 fold" ;;
        s-plus-8750) echo "SM-S936 pa2q SM8750 bar" ;;
        f8) echo "SM-F971 h8q SM8850 fold" ;;
        s-8850) echo "SM-S942 m1q SM8850 bar" ;;
        s-8850-jp) echo "SC-51G m1q SM8850 bar" ;;
        s-ultra-8650) echo "SM-S928 e3q SM8650 bar" ;;
        s-8550) echo "SM-S911 dm1q SM8550 bar" ;;
        s-plus-8550) echo "SM-S916 dm2q SM8550 bar" ;;
        s-plus-8850) echo "SM-S947 m2q SM8850 bar" ;;
        s-plus-9965) echo "SM-S947 m2s s5e9965 bar" ;;
        flip7) echo "SM-F766 b7s s5e9955 flip" ;;
        a56) echo "SM-A566 a56x s5e8855 bar" ;;
        a57) echo "SM-A576 a57x s5e8865 bar" ;;
        flip5) echo "SM-F731 b5q SM8550 flip" ;;
        f6) echo "SM-F956 q6q SM8650 fold" ;;
        a07) echo "SM-A075 a07 MT6789 bar" ;;
        *) return 1 ;;
    esac
}

# <profile> <image>: the file set staged with that image on that phone
# (Profiles: PhoneProfile.choices). From 0.3.3 a phone may take more than one
# image; the staged image decides the set.
profile_set() {
    case "$1 $2" in
        "f8u coloros17-findn6") echo n6-f8u ;;
        "f8u pixel11fold-yogi") echo pixel-f8u ;;
        "f8u hyperos-lhasa") echo hyper-f8u ;;
        "s-ultra-8850 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-ultra-8850 coloros17-findx9u") echo x9u-sultra ;;
        "s-ultra-8850 pixel11pro-kodiak") echo pixel-sultra ;;
        "s-ultra-8750 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-ultra-8750 coloros17-findx9u") echo x9u-sultra ;;
        "s-ultra-8750 pixel9proxl-komodo") echo pixel9-sultra ;;
        "s-8750 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-8750 coloros17-findx9u") echo x9u-sultra ;;
        "s-8750 pixel9proxl-komodo") echo pixel9-sultra ;;
        "f7 coloros17-findn6") echo n6-f8u ;;
        "s-plus-8750 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-plus-8750 coloros17-findx9u") echo x9u-sultra ;;
        "s-plus-8750 pixel9proxl-komodo") echo pixel9-sultra ;;
        "f8 coloros17-findn6") echo n6-f8 ;;
        "f8 pixel11fold-yogi") echo pixel-f8 ;;
        "s-8850 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-8850 coloros17-findx9u") echo x9u-sultra ;;
        "s-8850 pixel11pro-kodiak") echo pixel-sultra ;;
        "s-8850-jp coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-8850-jp coloros17-findx9u") echo x9u-sultra ;;
        "s-8850-jp pixel11pro-kodiak") echo pixel-sultra ;;
        "s-ultra-8650 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-ultra-8650 coloros17-findx9u") echo x9u-sultra ;;
        "s-ultra-8650 pixel9proxl-komodo") echo pixel9-sultra ;;
        "s-8550 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-8550 coloros17-findx9u") echo x9u-sultra ;;
        "s-8550 pixel9proxl-komodo") echo pixel9-sultra ;;
        "s-plus-8550 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-plus-8550 coloros17-findx9u") echo x9u-sultra ;;
        "s-plus-8550 pixel9proxl-komodo") echo pixel9-sultra ;;
        "s-plus-8850 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-plus-8850 coloros17-findx9u") echo x9u-sultra ;;
        "s-plus-8850 pixel11pro-kodiak") echo pixel-sultra ;;
        "s-plus-9965 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "s-plus-9965 coloros17-findx9u") echo x9u-sultra ;;
        "s-plus-9965 pixel11pro-kodiak") echo pixel-sultra ;;
        "flip7 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "flip7 coloros17-findx9u") echo x9u-sultra ;;
        "flip7 pixel9proxl-komodo") echo pixel9-sultra ;;
        "a56 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "a57 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "a56 coloros17-findx9u") echo x9u-sultra ;;
        "a57 coloros17-findx9u") echo x9u-sultra ;;
        "a56 pixel9proxl-komodo") echo pixel9-sultra ;;
        "a57 pixel9proxl-komodo") echo pixel9-sultra ;;
        "flip5 coloros17-findx9u-in") echo x9u-in-sultra ;;
        "flip5 coloros17-findx9u") echo x9u-sultra ;;
        "flip5 pixel9proxl-komodo") echo pixel9-sultra ;;
        "f8u infinityx41-findx9u") echo infinityx ;;
        "f8 infinityx41-findx9u") echo infinityx ;;
        "f8 hyperos-lhasa") echo hyper-f8u ;;
        "f7 infinityx41-findx9u") echo infinityx ;;
        "f7 hyperos-lhasa") echo hyper-f8u ;;
        "s-ultra-8850 infinityx41-findx9u") echo infinityx ;;
        "s-ultra-8850 hyperos-lhasa") echo hyper-f8u ;;
        "s-plus-8850 infinityx41-findx9u") echo infinityx ;;
        "s-plus-8850 hyperos-lhasa") echo hyper-f8u ;;
        "s-8850 infinityx41-findx9u") echo infinityx ;;
        "s-8850 hyperos-lhasa") echo hyper-f8u ;;
        "s-8850-jp infinityx41-findx9u") echo infinityx ;;
        "s-8850-jp hyperos-lhasa") echo hyper-f8u ;;
        "s-plus-9965 infinityx41-findx9u") echo infinityx ;;
        "s-plus-9965 hyperos-lhasa") echo hyper-f8u ;;
        "s-ultra-8750 infinityx41-findx9u") echo infinityx ;;
        "s-ultra-8750 hyperos-lhasa") echo hyper-f8u ;;
        "s-plus-8750 infinityx41-findx9u") echo infinityx ;;
        "s-plus-8750 hyperos-lhasa") echo hyper-f8u ;;
        "s-8750 infinityx41-findx9u") echo infinityx ;;
        "s-8750 hyperos-lhasa") echo hyper-f8u ;;
        "s-ultra-8650 infinityx41-findx9u") echo infinityx ;;
        "s-ultra-8650 hyperos-lhasa") echo hyper-f8u ;;
        "s-8550 infinityx41-findx9u") echo infinityx ;;
        "s-8550 hyperos-lhasa") echo hyper-f8u ;;
        "s-plus-8550 infinityx41-findx9u") echo infinityx ;;
        "s-plus-8550 hyperos-lhasa") echo hyper-f8u ;;
        "flip7 infinityx41-findx9u") echo infinityx ;;
        "flip7 hyperos-lhasa") echo hyper-f8u ;;
        "a56 infinityx41-findx9u") echo infinityx ;;
        "a57 infinityx41-findx9u") echo infinityx ;;
        "a56 hyperos-lhasa") echo hyper-f8u ;;
        "a57 hyperos-lhasa") echo hyper-f8u ;;
        "flip5 infinityx41-findx9u") echo infinityx ;;
        "flip5 hyperos-lhasa") echo hyper-f8u ;;
        "f6 coloros17-findn6") echo n6-f8u ;;
        "f6 infinityx41-findx9u") echo infinityx ;;
        "a07 infinityx41-findx9u") echo infinityx ;;
        *) return 1 ;;
    esac
}

# <image id>: <unpacked bytes> <unpacked SHA-256>, as staged in system.raw.
# Under the fake test root (only) ONEOS_TEST_RAW stands in for a 10 GB image.
image_raw() {
    if [ -n "$T" ] && [ -n "${ONEOS_TEST_RAW:-}" ]; then
        echo "$ONEOS_TEST_RAW"
        return 0
    fi
    case "$1" in
        coloros17-findn6) echo "10276003840 bdf5ef00aee3333758afdde152a29e0d2ca7bdaf89029ccd74bad34eec5bf444" ;;
        coloros17-findx9u) echo "10385141760 302467fc4e77998e84c10e037326508a39ab9e4be34058b751f0a768c7ff23e7" ;;
        coloros17-findx9u-in) echo "12069945344 a36ef550f4b2530fb9c7c6d363180d9eb4437e0497ebc87292697e4bf2a580b0" ;;
        pixel11fold-yogi) echo "6559223808 8b303c37916288545b3b4a317c368d89680b5c3ce09873dc764381ddd0e32830" ;;
        pixel11pro-kodiak) echo "6529871872 ac661a520263fa73a2b9996f19763350457b1558863d1dc15326a365ce1784c6" ;;
        hyperos-lhasa) echo "7749939200 269309b8869d4de41c5c852d230b4b7075b8ff733d9244f4fb868ec7109baeac" ;;
        pixel9proxl-komodo) echo "5837496320 2ef2dfe164dc6cac44c574696da474e71a01d9a769eda9c1a265d018c1d45a5c" ;;
        infinityx41-findx9u) echo "6677426176 899ce57a069959b6463263074f35e5aadadeb7bb46f0c0a756d2ba64168545e2" ;;
        *) return 1 ;;
    esac
}

set_files() {
    case "$1" in
        n6-f8u) echo "$SET_COMMON OneOSWallpapersFoldGrid.apk" ;;
        n6-f8) echo "$SET_COMMON OneOSWallpapersFoldGrid.apk Fold8SystemUI.apk Fold8AngleRelay.dex Fold8Aod.apk Fold8UMS.apk Fold8Instant.apk" ;;
        x9u-sultra) echo "$SET_COMMON" ;;
        x9u-in-sultra) echo "$SET_COMMON cellbroadcast_payload.img" ;;
        pixel-f8u) echo "$SET_PIXEL Fold8PixelSystemUI.apk Fold8AngleRelay.dex OneOSPixelUnfold.apk" ;;
        pixel-f8) echo "$SET_PIXEL Fold8PixelSystemUI.apk Fold8AngleRelay.dex Fold8PixelUnfold.apk" ;;
        pixel-sultra) echo "$SET_PIXEL" ;;
        hyper-f8u) echo "$SET_HYPEROS" ;;
        pixel9-sultra) echo "$SET_PIXEL" ;;
        infinityx) echo "$SET_INFINITYX" ;;
        *) return 1 ;;
    esac
}

# <set>: the image it was built from (scripts/make-overlays.sh, PackageSet.imageId).
set_image() {
    case "$1" in
        n6-f8u|n6-f8) echo coloros17-findn6 ;;
        x9u-sultra) echo coloros17-findx9u ;;
        x9u-in-sultra) echo coloros17-findx9u-in ;;
        pixel-f8u|pixel-f8) echo pixel11fold-yogi ;;
        pixel-sultra) echo pixel11pro-kodiak ;;
        hyper-f8u) echo hyperos-lhasa ;;
        pixel9-sultra) echo pixel9proxl-komodo ;;
        infinityx) echo infinityx41-findx9u ;;
        *) return 1 ;;
    esac
}

# <image id>: the system it is a build of (ImageRef.family, 0.3.7). Guest data
# is kept only for an image of its own system: the packages in its
# packages.xml tell ColorOS data (com.oplus., com.coloros.) from Pixel data
# (Pixel's launcher, com.google.android.apps.nexuslauncher), Infinity-X data
# (its updater, com.infinity.updater; it has OPPO packages too) and HyperOS data
# (com.miui.) (data_family).
image_family() {
    case "$1" in
        coloros17-findn6|coloros17-findx9u|coloros17-findx9u-in) echo coloros ;;
        pixel11fold-yogi|pixel11pro-kodiak|pixel9proxl-komodo) echo pixel ;;
        infinityx41-findx9u) echo infinityx ;;
        hyperos-lhasa) echo hyperos ;;
        *) return 1 ;;
    esac
}

# The system whose guest data the stage holds (StageStatus.guest, the same
# words in the same order as status.list); nothing when no package tells.
data_family() {
    pk="$STAGE/data/system/packages.xml"
    if grep -q -s -F "com.infinity.updater" "$pk"; then
        echo infinityx
    elif grep -q -s -F "com.oplus." "$pk" || grep -q -s -F "com.coloros." "$pk"; then
        echo coloros
    elif grep -q -s -F "com.google.android.apps.nexuslauncher" "$pk"; then
        echo pixel
    elif grep -q -s -F "com.miui." "$pk"; then
        echo hyperos
    fi
}

# The set of <image> on <profile>, only when both tables agree (fail closed):
# profile_set names it, and set_image says it was built from that image.
pair_set() { # <profile> <image>
    ps=$(profile_set "$1" "$2") || return 1
    [ "$(set_image "$ps")" = "$2" ] || return 1
    echo "$ps"
}

# Executables 0755 (as scripts/deploy-phone.sh pushes them; PackageFile.mode).
file_mode() {
    case "$1" in
        *.sh|zygote_exec|oneos_btaudio|surfaceflinger_patched|vold_patched) echo 0755 ;;
        *) echo 0644 ;;
    esac
}

# --- Helpers ---------------------------------------------------------------
say() { printf '%s\n' "$*"; }
refuse() { say "@@REFUSED $*"; exit 2; }
die() { say "@@FAILED $*"; exit 4; }
usage() { say "@@REFUSED bad arguments: $*"; exit 3; }
step() { say "@@STEP $*"; }

is_dec() {
    case "$1" in ''|*[!0-9]*) return 1 ;; esac
    [ ${#1} -le 18 ]
}
is_hex64() {
    case "$1" in *[!0-9a-f]*) return 1 ;; esac
    [ ${#1} -eq 64 ]
}
is_id() {
    case "$1" in ''|-*|*[!a-z0-9-]*) return 1 ;; esac
    [ ${#1} -le 32 ]
}
in_list() {
    for x in $2; do [ "$x" = "$1" ] && return 0; done
    return 1
}
sha() { sha256sum "$1" 2>/dev/null | cut -d' ' -f1; }
# Owned by root, not a link, writable by root only (stat %A: group w is the
# 6th character, other w the 9th).
mine() {
    [ -L "$1" ] && return 1
    [ -e "$1" ] || return 1
    case "$(stat -c '%u %A' "$1" 2>/dev/null)" in
        "$ROOT_UID "?????w*|"$ROOT_UID "????????w*) return 1 ;;
        "$ROOT_UID "*) return 0 ;;
    esac
    return 1
}
# a >= b for byte counts: awk, because mksh (Android's sh) has 32-bit arithmetic.
ge() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 >= b + 0) }'; }
own() { chown "$ROOT_OWNER" "$1" && chmod "$2" "$1"; }
mkdir_mine() {
    if [ -e "$1" ] || [ -L "$1" ]; then
        [ -d "$1" ] && mine "$1" && return 0
        mv "$1" "$1.old-$(date +%s)-$$" || return 1
    fi
    mkdir "$1" && own "$1" 0755
}
# Moves stage entries into $STAGE/attic/<epoch>/ (never deletes them).
ATTIC=""
to_attic() {
    for a in "$@"; do
        [ -e "$STAGE/$a" ] || [ -L "$STAGE/$a" ] || continue
        if [ -z "$ATTIC" ]; then
            mkdir_mine "$STAGE/attic" || die "could not make $STAGE/attic"
            ATTIC="$STAGE/attic/$(date +%s)"
            [ -e "$ATTIC" ] && ATTIC="$ATTIC-$$"
            mkdir_mine "$ATTIC" || die "could not make $ATTIC"
        fi
        mv "$STAGE/$a" "$ATTIC/" || die "could not move $a to the attic"
        say "@@INFO moved $a to ${ATTIC#$STAGE/}"
    done
}

# --- Is the stage idle? (init's mount namespace and this one) ---------------
mountinfo_ok() {
    for m in "$PROC/1/mountinfo" "$PROC/self/mountinfo"; do
        awk '$5 == "/" { f = 1 } END { exit !f }' "$m" 2>/dev/null || refuse "could not read $m"
    done
}
mounted_under() {
    awk -v p="$1" '$5 == p || index($5, p "/") == 1 { print $5 }' "$PROC/1/mountinfo" "$PROC/self/mountinfo" 2>/dev/null | sort -u
}
# Init's table only: the switch makes its bridges in init's namespace, and this
# script runs in the app's, where Android's app data isolation mounts tmpfs on
# /data/data, /data/user and /data/user_de (0.3.0 on the Fold: "still bridged"
# right after a clean reboot).
bridged() {
    for h in $BRIDGE_HOSTS; do
        awk -v p="$DATA/$h" '$5 == p { print $5 }' "$PROC/1/mountinfo" 2>/dev/null
    done | sort -u
}
raw_attached() {
    for b in "$SYSFS"/block/loop*/loop/backing_file; do
        [ -r "$b" ] && cat "$b" 2>/dev/null
    done | grep -q -F "$STAGE/system.raw"
}
idle_or_refuse() {
    mountinfo_ok
    m=$(mounted_under "$STAGE" | head -n 3 | tr '\n' ' ')
    [ -z "$m" ] || refuse "mounted in the stage: $m- restart the phone first"
    b=$(bridged | head -n 3 | tr '\n' ' ')
    [ -z "$b" ] || refuse "still bridged from a session: $b- restart the phone first"
    if raw_attached; then refuse "system.raw is attached to a loop device - restart the phone first"; fi
}
boot_id() { cat "$PROC/sys/kernel/random/boot_id" 2>/dev/null; }

# --- The marker: exactly the text StageMarker.toJson() writes --------------
# oneos.stage/2 (0.3.3) names the file set too. A oneos.stage/1 marker
# (0.3.0-0.3.2, M_SET empty) is still read, so an older stage stays OneOS's
# for keep and recover; launch refuses it.
marker_text() { # <versionCode> <profile> <image> <set> <created>
    printf '{\n  "schema": "oneos.stage/2",\n  "versionCode": %s,\n  "profile": "%s",\n  "image": "%s",\n  "set": "%s",\n  "created": %s\n}\n' "$1" "$2" "$3" "$4" "$5"
}
marker_text_v1() { # <versionCode> <profile> <image> <created>
    printf '{\n  "schema": "oneos.stage/1",\n  "versionCode": %s,\n  "profile": "%s",\n  "image": "%s",\n  "created": %s\n}\n' "$1" "$2" "$3" "$4"
}
read_marker() {
    mk="$STAGE/.oneos_stage"
    [ -f "$mk" ] && mine "$mk" || return 1
    M_CODE=$(sed -n 's/^  "versionCode": \([0-9][0-9]*\),$/\1/p' "$mk")
    M_PROFILE=$(sed -n 's/^  "profile": "\([a-z0-9-]*\)",$/\1/p' "$mk")
    M_IMAGE=$(sed -n 's/^  "image": "\([a-z0-9-]*\)",$/\1/p' "$mk")
    M_SET=$(sed -n 's/^  "set": "\([a-z0-9-]*\)",$/\1/p' "$mk")
    M_CREATED=$(sed -n 's/^  "created": \([0-9][0-9]*\)$/\1/p' "$mk")
    is_dec "$M_CODE" && is_id "$M_PROFILE" && is_id "$M_IMAGE" && is_dec "$M_CREATED" || return 1
    have=$(sha256sum < "$mk" | cut -d' ' -f1)
    if [ -n "$M_SET" ]; then
        is_id "$M_SET" && [ "$(marker_text "$M_CODE" "$M_PROFILE" "$M_IMAGE" "$M_SET" "$M_CREATED" | sha256sum | cut -d' ' -f1)" = "$have" ]
    else
        [ "$(marker_text_v1 "$M_CODE" "$M_PROFILE" "$M_IMAGE" "$M_CREATED" | sha256sum | cut -d' ' -f1)" = "$have" ]
    fi
}

# --- The plan of the preparation in progress (begin writes it) --------------
PLAN="$STAGE/incoming/plan"
RECEIVED="$STAGE/incoming/received"
need_plan() {
    [ -f "$PLAN" ] && mine "$PLAN" || refuse "no preparation in progress (begin first)"
    read -r P_PROFILE P_IMAGE P_SET P_CODE P_EPOCH < "$PLAN"
    pinfo=$(profile_info "$P_PROFILE") || refuse "the plan names an unknown profile"
    set -- $pinfo
    is_id "$P_SET" && [ "$(pair_set "$P_PROFILE" "$P_IMAGE")" = "$P_SET" ] && is_dec "$P_CODE" && is_dec "$P_EPOCH" ||
        refuse "the plan is not well formed"
    P_FORM=$4
    P_FILES="$SCRIPTS $(set_files "$P_SET")"
    set -- $(image_raw "$P_IMAGE")
    P_RAW_BYTES=$1
    P_RAW_SHA=$2
}
received_sum() { [ -f "$RECEIVED" ] && awk -v n="$1" 'NF == 2 && $2 == n { s = $1 } END { if (s != "") print s }' "$RECEIVED"; }
record_received() {
    { [ -f "$RECEIVED" ] && awk -v n="$2" '$2 != n' "$RECEIVED"; echo "$1  $2"; } > "$RECEIVED.tmp" &&
        own "$RECEIVED.tmp" 0644 && mv -f "$RECEIVED.tmp" "$RECEIVED" || die "could not record $2"
}
write_raw_id() {
    id_stat=$(stat -c '%d:%i:%s:%Y' "$STAGE/system.raw") || die "could not stat system.raw"
    echo "$1 $2 $id_stat" > "$STAGE/system.raw.id.tmp" && own "$STAGE/system.raw.id.tmp" 0644 &&
        mv -f "$STAGE/system.raw.id.tmp" "$STAGE/system.raw.id" || die "could not write system.raw.id"
}

# --- Checks shared by table/env/finish and launch ---------------------------
# session.env: every allowed key once, each with exactly the profile's value;
# ONEOS_GUEST_HIDE_GMS 0, or 1 when the image is in IMAGES_GMS;
# ONEOS_HEAT_CAP 1 (Prepare) or 0 (a launch with Cool down when hot off).
env_value() {
    case "$1" in
        ONEOS_APP|ONEOS_LOGCAT) echo 1 ;;
        ONEOS_PROFILE) echo "$3" ;;
        ONEOS_SPLASH_COMPONENT) echo "$SPLASH_COMPONENT" ;;
        ONEOS_WALLPAPER_PROBE|ONEOS_RECORD) echo 0 ;;
        ONEOS_GUEST_FORM) if [ "$2" = fold ]; then echo fold; else echo phone; fi ;;
        ONEOS_GUEST_FOLD) if [ "$2" = fold ]; then echo layout; else echo off; fi ;;
        ONEOS_COVER_TSP|ONEOS_GUEST_FOLD_CONTINUE|ONEOS_GUEST_UNFOLD_PORTRAIT|ONEOS_GUEST_COMPACT_WINDOW|ONEOS_GUEST_WALLPAPER_GRID)
            if [ "$2" = fold ]; then echo 1; else echo 0; fi ;;
        ONEOS_GUEST_HIDE_GMS) echo 0 ;;
        ONEOS_HEAT_CAP) echo 1 ;;
        # 0.4.20: this phone's Samsung IMS app in the guest (VoLTE), a launch's choice, 0 or 1 on any phone.
        ONEOS_GUEST_SEC_IMS) echo 0 ;;
        # 0.4.32: the Knox edmnativehelper stand-in, a launch's choice, 0 or 1 on any phone.
        ONEOS_GUEST_EDM_STUB) echo 0 ;;
        # 0.4.7: Back to One UI restarts One UI in place; a failed check reboots.
        ONEOS_RETURN_MODE) echo inplace ;;
        *) return 1 ;;
    esac
}
check_env() { # <file> <form> <profile> <image>
    f=$1
    [ -f "$f" ] || refuse "session.env missing"
    ge 4096 "$(stat -c %s "$f")" || refuse "session.env is over 4096 bytes"
    [ "$(( $(tail -c 1 "$f" | wc -l) + 0 ))" -eq 1 ] || refuse "session.env does not end with a newline"
    seen=" "
    while IFS= read -r line; do
        case "$line" in
            *[!A-Za-z0-9_=./-]*|'') refuse "session.env line '$line' has characters that are not allowed" ;;
        esac
        key=${line%%=*}
        val=${line#*=}
        [ "$key" != "$line" ] || refuse "session.env line '$line' is not KEY=VALUE"
        case "$seen" in *" $key "*) refuse "session.env sets $key twice" ;; esac
        seen="$seen$key "
        want=$(env_value "$key" "$2" "$3") || refuse "session.env key $key is not allowed"
        if [ "$key" = ONEOS_GUEST_SEC_IMS ] || [ "$key" = ONEOS_GUEST_EDM_STUB ]; then
            case "$val" in 0|1) continue ;; *) refuse "session.env $key=$val is not 0 or 1" ;; esac
        fi
        if [ "$key" = ONEOS_GUEST_HIDE_GMS ] && [ "$val" = 1 ]; then
            in_list "$4" "$IMAGES_GMS" || refuse "session.env hides Google services, but $4 has none a session may hide"
            continue
        fi
        [ "$key" = ONEOS_HEAT_CAP ] && [ "$val" = 0 ] && continue
        [ "$val" = "$want" ] || refuse "session.env $key=$val (profile $3: $want)"
    done < "$f"
    for k in $ENV_KEYS; do
        case "$seen" in *" $k "*) ;; *) refuse "session.env lacks $k" ;; esac
    done
}
# host_installd.table (InstalldTableFormat 1): this phone's fingerprint, the
# source jar's SHA-256, a valid trailer, and methods equal to the staged
# host_installd.txt, whose method lines hash to INSTALLD_SHA.
check_table() { # <file>
    f=$1
    [ -f "$f" ] || refuse "host_installd.table missing"
    ge 65536 "$(stat -c %s "$f")" || refuse "host_installd.table is over 64 kB"
    [ "$(grep -v '^#' "$STAGE/host_installd.txt" 2>/dev/null | sha256sum | cut -d' ' -f1)" = "$INSTALLD_SHA" ] ||
        refuse "host_installd.txt in the stage is not the table the OneOS jars carry"
    [ "$(sed -n 1p "$f")" = "oneos.installd-table 1" ] || refuse "host_installd.table: bad first line"
    fp=$(getprop ro.build.fingerprint)
    [ -n "$fp" ] && [ "$(sed -n 2p "$f")" = "fingerprint $fp" ] || refuse "host_installd.table is not for this One UI build ($fp)"
    set -- $(sed -n 3p "$f")
    [ $# -eq 3 ] && [ "$1" = source ] && is_hex64 "$3" || refuse "host_installd.table: bad source line"
    case "$2" in
        /system/*|/system_ext/*|/product/*|/apex/*) ;;
        *) refuse "host_installd.table: source $2 is not a system jar" ;;
    esac
    case "$2" in *..*) refuse "host_installd.table: source $2" ;; esac
    [ "$(sha "$T$2")" = "$3" ] || refuse "host_installd.table: $2 is not the jar the table was derived from (One UI updated?)"
    n=$(sed -n 's/^methods \([0-9][0-9]*\)$/\1/p' "$f" | head -n 1)
    is_dec "$n" && [ "$n" -ge 1 ] && [ "$n" -le 512 ] || refuse "host_installd.table: bad methods line"
    [ "$(sed -n 4p "$f")" = "methods $n" ] || refuse "host_installd.table: bad methods line"
    lines=$(wc -l < "$f")
    [ "$((lines + 0))" -eq $((n + 5)) ] || refuse "host_installd.table: expected $n methods and a trailer"
    [ "$(sed -n "$((n + 5))p" "$f")" = "sha256 $(sed '$d' "$f" | sha256sum | cut -d' ' -f1)" ] ||
        refuse "host_installd.table: the sha256 trailer does not match"
    [ "$(sed -n "5,$((n + 4))p" "$f" | sha256sum | cut -d' ' -f1)" = "$INSTALLD_SHA" ] ||
        refuse "this phone's installd table differs from the one the OneOS jars carry"
}
# Every staged file of the profile, root's and matching files.sha256; no file
# the session scripts would bind or run that the list does not name.
check_files() { # <names>
    s="$STAGE/files.sha256"
    [ -f "$s" ] && mine "$s" || refuse "files.sha256 is missing or not root's"
    want=0
    for n in $1; do
        want=$((want + 1))
        sum=$(awk -v n="$n" 'NF == 2 && $2 == n { print $1 }' "$s")
        is_hex64 "$sum" || refuse "files.sha256 does not list $n once"
        [ -f "$STAGE/$n" ] && mine "$STAGE/$n" || refuse "$n is missing, a link, or not root's"
        [ "$(sha "$STAGE/$n")" = "$sum" ] || refuse "$n changed since it was prepared"
    done
    [ "$(grep -c . "$s")" -eq "$want" ] || refuse "files.sha256 lists other files"
    for f in "$STAGE"/* "$STAGE"/.[!.]*; do
        [ -e "$f" ] || [ -L "$f" ] || continue
        [ -d "$f" ] && [ ! -L "$f" ] && continue
        n=${f##*/}
        case "$n" in
            *_patched*|*_payload.img|*.apk|*.jar|*.so|*.sh|Fold8AngleRelay.dex|zygote_exec|oneos_btaudio|socket_exec|clean_apex_info_list.xml|oneos-*)
                in_list "$n" "$1" || refuse "$n is in the stage but was not prepared (left from an older stage)" ;;
        esac
    done
}
check_raw() { # <bytes> <sha256>
    rid="$STAGE/system.raw.id"
    [ -f "$STAGE/system.raw" ] && mine "$STAGE/system.raw" || refuse "system.raw is missing, a link, or not root's"
    [ -f "$rid" ] && mine "$rid" || refuse "system.raw.id is missing or not root's"
    set -- $1 $2 $(cat "$rid")
    [ $# -eq 5 ] && [ "$3" = "$1" ] && [ "$4" = "$2" ] || refuse "system.raw.id is not this profile's image"
    [ "$(stat -c '%d:%i:%s:%Y' "$STAGE/system.raw")" = "$5" ] || refuse "system.raw changed since it was verified"
}

# --- Verbs --------------------------------------------------------------------
do_begin() {
    [ $# -eq 5 ] || usage "begin <new|keep|aside> <profile> <image> <set> <versionCode>"
    mode=$1
    case "$mode" in new|keep|aside) ;; *) usage "begin mode $mode" ;; esac
    is_id "$2" && is_id "$3" && is_id "$4" && is_dec "$5" || usage "begin $*"
    profile_info "$2" > /dev/null || refuse "profile $2 is not one this build prepares"
    pset=$(pair_set "$2" "$3") || refuse "profile $2 does not run $3"
    [ "$4" = "$pset" ] || refuse "$4 is not the file set of $3 on $2 ($pset)"
    B_PROFILE=$2 B_IMAGE=$3 B_SET=$4 B_CODE=$5
    idle_or_refuse
    ours=0
    read_marker && ours=1
    E=$(date +%s)
    entries=$(ls -A "$STAGE" 2>/dev/null | grep -v -x -e bin -e incoming -e attic | head -n 1)
    # OneOS's own unfinished session: its snapshot is the only way back to
    # One UI's persist props, so it is restored (recover) before anything else.
    if [ "$ours" = 1 ] && [ -f "$STAGE/persist_session.active" ]; then
        if [ "$(sed -n 's/^boot_id=//p' "$STAGE/persist_session.active" | head -n 1)" = "$(boot_id)" ]; then
            refuse "a session of this stage is still running"
        fi
        refuse "the last session never returned: restore One UI's settings first (Last session)"
    fi
    case "$mode" in
        new)
            [ -z "$entries" ] || refuse "the stage is not empty (keep it or move it aside)"
            ;;
        keep)
            # Guest data stays with the image that made it (0.3.3: a phone may
            # take more than one image, and another build could change or
            # break it): the marker's image, or the plan's for a preparation
            # that did not finish (keep never crosses images, so it is the same).
            if [ -e "$STAGE/data" ] || [ -L "$STAGE/data" ]; then
                made=""
                if [ "$ours" = 1 ]; then
                    made=$M_IMAGE
                elif [ -f "$PLAN" ] && mine "$PLAN"; then
                    { read -r pp made pr < "$PLAN" && is_id "$made"; } || made="another image"
                fi
                [ -z "$made" ] || [ "$made" = "$B_IMAGE" ] ||
                    refuse "the stage's guest data is from $made, not $B_IMAGE: move the stage aside"
                # And never another system's data (0.3.7): a stage another tool
                # made (no marker, no plan), or one whose marker names an image
                # its data is not from (scripts/deploy-phone.sh without --fresh).
                dfam=$(data_family)
                ifam=$(image_family "$B_IMAGE") || refuse "$B_IMAGE has no system family"
                [ -z "$dfam" ] || [ "$dfam" = "$ifam" ] ||
                    refuse "the stage holds another system's guest data ($dfam), not $B_IMAGE's: move the stage aside"
            fi
            if [ "$ours" = 0 ]; then
                if grep -q -s -F "org.lineageos." "$STAGE/data/system/packages.xml"; then
                    refuse "the stage holds LineageOS data, not $B_IMAGE's: move it aside"
                fi
                # Never trust another stage's persist snapshot (it could
                # delete One UI's props on restore).
                to_attic persist_session.active persist_props.snapshot
            fi
            ;;
        aside)
            self_sum=$(sha "$SELF")
            OLD="$STAGE.old-$E"
            [ -e "$OLD" ] && OLD="$OLD-$$"
            step "aside $OLD"
            mv "$STAGE" "$OLD" || die "could not move the stage aside"
            mkdir "$STAGE" && own "$STAGE" 0755 && mkdir "$STAGE/bin" && own "$STAGE/bin" 0755 || die "could not make a new stage"
            cp "$OLD/bin/oneos_root.sh" "$STAGE/bin/.oneos_root.sh.new" && own "$STAGE/bin/.oneos_root.sh.new" 0755 &&
                [ "$(sha "$STAGE/bin/.oneos_root.sh.new")" = "$self_sum" ] &&
                mv -f "$STAGE/bin/.oneos_root.sh.new" "$STAGE/bin/oneos_root.sh" || die "could not install the root script in the new stage"
            say "@@RESULT aside ${OLD#$T}"
            ;;
    esac
    own "$STAGE" 0755 || die "could not make the stage root's"
    mkdir_mine "$STAGE/incoming" || die "could not make $STAGE/incoming"
    for f in "$STAGE/incoming"/* "$STAGE/incoming"/.[!.]*; do
        [ -e "$f" ] || [ -L "$f" ] || continue
        if [ -f "$f" ] || [ -L "$f" ]; then rm -f "$f"; else to_attic "incoming/${f##*/}"; fi
    done
    # The last preparation's records: the new one writes its own, and a
    # half-prepared stage has none (the app and the scripts then refuse).
    to_attic .oneos_stage files.sha256 system.raw.id session.env host_installd.table
    echo "$B_PROFILE $B_IMAGE $B_SET $B_CODE $E" > "$PLAN" && own "$PLAN" 0644 || die "could not write the plan"
    say "@@OK begin $mode"
}

do_receive() {
    [ $# -eq 3 ] || usage "receive <name> <bytes> <sha256>"
    name=$1 bytes=$2 sum=$3
    is_dec "$bytes" && is_hex64 "$sum" || usage "receive $*"
    need_plan
    if [ "$name" = system.raw ]; then
        [ "$bytes" = "$P_RAW_BYTES" ] && [ "$sum" = "$P_RAW_SHA" ] || refuse "system.raw: not the $P_IMAGE image"
        mode=0644
    else
        in_list "$name" "$P_FILES" || refuse "$name is not a file of $P_SET"
        ge 268435456 "$bytes" || refuse "$name is too large"
        mode=$(file_mode "$name")
    fi
    idle_or_refuse
    dst="$STAGE/$name"
    if [ -L "$dst" ] || { [ -e "$dst" ] && [ ! -f "$dst" ]; }; then to_attic "$name"; fi
    if [ "$name" = system.raw ] && [ -f "$dst" ]; then
        # The old image goes first, so two copies never need room.
        rm -f "$dst" || die "could not remove the old system.raw"
        say "@@INFO removed the old system.raw"
    fi
    set -- $(stat -f -c '%a %S' "$STAGE" 2>/dev/null)
    [ $# -eq 2 ] && ge "$(awk -v a="$1" -v s="$2" 'BEGIN { printf "%.0f", a * s }')" "$(awk -v b="$bytes" 'BEGIN { printf "%.0f", b + 1073741824 }')" ||
        refuse "not enough free space for $name"
    part="$STAGE/incoming/$name.part"
    rm -f "$part"
    step "receive $name $bytes"
    head -c "$bytes" > "$part" || { rm -f "$part"; die "could not write $name"; }
    got=$(stat -c %s "$part")
    [ "$got" = "$bytes" ] || { rm -f "$part"; refuse "$name arrived with $got of $bytes bytes"; }
    step "verify $name"
    [ "$(sha "$part")" = "$sum" ] || { rm -f "$part"; refuse "$name does not match its SHA-256"; }
    own "$part" "$mode" && mv -f "$part" "$dst" || die "could not put $name in place"
    [ "$name" = system.raw ] && write_raw_id "$bytes" "$sum"
    record_received "$sum" "$name"
    say "@@OK receive $name"
}

do_keep_raw() {
    [ $# -eq 2 ] || usage "keep-raw <bytes> <sha256>"
    is_dec "$1" && is_hex64 "$2" || usage "keep-raw $*"
    need_plan
    [ "$1" = "$P_RAW_BYTES" ] && [ "$2" = "$P_RAW_SHA" ] || refuse "not the $P_IMAGE image"
    idle_or_refuse
    f="$STAGE/system.raw"
    [ -f "$f" ] && [ ! -L "$f" ] || refuse "there is no system.raw on this phone"
    got=$(stat -c %s "$f")
    if [ "$got" != "$1" ]; then
        to_attic system.raw
        refuse "system.raw has $got bytes, not the $P_IMAGE image: moved to the attic; pick the .gz"
    fi
    step "verify system.raw"
    if [ "$(sha "$f")" != "$2" ]; then
        to_attic system.raw
        refuse "system.raw is not the $P_IMAGE image (SHA-256 differs): moved to the attic; pick the .gz"
    fi
    own "$f" 0644 || die "could not make system.raw root's"
    write_raw_id "$1" "$2"
    record_received "$2" system.raw
    say "@@OK keep-raw"
}

# table and env: stdin is a small text file, checked before it is put in place.
receive_text() { # <name> <bytes> <max>
    is_dec "$2" && ge "$3" "$2" || usage "$1 size $2"
    need_plan
    part="$STAGE/incoming/$1.part"
    rm -f "$part"
    head -c "$2" > "$part" || { rm -f "$part"; die "could not write $1"; }
    [ "$(stat -c %s "$part")" = "$2" ] || { rm -f "$part"; refuse "$1 arrived incomplete"; }
    own "$part" 0644 || die "could not make $1 root's"
}

do_table() {
    [ $# -eq 1 ] || usage "table <bytes>"
    receive_text host_installd.table "$1" 65536
    [ -n "$(received_sum host_installd.txt)" ] || { rm -f "$part"; refuse "host_installd.txt was not received"; }
    ( check_table "$part" ) || { rm -f "$part"; exit 2; }
    mv -f "$part" "$STAGE/host_installd.table" || die "could not put host_installd.table in place"
    say "@@OK table"
}

do_env() {
    [ $# -eq 1 ] || usage "env <bytes>"
    receive_text session.env "$1" 4096
    ( check_env "$part" "$P_FORM" "$P_PROFILE" "$P_IMAGE" ) || { rm -f "$part"; exit 2; }
    mv -f "$part" "$STAGE/session.env" || die "could not put session.env in place"
    say "@@OK env"
}

do_finish() {
    [ $# -eq 4 ] || usage "finish <profile> <image> <set> <versionCode>"
    need_plan
    [ "$1" = "$P_PROFILE" ] && [ "$2" = "$P_IMAGE" ] && [ "$3" = "$P_SET" ] && [ "$4" = "$P_CODE" ] || refuse "finish does not match the plan"
    idle_or_refuse
    step "files"
    list=""
    for n in $P_FILES; do
        sum=$(received_sum "$n")
        is_hex64 "$sum" || refuse "$n was not received"
        [ -f "$STAGE/$n" ] && mine "$STAGE/$n" && [ "$(sha "$STAGE/$n")" = "$sum" ] || refuse "$n changed after it was received"
        list="$list$sum  $n
"
    done
    [ "$(received_sum system.raw)" = "$P_RAW_SHA" ] || refuse "the image was not received or checked"
    check_raw "$P_RAW_BYTES" "$P_RAW_SHA"
    check_env "$STAGE/session.env" "$P_FORM" "$P_PROFILE" "$P_IMAGE"
    check_table "$STAGE/host_installd.table"
    printf '%s' "$list" | LC_ALL=C sort -k2 > "$STAGE/files.sha256.tmp" && own "$STAGE/files.sha256.tmp" 0644 &&
        mv -f "$STAGE/files.sha256.tmp" "$STAGE/files.sha256" || die "could not write files.sha256"
    # Every other flat file that is not the sessions' own state goes to the
    # attic, so the scripts can never bind a leftover (framework_patched.jar,
    # an old payload or RRO, OneLineage's 7zz).
    step "attic"
    for f in "$STAGE"/* "$STAGE"/.[!.]*; do
        [ -e "$f" ] || [ -L "$f" ] || continue
        [ -d "$f" ] && [ ! -L "$f" ] && continue
        n=${f##*/}
        in_list "$n" "$P_FILES system.raw system.raw.id files.sha256 session.env host_installd.table" && continue
        case "$n" in
            *.log|persist_props.snapshot|persist_session.active|guest_locale|guest_props.added|guest_props.overridden|guest_props.boot|guest_props.blanked|guest_props.blanked.boot|\
            host_wifi_on|host_adb_on|host_mobile_data|host_supplicant_svc|host_apex.before|host_apex.after|guest_mntns|guest_rootdev) continue ;;
        esac
        to_attic "$n"
    done
    check_files "$P_FILES"
    marker_text "$P_CODE" "$P_PROFILE" "$P_IMAGE" "$P_SET" "$(date +%s)" > "$STAGE/.oneos_stage.tmp" && own "$STAGE/.oneos_stage.tmp" 0644 &&
        mv -f "$STAGE/.oneos_stage.tmp" "$STAGE/.oneos_stage" || die "could not write the marker"
    rm -f "$PLAN" "$RECEIVED"
    say "@@OK finish"
}

# The root manager's own busybox and resetprop, never a search of /data.
find_tools() {
    BB=""
    for c in "$ADB/ksu/bin/busybox" "$ADB/ap/bin/busybox" "$ADB/magisk/busybox"; do
        [ -x "$c" ] && { BB=$c; break; }
    done
    RP=""
    for c in "$ADB/ksu/bin/resetprop" "$ADB/ap/bin/resetprop"; do
        [ -x "$c" ] && { RP=$c; break; }
    done
}

do_launch() {
    [ $# -eq 7 ] || usage "launch <session|dry> <versionCode> <sessionId> <hideGms> <heatCap> <secIms> <edmStub>"
    mode=$1 code=$2 sid=$3 gms=$4 heat=$5 secims=$6 edmstub=$7
    case "$mode" in session|dry) ;; *) usage "launch mode $mode" ;; esac
    is_dec "$code" || usage "launch versionCode $code"
    case "$sid" in *[!0-9a-f]*|'') usage "launch session id" ;; esac
    [ ${#sid} -eq 16 ] || usage "launch session id"
    case "$gms" in 0|1) ;; *) usage "launch hideGms $gms" ;; esac
    case "$heat" in 0|1) ;; *) usage "launch heatCap $heat" ;; esac
    case "$secims" in 0|1) ;; *) usage "launch secIms $secims" ;; esac
    case "$edmstub" in 0|1) ;; *) usage "launch edmStub $edmstub" ;; esac
    [ -n "$T" ] || [ "$(id -u)" = 0 ] || refuse "not root"
    step "checks"
    read_marker || refuse "the stage has no OneOS marker: prepare first"
    [ "$M_CODE" = "$code" ] || refuse "prepared by app build $M_CODE, this is build $code: prepare again"
    pinfo=$(profile_info "$M_PROFILE") || refuse "the stage is prepared for $M_PROFILE, which this build does not switch"
    # The staged image decides the set: the marker's must be that image's on this phone.
    [ -n "$M_SET" ] && [ "$(pair_set "$M_PROFILE" "$M_IMAGE")" = "$M_SET" ] ||
        refuse "the stage holds $M_IMAGE with ${M_SET:-no file set}, not its file set for $M_PROFILE: prepare again"
    [ "$gms" = 0 ] || in_list "$M_IMAGE" "$IMAGES_GMS" || refuse "$M_IMAGE has no Google services a session may hide"
    set -- $pinfo
    form=$4
    model=$(getprop ro.product.model)
    case "$model" in "$1"*) ;; *) refuse "this phone ($model) is not a $1" ;; esac
    [ "$(getprop ro.product.device)" = "$2" ] || refuse "codename $(getprop ro.product.device), profile $M_PROFILE is $2"
    [ "$(getprop ro.soc.model)" = "$3" ] || refuse "chip $(getprop ro.soc.model), profile $M_PROFILE is $3"
    [ "$(getprop ro.build.version.sdk)" = "$IMAGE_SDK" ] || refuse "Android SDK $(getprop ro.build.version.sdk), the image is $IMAGE_SDK"
    idle_or_refuse
    if [ -f "$STAGE/persist_session.active" ]; then
        refuse "the last session never returned: restore One UI's settings first (Last session)"
    fi
    names="$SCRIPTS $(set_files "$M_SET")"
    check_files "$names"
    set -- $(image_raw "$M_IMAGE")
    check_raw "$1" "$2"
    check_table "$STAGE/host_installd.table"
    check_env "$STAGE/session.env" "$form" "$M_PROFILE" "$M_IMAGE"
    find_tools
    [ -n "$BB" ] || refuse "no busybox at the root manager's paths"
    for a in sh nsenter setsid; do
        "$BB" --list 2>/dev/null | grep -q -x "$a" || refuse "$BB lacks $a"
    done
    [ -n "$RP" ] || refuse "no resetprop at the root manager's paths"
    pm path android 2>/dev/null | grep -q '^package:' || refuse "pm does not answer"
    if pm path "$OLD_SWITCHER" 2>/dev/null | grep -q '^package:'; then
        refuse "the old switcher ($OLD_SWITCHER) is installed: uninstall it"
    fi
    keys=$(getevent -pl 2>/dev/null)
    case "$keys" in *KEY_VOLUMEUP*) ;; *) refuse "no Volume Up key found (getevent)" ;; esac
    case "$keys" in *KEY_VOLUMEDOWN*) ;; *) refuse "no Volume Down key found (getevent)" ;; esac
    bat="$SYSFS/class/power_supply/battery"
    cap=$(cat "$bat/capacity" 2>/dev/null)
    st=$(cat "$bat/status" 2>/dev/null)
    temp=$(cat "$bat/temp" 2>/dev/null)
    is_dec "$cap" && is_dec "${temp#-}" || refuse "battery not readable"
    [ "$cap" -ge 30 ] || [ "$st" = Charging ] || [ "$st" = Full ] || refuse "battery at $cap% and not charging"
    [ "$temp" -lt 420 ] || refuse "battery too warm"
    set -- $(stat -f -c '%a %S' "$DATA" 2>/dev/null)
    [ $# -eq 2 ] && ge "$(awk -v a="$1" -v s="$2" 'BEGIN { printf "%.0f", a * s }')" 11811160064 ||
        refuse "less than 11 GiB free on /data"

    # This launch's Google services choice (the Switch screen; Prepare wrote 0),
    # its Cool down when hot (Advanced; Prepare wrote 1), its Samsung IMS choice
    # and (0.4.32) its Knox edm stub choice (both Advanced; Prepare wrote 0): the
    # four lines of session.env a launch changes, after every check. The next
    # launch writes its own. (Before the test root's stop, so the tests see it.)
    sed -e "s/^ONEOS_GUEST_HIDE_GMS=.*/ONEOS_GUEST_HIDE_GMS=$gms/" -e "s/^ONEOS_HEAT_CAP=.*/ONEOS_HEAT_CAP=$heat/" \
        -e "s/^ONEOS_GUEST_SEC_IMS=.*/ONEOS_GUEST_SEC_IMS=$secims/" \
        -e "s/^ONEOS_GUEST_EDM_STUB=.*/ONEOS_GUEST_EDM_STUB=$edmstub/" "$STAGE/session.env" > "$STAGE/session.env.tmp" &&
        grep -q -x "ONEOS_GUEST_HIDE_GMS=$gms" "$STAGE/session.env.tmp" && grep -q -x "ONEOS_HEAT_CAP=$heat" "$STAGE/session.env.tmp" &&
        grep -q -x "ONEOS_GUEST_SEC_IMS=$secims" "$STAGE/session.env.tmp" &&
        grep -q -x "ONEOS_GUEST_EDM_STUB=$edmstub" "$STAGE/session.env.tmp" &&
        own "$STAGE/session.env.tmp" 0644 &&
        mv -f "$STAGE/session.env.tmp" "$STAGE/session.env" || die "could not write session.env for this launch"
    [ -z "$T" ] || refuse "test root: every launch check passed, nothing was started"

    mkdir_mine "$STAGE/session" || die "could not make $STAGE/session"
    {
        echo "{"
        echo "  \"schema\": \"oneos.session/1\","
        echo "  \"session\": \"$sid\","
        echo "  \"mode\": \"$mode\","
        echo "  \"epoch\": $(date +%s),"
        echo "  \"boot\": \"$(boot_id)\","
        echo "  \"versionCode\": $code,"
        echo "  \"profile\": \"$M_PROFILE\","
        echo "  \"image\": \"$M_IMAGE\","
        echo "  \"set\": \"$M_SET\","
        echo "  \"hideGms\": $gms,"
        echo "  \"secIms\": $secims,"
        echo "  \"edmStub\": $edmstub,"
        echo "  \"files\": \"$(sha "$STAGE/files.sha256")\","
        echo "  \"table\": \"$(sha "$STAGE/host_installd.table")\""
        echo "}"
    } > "$STAGE/session/current.json.tmp" && own "$STAGE/session/current.json.tmp" 0644 &&
        mv -f "$STAGE/session/current.json.tmp" "$STAGE/session/current.json" || die "could not write session/current.json"
    echo "=== OneOS app launch $sid $mode $(date '+%F %T') epoch $(date +%s) profile $M_PROFILE image $M_IMAGE set $M_SET hide-gms $gms sec-ims $secims edm-stub $edmstub ===" >> "$STAGE/takeover_app.log"
    # Out of the app's cgroup: ActivityManager kills an app's whole cgroup when
    # the app dies, and the app dies when One UI's framework stops.
    echo $$ > /sys/fs/cgroup/cgroup.procs 2>/dev/null
    # Only session.env (and, for the dry run, ONEOS_PREPARE_ONLY) set ONEOS_*.
    for v in $(env | sed -n 's/^\(ONEOS_[A-Za-z0-9_]*\)=.*/\1/p'); do unset "$v"; done
    [ "$mode" = dry ] && export ONEOS_PREPARE_ONLY=1
    "$BB" nsenter -t 1 -m -- "$BB" setsid /system/bin/sh -c \
        'set -a; . "$1/session.env"; set +a; exec /system/bin/sh "$1/oneos_switch.sh"' oneos-session "$STAGE" \
        </dev/null >>"$STAGE/takeover_app.log" 2>&1 &
    say "@@OK launch $sid"
}

do_recover() {
    [ $# -eq 0 ] || usage "recover"
    read_marker || refuse "the stage has no OneOS marker"
    [ -f "$STAGE/persist_session.active" ] || { say "@@OK recover nothing to restore"; exit 0; }
    pinfo=$(profile_info "$M_PROFILE") || refuse "unknown profile $M_PROFILE"
    set -- $pinfo
    s="$STAGE/files.sha256"
    [ -f "$s" ] && mine "$s" || refuse "files.sha256 is missing or not root's"
    sum=$(awk 'NF == 2 && $2 == "guest_props.sh" { print $1 }' "$s")
    is_hex64 "$sum" && mine "$STAGE/guest_props.sh" && [ "$(sha "$STAGE/guest_props.sh")" = "$sum" ] ||
        refuse "guest_props.sh is not the prepared one"
    find_tools
    [ -n "$BB" ] || refuse "no busybox at the root manager's paths"
    step "recover"
    ONEOS_APP=1 STAGE="$STAGE" "$BB" nsenter -t 1 -m -- /system/bin/sh "$STAGE/guest_props.sh" recover </dev/null
    rc=$?
    case "$rc" in
        # guest_props.sh recover puts back only the language and OPPO's
        # persist.oplus.* (props clearly the guest's): a Pixel or HyperOS
        # session's other persist props stay in One UI (docs/pixel-guest.md),
        # so say so.
        0) if [ "$(image_family "$M_IMAGE")" != coloros ]; then
               say "@@OK recover restored the language only (other settings the session saved stay)"
           else
               say "@@OK recover restored"
           fi ;;
        3) refuse "the session may still be running (same boot, or the stage is mounted)" ;;
        4) say "@@OK recover untrusted: its snapshot was not this stage's, moved to the attic" ;;
        *) die "guest_props.sh recover exit $rc" ;;
    esac
}

# --- Old stages (0.3.5): what begin aside left behind, listed and deleted ----
# oneos.old-<epoch>[-<pid>] is a stage begin aside (or a foreign stage) moved
# out of the way, with its whole ColorOS install; oneos.trash-<epoch> is one a
# purge renamed and could not finish deleting. Up to 0.3.6 they were
# lineage_stage.old-* and lineage_stage.trash-*: still listed and deleted under
# those names while migrate_legacy cannot rename them. Nothing else in
# /data/local/tmp is listed or touched.
OLD_PARENT="$DATA/local/tmp"
old_name_ok() {
    case "$1" in
        oneos.old-*) on_rest=${1#oneos.old-} ;;
        oneos.trash-*) on_rest=${1#oneos.trash-} ;;
        lineage_stage.old-*) on_rest=${1#lineage_stage.old-} ;;
        lineage_stage.trash-*) on_rest=${1#lineage_stage.trash-} ;;
        *) return 1 ;;
    esac
    on_epoch=${on_rest%%-*}
    on_pid=${on_rest#"$on_epoch"}
    on_pid=${on_pid#-}
    is_dec "$on_epoch" && [ ${#on_epoch} -le 12 ] || return 1
    [ "$on_rest" = "$on_epoch" ] || { is_dec "$on_pid" && [ ${#on_pid} -le 10 ]; }
}
# A mount at or under $1 in any mount namespace this root can read: init's,
# this process's and every other process's (a guest namespace that is still
# around). A table that exists but cannot be read counts as mounted.
mounted_anywhere() {
    for m in "$PROC"/[0-9]*/mountinfo "$PROC/self/mountinfo"; do
        [ -e "$m" ] || continue
        if [ ! -r "$m" ]; then echo "$m (unreadable)"; continue; fi
        awk -v p="$1" '$5 == p || index($5, p "/") == 1 { print $5 }' "$m" 2>/dev/null
    done | sort -u
}
backed_by_files_in() {
    for b in "$SYSFS"/block/loop*/loop/backing_file; do
        [ -r "$b" ] && cat "$b" 2>/dev/null
    done | awk -v p="$1/" 'index($0, p) == 1' | head -n 3
}
# 1 when the stage at $1 records a session of this boot (still running, or a
# phone that was never restarted since).
# A session of this boot that is still running (guest_props.boot) or never
# returned (persist_session.active). 0.4.13: not return_keys/boot, the return
# watcher's record, which stays after a clean in-place return and kept every
# copy from such a session undeletable until a restart.
session_this_boot() {
    now=$(boot_id)
    [ -n "$now" ] || return 0
    for f in "$1/persist_session.active" "$1/guest_props.boot"; do
        [ -f "$f" ] && grep -q -F "$now" "$f" 2>/dev/null && return 0
    done
    return 1
}
old_checks() { # <path>
    d=$1
    # Root's, any mode: a foreign stage begin moved aside can be world-writable
    # (OneLineage's was drwxrwxrwx). rm -rf never follows the links inside, and
    # the mount checks below are what keeps One UI's data out of reach.
    [ -d "$d" ] && [ ! -L "$d" ] && [ "$(stat -c %u "$d" 2>/dev/null)" = "$ROOT_UID" ] ||
        refuse "${d#$T} is not a root-owned folder"
    [ "$d" != "$STAGE" ] || refuse "that is the active stage"
    m=$(mounted_anywhere "$d" | head -n 3 | tr '\n' ' ')
    [ -z "$m" ] || refuse "something is mounted in ${d#$T}: $m- restart the phone first"
    l=$(backed_by_files_in "$d" | tr '\n' ' ')
    [ -z "$l" ] || refuse "a loop device uses a file in ${d#$T}: $l- restart the phone first"
    b=$(bridged | head -n 3 | tr '\n' ' ')
    [ -z "$b" ] || refuse "still bridged from a session: $b- restart the phone first"
    if session_this_boot "$d"; then refuse "${d#$T} had a session in this boot - restart the phone first"; fi
}
do_old_list() {
    [ $# -eq 0 ] || usage "old-list"
    mountinfo_ok
    count=0
    for d in "$OLD_PARENT"/oneos.old-* "$OLD_PARENT"/oneos.trash-* "$OLD_PARENT"/lineage_stage.old-* "$OLD_PARENT"/lineage_stage.trash-*; do
        [ -d "$d" ] && [ ! -L "$d" ] || continue
        name=${d##*/}
        old_name_ok "$name" || continue
        kb=$(du -sk "$d" 2>/dev/null | cut -f1)
        is_dec "$kb" || kb=0
        img=unknown
        mk="$d/.oneos_stage"
        if [ -f "$mk" ] && [ ! -L "$mk" ]; then
            i=$(sed -n 's/^  "image": "\([a-z0-9-]*\)",$/\1/p' "$mk")
            is_id "$i" && img=$i
        fi
        data=0
        [ -d "$d/data" ] && data=1
        busy=0
        { [ -n "$(mounted_anywhere "$d")" ] || [ -n "$(backed_by_files_in "$d")" ] || session_this_boot "$d"; } && busy=1
        say "@@RESULT old $name $kb $img $data $busy"
        count=$((count + 1))
    done
    say "@@OK old-list $count"
}
# The only deletion of user data in this script, and its audited exception
# (StageAudit): OLDDIR is exactly $OLD_PARENT/<checked name>, TRASH its
# oneos.trash-<epoch> sibling, rm -rf only on "$TRASH". purge and remove
# (each copy, and the stage once it is renamed to one) both go through it.
purge_one() { # <name>, already old_name_ok
    asked=$1
    # Listed under its name up to 0.3.6 and renamed since (migrate_legacy).
    case "$1" in
        lineage_stage.*)
            if [ ! -e "$OLD_PARENT/$1" ] && [ ! -L "$OLD_PARENT/$1" ] && [ -d "$OLD_PARENT/oneos.${1#lineage_stage.}" ]; then
                set -- "oneos.${1#lineage_stage.}"
                old_name_ok "$1" || usage "purge: '$1' is not an old stage name"
            fi
            ;;
    esac
    OLDDIR="$OLD_PARENT/$1"
    old_checks "$OLDDIR"
    kb=$(du -sk "$OLDDIR" 2>/dev/null | cut -f1)
    is_dec "$kb" || kb=0
    case "$1" in
        oneos.trash-*|lineage_stage.trash-*) TRASH="$OLDDIR" ;;
        *)
            TRASH="$OLD_PARENT/oneos.trash-$(date +%s)"
            [ -e "$TRASH" ] && TRASH="$TRASH-$$"
            step "rename $1"
            mv "$OLDDIR" "$TRASH" || die "could not rename ${OLDDIR#$T}"
            ;;
    esac
    # Again on the new name: nothing may have appeared in between.
    old_checks "$TRASH"
    step "delete ${TRASH#$T}"
    rm -rf "$TRASH" || true
    [ ! -e "$TRASH" ] || die "could not delete all of ${TRASH#$T}; it stays as ${TRASH##*/}"
    say "@@RESULT purged $asked $kb"
}
do_purge() {
    [ $# -eq 1 ] || usage "purge <name>"
    old_name_ok "$1" || usage "purge: '$1' is not an old stage name"
    mountinfo_ok
    purge_one "$1"
    say "@@OK purge $1"
}

# Everything OneOS put on the phone (0.4.25, Advanced > Remove OneOS data;
# owner: "remove the folders it creates ... clean stages, or whole OneOS
# folders"): every old copy, the stage with the guest's data, the session's
# log capture ($OLD_PARENT/oneos_logs) and the session's helper files in
# $OLD_PARENT. Every check comes first, for all of them, so it refuses before
# its first change: the stage as restore checks it (nothing mounted at or
# under it anywhere, no loop device on its files, no session running or one
# that never returned), no bridge up, each copy as purge checks it, and the log
# folder root's with nothing mounted in it. The log folder and the helper files
# are then moved into the stage (the only writes outside it: $LOGS and $HELPER,
# exactly these names, StageAudit); the copies are purged, and the stage is
# renamed to an old copy and purged like one (purge_one checks again before and
# after its rename). Nothing else in /data/local/tmp is touched. This script
# runs on from its open file once its folder is gone.
do_remove() {
    [ $# -eq 0 ] || usage "remove"
    mountinfo_ok
    restore_checks "$STAGE"
    b=$(bridged | head -n 3 | tr '\n' ' ')
    [ -z "$b" ] || refuse "still bridged from a session: $b- restart the phone first"
    copies=""
    for d in "$OLD_PARENT"/oneos.old-* "$OLD_PARENT"/oneos.trash-* "$OLD_PARENT"/lineage_stage.old-* "$OLD_PARENT"/lineage_stage.trash-*; do
        [ -d "$d" ] && [ ! -L "$d" ] || continue
        old_name_ok "${d##*/}" || continue
        old_checks "$d"
        copies="$copies ${d##*/}"
    done
    LOGS="$OLD_PARENT/oneos_logs"
    if [ -e "$LOGS" ] || [ -L "$LOGS" ]; then
        [ -d "$LOGS" ] && [ ! -L "$LOGS" ] && [ "$(stat -c %u "$LOGS" 2>/dev/null)" = "$ROOT_UID" ] ||
            refuse "${LOGS#$T} is not a root-owned folder"
        m=$(mounted_anywhere "$LOGS" | head -n 3 | tr '\n' ' ')
        [ -z "$m" ] || refuse "something is mounted in ${LOGS#$T}: $m- restart the phone first"
    fi
    kb=$(du -sk "$STAGE" 2>/dev/null | cut -f1)
    is_dec "$kb" || kb=0
    mkdir_mine "$STAGE/removed" || die "could not make the stage's removed folder"
    if [ -d "$LOGS" ]; then
        step "move ${LOGS#$T} into the stage"
        mv "$LOGS" "$STAGE/removed/" || die "could not move ${LOGS#$T} into the stage"
    fi
    for HELPER in "$OLD_PARENT/adb_watchdog.sh" "$OLD_PARENT/adb_watchdog.log" "$OLD_PARENT/ims_watchdog.sh" "$OLD_PARENT/ims_watchdog.log" "$OLD_PARENT/ui_activator.log" "$OLD_PARENT/restore_app.log"; do
        [ -f "$HELPER" ] || [ -L "$HELPER" ] || continue
        mv "$HELPER" "$STAGE/removed/" || die "could not move ${HELPER#$T} into the stage"
    done
    for c in $copies; do purge_one "$c"; done
    OLD="$STAGE.old-$(date +%s)"
    [ -e "$OLD" ] && OLD="$OLD-$$"
    [ ! -e "$OLD" ] || die "${OLD#$T} exists already; the copies are gone, the stage stays"
    step "aside ${STAGE#$T}"
    mv "$STAGE" "$OLD" || die "could not move the stage aside; the copies are gone, the stage stays"
    purge_one "${OLD##*/}"
    say "@@RESULT removed $kb"
    say "@@OK remove"
}

# Use a set-aside stage again (0.4.10). Like purge's checks, but a session
# earlier in this boot that returned is fine (session_this_boot would refuse
# it): only a session still running or one that never returned stops it.
restore_checks() { # <path>
    d=$1
    [ -d "$d" ] && [ ! -L "$d" ] && [ "$(stat -c %u "$d" 2>/dev/null)" = "$ROOT_UID" ] ||
        refuse "${d#$T} is not a root-owned folder"
    m=$(mounted_anywhere "$d" | head -n 3 | tr '\n' ' ')
    [ -z "$m" ] || refuse "something is mounted in ${d#$T}: $m- return to One UI, or restart the phone"
    l=$(backed_by_files_in "$d" | tr '\n' ' ')
    [ -z "$l" ] || refuse "a loop device uses a file in ${d#$T}: $l- restart the phone first"
    [ ! -f "$d/persist_session.active" ] || refuse "${d#$T} holds a session that never returned: restore One UI's settings first (Last session)"
    if [ -f "$d/guest_props.boot" ] && [ "$(cat "$d/guest_props.boot" 2>/dev/null)" = "$(boot_id)" ]; then
        refuse "${d#$T} has a session running"
    fi
}
do_restore() {
    [ $# -eq 1 ] || usage "restore <name>"
    case "$1" in oneos.old-*) ;; *) usage "restore: '$1' is not a set-aside stage (oneos.old-<time>)" ;; esac
    old_name_ok "$1" || usage "restore: '$1' is not an old stage name"
    mountinfo_ok
    RFROM="$OLD_PARENT/$1"
    [ "$RFROM" != "$STAGE" ] || refuse "that is the active stage"
    [ -f "$RFROM/.oneos_stage" ] && [ ! -L "$RFROM/.oneos_stage" ] || refuse "$1 has no stage marker: OneOS did not set it up"
    [ -d "$RFROM/bin" ] && [ ! -L "$RFROM/bin" ] || refuse "$1 has no bin folder: not a whole stage"
    mine "$RFROM" && mine "$RFROM/bin" || refuse "$1 or its bin folder is not root's alone (writable by others)"
    b=$(bridged | head -n 3 | tr '\n' ' ')
    [ -z "$b" ] || refuse "still bridged from a session: $b- restart the phone first"
    restore_checks "$RFROM"
    restore_checks "$STAGE"
    OLD="$STAGE.old-$(date +%s)"
    [ -e "$OLD" ] && OLD="$OLD-$$"
    [ ! -e "$OLD" ] || refuse "${OLD#$T} exists already"
    step "aside ${STAGE#$T}"
    mv "$STAGE" "$OLD" || die "could not move the stage aside"
    step "restore $1"
    if ! mv "$RFROM" "$STAGE"; then
        mv "$OLD" "$STAGE" || die "could not restore $1, nor put the stage back from ${OLD#$T}"
        die "could not restore $1; the stage is as it was"
    fi
    say "@@RESULT restored $1 ${OLD##*/}"
    say "@@OK restore $1"
}

# --- The names up to app 0.3.6 ------------------------------------------------
# The stage was lineage_stage (the name OneLineage gave it), its copies
# lineage_stage.old-<epoch>[-<pid>] and lineage_stage.trash-<epoch>. Before its
# verb, every run renames them to the OneOS names with the same suffix, each
# one only while it is idle (legacy_busy: nothing mounted at or under it in any
# mount table root can read, no loop device on a file in it, no bridge up, no
# session of this boot) and only while the new name is free. The launcher runs
# this script from the old stage while there is no new one. A copy that cannot
# be renamed yet keeps its name (old-list and purge take both). The stage:
#   stage  (the verbs that use it) refuse while it cannot be renamed, and while
#          both lineage_stage and oneos exist;
#   copies (old-list, purge: they never touch the stage) go on, from the old
#          stage while it cannot be renamed.
# The only writes are the two mv below, from $LEGACY or a checked
# lineage_stage.* name to its OneOS name (StageAudit).
legacy_busy() { # <path>: why it cannot be renamed now; nothing when it is idle
    lb=$(mounted_anywhere "$1" | head -n 3 | tr '\n' ' ')
    [ -z "$lb" ] || { say "something is mounted in it: $lb"; return 0; }
    lb=$(backed_by_files_in "$1" | tr '\n' ' ')
    [ -z "$lb" ] || { say "a loop device uses a file in it: $lb"; return 0; }
    lb=$(bridged | head -n 3 | tr '\n' ' ')
    [ -z "$lb" ] || { say "still bridged from a session: $lb"; return 0; }
    if session_this_boot "$1"; then say "it had a session in this boot"; fi
    return 0
}
migrate_legacy() { # <stage|copies>
    lm=0
    for lp in "$LEGACY" "$OLD_PARENT"/lineage_stage.old-* "$OLD_PARENT"/lineage_stage.trash-*; do
        [ -d "$lp" ] && [ ! -L "$lp" ] && lm=1
    done
    [ "$lm" = 1 ] || return 0
    mountinfo_ok
    for MFROM in "$OLD_PARENT"/lineage_stage.old-* "$OLD_PARENT"/lineage_stage.trash-*; do
        [ -d "$MFROM" ] && [ ! -L "$MFROM" ] || continue
        mn=${MFROM##*/}
        old_name_ok "$mn" && [ "$(stat -c %u "$MFROM" 2>/dev/null)" = "$ROOT_UID" ] || continue
        MTO="$OLD_PARENT/oneos.${mn#lineage_stage.}"
        if [ -e "$MTO" ] || [ -L "$MTO" ]; then
            say "@@INFO $mn keeps its name: ${MTO##*/} exists"
            continue
        fi
        why=$(legacy_busy "$MFROM")
        if [ -n "$why" ]; then
            say "@@INFO $mn keeps its name for now: $why"
            continue
        fi
        if mv "$MFROM" "$MTO"; then say "@@INFO renamed $mn to ${MTO##*/}"; else say "@@INFO could not rename $mn"; fi
    done
    [ -d "$LEGACY" ] && [ ! -L "$LEGACY" ] || return 0
    if [ -e "$STAGE" ] || [ -L "$STAGE" ]; then
        both="both ${STAGE#$T} and ${LEGACY#$T} (the stage's path up to OneOS 0.3.6) are on this phone, and OneOS uses one stage: move the one you do not need out of /data/local/tmp as root (or rename it to ${STAGE#$T}.old-<time> to see it under Old copies), then try again"
        [ "$1" = copies ] || refuse "$both"
        say "@@INFO $both"
        return 0
    fi
    why=$(legacy_busy "$LEGACY")
    [ -n "$why" ] || [ "$(stat -c %u "$LEGACY" 2>/dev/null)" = "$ROOT_UID" ] || why="it is not root's folder"
    if [ -n "$why" ]; then
        [ "$1" = copies ] || refuse "the stage is still at ${LEGACY#$T}, its path up to OneOS 0.3.6, and cannot be renamed to ${STAGE#$T} now ($why) - restart the phone first"
        say "@@INFO the stage stays at ${LEGACY#$T} for now: $why"
        STAGE="$LEGACY"
        return 0
    fi
    step "rename ${LEGACY#$T} to ${STAGE#$T}"
    mv "$LEGACY" "$STAGE" || die "could not rename ${LEGACY#$T} to ${STAGE#$T}"
    [ "$SELF" != "$LEGACY/bin/oneos_root.sh" ] || SELF="$STAGE/bin/oneos_root.sh"
    say "@@INFO renamed the stage ${LEGACY#$T} to ${STAGE#$T}"
}

# --- Main ---------------------------------------------------------------------
[ -n "$T" ] || [ "$(id -u)" = 0 ] || refuse "not root"
verb=$1
case "$verb" in
    begin|receive|keep-raw|table|env|finish|launch|recover|restore|remove) migrate_legacy stage ;;
    old-list|purge) migrate_legacy copies ;;
esac
[ -d "$STAGE" ] && mine "$STAGE" || refuse "$STAGE is not root's folder"
[ -d "$STAGE/bin" ] && mine "$STAGE/bin" || refuse "$STAGE/bin is not root's folder"
[ $# -gt 0 ] && shift
case "$verb" in
    begin) do_begin "$@" ;;
    receive) do_receive "$@" ;;
    keep-raw) do_keep_raw "$@" ;;
    table) do_table "$@" ;;
    env) do_env "$@" ;;
    finish) do_finish "$@" ;;
    launch) do_launch "$@" ;;
    recover) do_recover "$@" ;;
    old-list) do_old_list "$@" ;;
    purge) do_purge "$@" ;;
    restore) do_restore "$@" ;;
    remove) do_remove "$@" ;;
    *) usage "unknown verb '$verb'" ;;
esac
