package com.fahadali.oneos.core.model

/**
 * The OneOS downloads by phone series (0.4.25, owner: "start repacking apks
 * per device or at least series"). The all-in-one APK carried every package
 * set (1.37 GB at 0.4.24); a series APK carries only the sets its phones'
 * systems use, about 0.6 GB. app/build.gradle.kts builds one with
 * -Poneos.series=<id> from app-android/packages/series.txt, which lists the
 * same sets as [setIds] (ApkSeriesTest); the version name then ends in
 * "-<id>" (OneOS-0.4.25-fold.apk). Without the property it packs every set.
 * Every profile is in exactly one series.
 */
enum class ApkSeries(val id: String, val label: String, val profileIds: List<String>) {
    FOLD("fold", "Galaxy Z Fold", listOf(Profiles.ID_F8U, Profiles.ID_FOLD8, Profiles.ID_FOLD7, Profiles.ID_FOLD6)),
    S26(
        "s26",
        "Galaxy S26 series",
        listOf(Profiles.ID_S_ULTRA_8850, Profiles.ID_S_PLUS_8850, Profiles.ID_S_8850, Profiles.ID_S_8850_JP, Profiles.ID_S_PLUS_9965),
    ),
    S25(
        "s25",
        "Galaxy S25, S24, S23, Z Flip7, Z Flip5, A57, A56 and A07",
        listOf(
            Profiles.ID_S_ULTRA_8750, Profiles.ID_S_PLUS_8750, Profiles.ID_S_8750, Profiles.ID_S_ULTRA_8650,
            Profiles.ID_S_8550, Profiles.ID_S_PLUS_8550, Profiles.ID_FLIP7, Profiles.ID_A56, Profiles.ID_A57, Profiles.ID_FLIP5,
            Profiles.ID_A07,
        ),
    ),
    ;

    /** The sets this series' APK carries: every set its phones' systems use. */
    fun setIds(profiles: List<PhoneProfile> = Profiles.BUILT_IN): Set<String> =
        profileIds.mapNotNull { id -> profiles.firstOrNull { it.id == id } }
            .flatMap { p -> p.choices.map { it.packageSetId } }
            .toSortedSet()

    companion object {
        fun byId(id: String?): ApkSeries? = entries.firstOrNull { it.id == id }

        /** The series a profile's phones download. */
        fun of(profileId: String): ApkSeries? = entries.firstOrNull { profileId in it.profileIds }
    }
}
