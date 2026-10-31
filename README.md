<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="images/banner-dark-2x.png">
    <source media="(prefers-color-scheme: light)" srcset="images/banner-light-2x.png">
    <img src="images/banner-light-2x.png" width="100%" alt="OneOS: another Android, next to One UI. Nothing flashed. Switch from an app, come back the same way. A One UI phone and a OneOS foldable, with arrows going both ways.">
  </picture>
</p>

<p align="center">
  <img alt="Status: experimental" src="https://img.shields.io/badge/status-experimental-5B3FCB?style=flat-square&labelColor=2B3445">
  <img alt="Android 17" src="https://img.shields.io/badge/Android-17-1C5FD6?style=flat-square&labelColor=2B3445">
  <img alt="One UI 9" src="https://img.shields.io/badge/One%20UI-9-1C5FD6?style=flat-square&labelColor=2B3445">
  <img alt="Root: KernelSU" src="https://img.shields.io/badge/root-KernelSU-1C5FD6?style=flat-square&labelColor=2B3445">
</p>

OneOS runs a second Android system next to One UI on a rooted Samsung Galaxy
and switches between the two without flashing anything. One UI stays
installed; the other system lives in a folder on the phone and starts on One
UI's kernel as a container with its own zygote and system_server. You switch
from the OneOS app and come back with **Back to One UI** (the OneOS app inside
the other system) or by holding **Volume Up + Volume Down** for 3 seconds.

