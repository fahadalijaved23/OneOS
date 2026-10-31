"""Let HyperOS's wallpaper app start when a wallpaper has no effect type yet.

com.miui.miwallpaper (product/app/MiWallpaper) died in its Application.onCreate
on every start on a bar phone (0.4.32, Galaxy S25 report d52cef74: 17 times,
"Unable to create application com.miui.miwallpaper.MiWallpaperApplication",
NullPointerException in WallpaperTypeUtils.f), so there was no wallpaper at all
and the earlier S26 reports said "wallpaper doesn't work". EffectUtils.e asks
MiuiWallpaperManagerService for the lock and home wallpapers' effect type by a
"which" it works out from the displays, and on a fresh stage of a one-panel
phone the lock one has none: f(null) then calls getClass() on it (Kotlin's null
check) and throws.

f answers whether a type is one of the effect types it knows (image, gallery,
dark, depth_blur, super_save_power) and false for any other. The patch gives a
null type that same false, before the null check: an unknown type, so the app
goes on as for any other wallpaper without an effect. A non-null type takes the
same path as before.

Usage: hyperos_miwallpaper_null.py <smali root dir>
Prints the smali_* directory (dex file) it changed.
"""
import os
import sys

REL = os.path.join("com", "miui", "miwallpaper", "utils", "WallpaperTypeUtils.smali")
HEADER = ".method public static f(Ljava/lang/String;)Z"
OLD = [
    "    .locals 4",
    "    const/4 v0, 0x1",
    "    const/4 v1, 0x0",
    "    invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;",
]


def main():
    root = sys.argv[1]
    hits = [os.path.join(d, f) for d, _, fs in os.walk(root) for f in fs
            if os.path.join(d, f).endswith(os.sep + REL)]
    if len(hits) != 1:
        sys.exit(f"expected exactly one {REL}, found {len(hits)}")
    path = hits[0]
    lines = open(path).read().split("\n")
    idx = [i for i, l in enumerate(lines) if l == HEADER]
    if len(idx) != 1:
        sys.exit("WallpaperTypeUtils.f(String) not found")
    end = lines.index(".end method", idx[0])
    body = [(i, l) for i, l in enumerate(lines[idx[0] + 1:end], idx[0] + 1)
            if l.strip() and not l.strip().startswith(".line")]
    if [l for _, l in body[:4]] != OLD:
        sys.exit("WallpaperTypeUtils.f does not start as this patch expects; left as it is")
    if any("oneos_type_known" in l for _, l in body):
        sys.exit("already patched")
    at = body[3][0]  # the getClass() null check
    lines[at:at] = [
        "    # OneOS: a wallpaper without an effect type yet is an unknown type (hyperos_miwallpaper_null.py).",
        "    if-nez p0, :oneos_type_known",
        "",
        "    return v1",
        "",
        "    :oneos_type_known",
    ]
    open(path, "w").write("\n".join(lines))
    print(os.path.relpath(path, root).split(os.sep)[0])


if __name__ == "__main__":
    main()