OneOS started from [OneLineage](https://github.com/WeWaffles/OneLineage) by
WeWaffles, and builds its guest images with
[MysticGSI](https://github.com/MysticGSI/mysticgsi). See [Credits](#credits).

> [!WARNING]
> **Experimental. It can break your phone's data. Do not try this unless you
> know what you are doing.** OneOS replaces core parts of a running system as
> root. During development a guest system reached One UI's data and deleted
> app data and photos, and a guest once took over the phone's lock-screen
> hardware slot so that One UI could not unlock until a factory reset. Both
> are guarded against now, but untested phones and images can fail in new
> ways. **You must back up your accounts and data first, and remove the screen
> lock (PIN, pattern, password, fingerprint, face) before switching.** Only use
> a phone you can afford to reset. Nothing here is supported by Samsung,
> Google or OPPO.

## Status

App version 0.4.34. Samsung flagships with Snapdragon, plus the first four
Exynos phones and (0.4.33) the first MediaTek phone; root is required (tested with KernelSU; Magisk and APatch are
detected but untested).

Where each system has actually started, on our phones and on testers' (One UI
9 throughout). What they reported working, item by item, is in
[docs/compatibility.md](docs/compatibility.md).

| Guest system | Has run on |
| --- | --- |
| Pixel 11 Fold (Android 17) | Galaxy Z Fold8 Ultra (SM-F976B), Z Fold8 (SM-F971B) |
| ColorOS 17, Find N6 build | Galaxy Z Fold8 Ultra (SM-F976B), Z Fold8 (SM-F971B) |
| ColorOS 17, Find X9 Ultra CN build | Galaxy S26 Ultra (SM-S948), S25 Ultra (SM-S938), S25 (SM-S931), A57 (SM-A576B) |
| ColorOS 17, Find X9 Ultra IN (global) build | Galaxy Z Flip5 (SM-F731B) |
| Infinity-X 4.1 | Galaxy S26 Ultra (SM-S948), S25 Ultra (SM-S938), S25 (SM-S931), S24 Ultra (SM-S928), Z Flip5 (SM-F731B) |

Started is not the same as finished: on Infinity-X the reports so far say calls
do not work and SMS only sometimes, which is what the IMS switch in Advanced is
for (0.4.31, Infinity-X and HyperOS). On the Z Flip5 the camera was broken in
both systems; 0.4.31 carries the fix.

Untested profiles, where the app asks you to tick that you know before
switching: Galaxy Z Fold8 (SM-F971) and Z Fold7 (SM-F966), Galaxy S26+ (SM-S947),
S26 (SM-S942, SC-51G), S25+ (SM-S936) and S24 Ultra (SM-S928) with ColorOS; the
Pixel 11 Fold image on the Z Fold8, the Pixel 11 Pro image on the Galaxy S26
Ultra, S26+ and S26, and the Pixel 9 Pro XL image on the Galaxy S25 Ultra, S25,
S25+ and S24 Ultra (the Pixel 11 images need a newer CPU), and from 0.4.30 also on
the Galaxy S23, S23+, Z Flip5, Z Flip7, A57 and (0.4.31) A56; and on every Galaxy S phone above, the Find X9 Ultra IN (global) build
of ColorOS 17, with Google Play and the Play Store; and on every phone above,
Infinity-X 4.1 (0.4.27), a custom Android 17 made for the Find X9 Ultra, with
Google Play and Google's apps; and from 0.4.31, on every phone above, HyperOS,
Xiaomi's system from the Xiaomi 18 Fold, made for the Galaxy Z Fold8 Ultra and Z
Fold8 (its cover screen layout runs on the cover; other phones may try it too,
where it runs as a phone). Galaxy S23 (SM-S911),
S23+ (SM-S916) and, from 0.4.29, the Galaxy Z Flip5 (SM-F731, used as a phone on
the main screen) are accepted for testing only:
their vendor software is too old and the system will most likely not start
(One UI comes back by itself; please share the session logs).

Exynos, first runs: the Galaxy S26+ with Exynos (SM-S947B, m2s) with both
Find X9 Ultra builds and the Pixel 11 Pro image, the Galaxy Z Flip7
(SM-F766, Exynos 2500) with both Find X9 Ultra builds, used as a phone on the
main screen (no cover screen layout yet), from 0.4.29 the Galaxy A57
(SM-A576, a57x) with both Find X9 Ultra builds, the first A-series phone, and
from 0.4.31 its smaller sibling the Galaxy A56 (SM-A566, a56x), with the same
images. The
S26+ and Z Flip7 test sessions fixed the first Exynos problems (the phone
restarting after the switch); every system on Exynos is still untested. Each
of them also offers Infinity-X.

New in 0.4.33, untested: the Galaxy Z Fold6 (SM-F956, q6q, the S24 Ultra's
Snapdragon 8 Gen 3) with the Find N6 build and Infinity-X, and the Galaxy A07
(SM-A075, MediaTek MT6789, One UI 9), the first MediaTek phone, with Infinity-X
only. The Galaxy A16 and A17 on One UI 8.5 (Android 16) are not supported yet:
every system here is Android 17.

New in 0.4.34: sound and the microphone in Infinity-X on the S25 series (its
audio service waited for a One UI extension that is not there during a
session); the Bluetooth audio bridge starts on the phones that need it (0.4.33
checked too early and skipped it); the camera opens without a 5 s wait and a
refusal on the S25 Ultra's firmware; One UI's brightness curve for every
phone's own screen in Pixel and HyperOS; HyperOS's preinstalled apps
(Downloads, Weather, Game Center, WPS) find their libraries.

Also in 0.4.33: Bluetooth audio on the phones whose One UI has only Samsung's
Bluetooth audio HAL (S25, S24, S23, Z Fold7, Z Flip7 and Flip5, A57, A56;
untested there), through a small bridge to Samsung's HAL; Chrome loads pages
again in every system (it is told to use its Java renderers); the camera opens
without Knox's 5 s wait by default; on bar phones the Pixel and Infinity-X
status bar takes One UI's height and corner size.

Pixel 11 Fold on the Galaxy Z Fold8 Ultra: calls and SMS on both SIMs, 5G and
Wi-Fi, Bluetooth audio, Google Play and Google's apps, the inner and cover
screens, blur, and Back to One UI (usually without a restart).

Pixel 9 Pro XL on the Galaxy S25 Ultra (first session, 0.4.22): it starts,
runs Google's setup wizard, has mobile data on SIM 1, Bluetooth and stereo
speakers, and Back to One UI works. The phone got very hot. 0.4.23 fixes the
crash loop behind that, renders at 120 Hz instead of dropping to 60, and adds
a heat cap (Advanced > Cool down when hot):
One UI's own heat control does not run in the other system, so from 45 °C
OneOS slows the processor until the phone cools.

Camera in the Pixel systems (0.4.24): the flashlight tile and apps that use
the Camera2 API work (Open Camera: Settings › Camera API › Camera2). Apps
that use the older camera API still fail; Pixel Camera itself needs Google's
camera hardware support and will not work on a Galaxy.

Known open issues for each system are in `docs/pixel-guest.md` and
`docs/coloros-on-fold.md`.

Everything else (other Exynos phones, other models, other images, other
Android versions) is refused by the app's compatibility checks.

## Your phone is not listed?

Run **OneOS Probe** on it. It is a separate read-only app: it changes nothing,
and it strips the IMEI, phone numbers and Wi-Fi names from its report (it
needs root to read the system). Tap **Run tests**, then **Send to the OneOS
team** (from 0.4.29), or **Share** and post the zip with your phone model in
**Phone reports** in the [OneOS Telegram group](https://t.me/TheoneOS). New releases are announced
there too. Probe is in the same download folder as the app (see the latest
release).

## Getting started

**Before anything else (do not skip these):**

1. **Use a tested phone.** The systems above have only been tested on the
   phones listed next to them. On any other phone the app warns you, and things
   may not work.
2. **Back up your accounts and data.** Samsung Cloud, Google and Smart Switch,
   plus photos and anything else you can't lose. OneOS guards One UI's data,
   but it is experimental.
3. **Remove your screen lock.** Settings › Lock screen › Screen lock type ›
   **None** or **Swipe**: no PIN, pattern, password, fingerprint or face. If
   something goes wrong and the phone boot-loops, a locked phone may need a
   factory reset to recover. OneOS will not switch while a lock is set.
   From 0.4.32 Advanced has a switch, under **Risky**, to do it anyway: your
   One UI apps and photos are encrypted behind that lock, so a session that
   leaves the phone unable to accept it is a factory reset, not an
   inconvenience. Leave it off unless you have a reason and accept that.
4. **Root, battery and heat.** The phone must be rooted (tested with KernelSU),
   charged to at least 30 % and not hot (under 42 °C).

**Then:**

5. Install the OneOS app made for your phone's series and allow it in your
   root manager. From 0.4.25 there are three downloads, each about half the
   size of the old single app:
   `OneOS-<version>-fold.apk` (Galaxy Z Fold8 Ultra, Z Fold8, Z Fold7, Z Fold6),
   `OneOS-<version>-s26.apk` (Galaxy S26 Ultra, S26+, S26) and
   `OneOS-<version>-s25.apk` (Galaxy S25 Ultra, S25+, S25, S24 Ultra, S23,
   S23+, Z Flip7, Z Flip5, A57, A56, A07). Installed the wrong one? OneOS tells you which file your
   phone needs.
6. In OneOS open **Systems**, tap **Download** on the system made for your
   phone and download it, on Wi-Fi (3.3 to 5.3 GB).
7. Back in OneOS tap **I have the file**, pick the download, then **Set up**.
   Setup takes about 15 minutes: keep OneOS open and the phone plugged in. One
   UI keeps working meanwhile.
8. Tap **Switch**. To come back, tap **Back to One UI** in the other system
   (usually no restart), or hold **Volume Up + Volume Down** for 3 seconds.
   Stuck on the logo? Hold
   **Power + Volume Down** to restart into One UI.

If your root does not survive a restart, activate it again after you come back.

**Tell us how it went** (from 0.4.29): after a session, open **Last session**
and tap **Send to the OneOS team**. Say whether the system started and tick
what worked and what had problems (Wi-Fi, calls, Bluetooth, camera and so on);
a note and your Telegram name are optional. By default the app sends the short
log (the first minutes after the switch and the last two before it ended); it
sends the whole session's log when you tick a problem or turn it on. The logs
are the same file **Share** sends, with personal data already removed, and the
team keeps reports for 30 days. **Share** and **Save** are still there if
sending does not work. What everyone reported is counted in
[docs/compatibility.md](docs/compatibility.md): per OneOS version, phone and
system, how many reports started and what worked (answers only: no names,
notes or phone details).

You can tick what works while you are still in the other system (from
0.4.30): open the OneOS app there and tap **How's it going?**. Back in One UI,
**Send to the OneOS team** has your answers filled in.

**More than one system** (from 0.4.30): when you set up another system, the
one you had stays on the phone as a copy, with its apps and data. Home lists
each of them as **Switch to <system>**. One tap puts it back (the system set
up now is kept the same way), updates OneOS's files in it if they are older
than the app (about 2 minutes) and opens the switch. A copy you no longer
need can be deleted there too, or in **Advanced › Free up space**.

**Removing OneOS:** in OneOS open **Advanced › Remove OneOS data**. It deletes
everything OneOS put on the phone: the system you set up (with the apps and
accounts in it), older copies and the session logs. It checks first that
nothing of a session is still connected to One UI, and refuses otherwise. One
UI and its data are not touched, and downloaded system files stay where you
saved them. Then uninstall the app as usual. Without the app, the same cleanup
runs from a root shell such as Termux: `sh remove-oneos-data.sh` (from
`scripts/`; it asks su for root, and `--dry-run` shows first what it would
delete).

## How it works

1. On a build machine, an OEM firmware image is turned into a system image
   (with MysticGSI, outside this repository), and `scripts/make-overlays.sh`
   patches its framework (`switcher/patches/`) so it can run as a guest next to
   One UI.
2. The app checks the phone and the image (size and SHA-256 against its own
   table), then, as root, stages the image and its patched files in
   `/data/local/tmp/oneos`.
3. `switcher/assets/oneos_switch.sh` stops One UI's zygote and system_server
   (the kernel, vendor services and radio keep running) and `guest_prepare.sh`
   / `guest_start.sh` start the guest's own framework in a mount namespace whose
   `/system` is the image and whose `/data` is the stage.
4. Guards keep the guest away from One UI's data and keys: its own vold,
   storage and installd guards, no weaver or gatekeeper writes, no new users,
   app ids of its own (`docs/app/patch-catalog.md`).
5. `return_to_oneui.sh` stops the guest and gives the phone back to One UI, in
   place when it can and by a reboot otherwise (`docs/inplace-return.md`).

## Repository

| Path | What it is |
| --- | --- |
| `app-android/` | The OneOS app and OneOS Probe (Kotlin, One UI look through SESL), the in-guest Back to One UI app, and their tests |
| `switcher/assets/` | The session scripts the app ships: switch, prepare, start, return, bridges |
| `switcher/patches/` | Framework patchers (Python and smali) applied by `make-overlays.sh`, and Samsung's installd interface table |
| `switcher/overlays/`, `switcher/native/` | Runtime resource overlays, and the source of the guest zygote launcher (`zygote_exec`) |
| `scripts/` | Build-machine tools: the overlay build, adb deploy for development, log collection |
| `docs/` | Design notes, the patch catalog, and what each test session showed |

## Building from source

```sh
git clone https://github.com/fahadalijaved/OneOS.git
cd OneOS/app-android
./gradlew assembleProbeDebug     # OneOS Probe: needs nothing outside this repository
./gradlew test                   # unit tests (dash or busybox for the root script tests)
```

Requirements: JDK 21, the Android SDK with platform `android-37`
(`ANDROID_HOME` or `sdk.dir` in `local.properties`), and internet access to
Google Maven and Maven Central. Details: `app-android/README.md`.

The full OneOS app (`assembleFullDebug`, `assembleFullRelease`) also packs the
package sets: the files OneOS patches out of each built system image, from
`../out/overlays*`. They are not in this repository. Building the guest images
themselves is not part of this repository either (OneOS's images are built with
MysticGSI, see Credits). What is here is how OneOS patches a built image:

```sh
APKTOOL=/path/to/apktool.jar EXTRACT_EROFS=/path/to/extract.erofs \
    scripts/make-overlays.sh <system.img> out/overlays   # the set for that image
```

The build fails unless every file matches `app-android/packages/<set>.sha256`
exactly, so the sets must come from the same firmware builds. Release builds
are signed with a key from `~/.oneos-release/keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it the
release packaging stops. Debug builds use your machine's debug key.

## Where the images come from

This repository does not host system images, OEM firmware or the patched
framework files built from them, and GitHub releases never carry them. They
contain Samsung's, Google's or OPPO's code. The guest images are built from
the vendor's own firmware outside this repository, and the app accepts only
the builds whose size and SHA-256 it knows
(`app-android/core/model/src/main/kotlin/com/fahadali/oneos/core/model/Images.kt`).
The `firmware/` and `out/` folders are ignored by git for that reason.

## License

OneOS is licensed under the [Apache License 2.0](LICENSE). Parts of this
repository come from other projects and keep their own terms: the
OneLineage-derived session scripts (published with WeWaffles' permission). See
[NOTICE](NOTICE) and [CREDITS.md](CREDITS.md).

## Credits

- **[OneLineage](https://github.com/WeWaffles/OneLineage)** by **WeWaffles**:
  the container hot-switch OneOS is built on. OneOS's session scripts started
  from OneLineage's, and WeWaffles gave permission to publish this work on top
  of it, with credit here and in the app.
- **[MysticGSI](https://github.com/MysticGSI/mysticgsi)** (The MysticGSI
  Authors, Apache-2.0): builds the guest images OneOS runs (not part of this
  repository). MysticGSI includes TrebleDroid work by Pierre-Hugues Husson
  (phh) and contributors.
- **[Project Infinity-X](https://github.com/ProjectInfinity-X)** and its Find X9
  Ultra build ([X9UInfinityX](https://github.com/X9UInfinityX)): the Infinity-X
  system OneOS runs from 0.4.27, unchanged, with OneOS's own framework patches.
- **[OneUIProject](https://github.com/OneUIProject)**: SESL, oneui-design and
  oneui-icons (MIT) give the app its One UI look.
- **[Josh Skinner (@thatjoshguy67)](https://github.com/thatjoshguy67)**: Galaxy
  Z Fold8 support ([pull request #1](https://github.com/fahadalijaved/OneOS/pull/1)):
  the Fold8's Find N6 ColorOS set, its cover display, Quick Settings, AOD, widgets,
  Pocket Studio and Google fixes, and Samsung's hinge sensor for half-folded
  postures and the open/close animations in ColorOS and Pixel, which the Z Fold8
  Ultra uses too.
- smali/dexlib2, AndroidX, Kotlin, Guava, ZXing and others: the full list with
  licenses is in [CREDITS.md](CREDITS.md).

Not affiliated with Samsung, Google, OPPO, OnePlus or Project Infinity-X. Galaxy and One UI are
trademarks of Samsung Electronics; Android and Pixel of Google LLC; ColorOS of
OPPO.
