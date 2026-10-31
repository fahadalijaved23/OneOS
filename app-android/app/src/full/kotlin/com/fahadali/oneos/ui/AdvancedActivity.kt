package com.fahadali.oneos.ui

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.OutcomeKind
import com.fahadali.oneos.session.LogJob
import com.fahadali.oneos.session.OldStagesJob
import com.fahadali.oneos.session.RemoveJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import dev.oneuiproject.oneui.R as OuiIcons

/**
 * Advanced (the redesign): a few plain cards, each row named like the screen
 * it opens.
 * - Setup and space: Repair or start fresh (the setup page),
 *   Use a downloaded file, Free up space (free space and the old copies,
 *   from what is known already: no root call here).
 * - Sessions: Last session (how it went), Session logs (when collected),
 *   Restore One UI's settings (only when Last session offers it; the same
 *   confirm).
 * - Help: Before you start, About OneOS.
 * - For the OneOS team: one entry for the technical screens ([TeamActivity]).
 */
class AdvancedActivity : ToolbarActivity() {
    override val screenTitle: CharSequence get() = getString(R.string.advanced_title)

    override fun createFragment(): Fragment = AdvancedFragment()
}

class AdvancedFragment : CardsFragment() {
    private val subs = mutableListOf<() -> Unit>()

    override fun onStart() {
        super.onStart()
        subs += LogJob.observe { redraw() }
        subs += OldStagesJob.observe { redraw() }
        subs += StageCheck.observe { redraw() }
        subs += RemoveJob.observe { redraw() }
    }

    override fun onResume() {
        super.onResume()
        // Back from a screen below (a copy deleted, logs collected): read again.
        redraw()
    }

    override fun onStop() {
        subs.forEach { it() }
        subs.clear()
        super.onStop()
    }

    override fun build(column: LinearLayout) {
        val ctx = column.context
        fun open(cls: Class<*>): (View) -> Unit = { startActivity(Intent(ctx, cls)) }

        // Setup and space.
        val system = Cards.section(column, getString(R.string.advanced_card_system), rows = true)
        Cards.row(system, OuiIcons.drawable.ic_oui_refresh, getString(R.string.prepare_title), getString(R.string.advanced_setup_summary), open(PrepareActivity::class.java))
        Cards.row(system, OuiIcons.drawable.ic_oui_folder_open, getString(R.string.image_title), getString(R.string.advanced_file_summary), open(ImageActivity::class.java))
        Cards.row(system, OuiIcons.drawable.ic_oui_manage_storage_outline, getString(R.string.space_title), spaceSummary(), open(OldStagesActivity::class.java))
        // Remove OneOS data (0.4.25): everything OneOS put on the phone, after root checks it all.
        val removing = RemoveJob.state
        if (removing.busy) {
            CardRows.tint(Cards.row(system, OuiIcons.drawable.ic_oui_delete_outline, getString(R.string.remove_running), null), Level.INFO)
        } else {
            Cards.row(system, OuiIcons.drawable.ic_oui_delete_outline, getString(R.string.remove_title), removing.message ?: getString(R.string.remove_summary)) {
                confirmRemove()
            }
        }

        // Sessions.
        val sessions = Cards.section(column, getString(R.string.advanced_card_sessions), rows = true)
        Cards.row(sessions, R.drawable.ic_oneos_row_last_time, getString(R.string.last_title), lastSummary(), open(LastSessionActivity::class.java))
        // Read by every launch (LaunchJob), from Home or the Switch screen.
        CardRows.toggle(sessions, getString(R.string.advanced_heat_cap_title), getString(R.string.advanced_heat_cap_summary), SessionPrefs.heatCap(ctx)) { on ->
            SessionPrefs.setHeatCap(ctx, on)
        }
        val logs = LogJob.state
        val zip = LogJob.latestZip(ctx)
        if (zip != null && !logs.busy) {
            Cards.row(sessions, OuiIcons.drawable.ic_oui_file_type_txt, getString(R.string.logs_title), getString(R.string.advanced_logs_summary)) {
                startActivity(Intent(ctx, FindingsActivity::class.java).putExtra(FindingsActivity.EXTRA_ZIP, zip.path))
            }
        }
        if (logs.busy && logs.recovering) {
            CardRows.tint(Cards.row(sessions, OuiIcons.drawable.ic_oui_restart, getString(R.string.home_restoring), null), Level.INFO)
        } else if (!logs.busy && SessionRestore.offered(ctx)) {
            Cards.row(sessions, OuiIcons.drawable.ic_oui_restart, getString(R.string.last_restore_title), getString(R.string.advanced_restore_summary)) {
                activity?.let { SessionRestore.confirm(it) }
            }
        }

        // Calls (0.4.20): this phone's Samsung IMS app in the other system, for VoLTE. Experimental, off by default.
        val calls = Cards.section(column, getString(R.string.advanced_card_calls), rows = true)
        CardRows.toggle(calls, getString(R.string.advanced_sec_ims_title), getString(R.string.advanced_sec_ims_summary), SessionPrefs.secIms(ctx)) { on ->
            SessionPrefs.setSecIms(ctx, on)
        }
        // Camera (0.4.32): One UI's camera service asks Knox on every open and waits
        // 5 s for a service the other system does not have, so the camera app gives
        // up first. On by default since 0.4.33 (every 0.4.32 camera report waited).
        CardRows.toggle(calls, getString(R.string.advanced_edm_stub_title), getString(R.string.advanced_edm_stub_summary), SessionPrefs.edmStub(ctx)) { on ->
            SessionPrefs.setEdmStub(ctx, on)
        }

        // Risky (0.4.32, owner's call): things that can cost the phone's data. The
        // card is headed in red so it does not read like the switches above it.
        val risky = Cards.section(
            column, getString(R.string.advanced_card_risky),
            trailing = getString(R.string.advanced_card_risky_tag), trailingLevel = Level.BLOCK, rows = true,
        )
        CardRows.toggle(risky, getString(R.string.advanced_allow_lock_title), getString(R.string.advanced_allow_lock_summary), SessionPrefs.allowScreenLock(ctx)) { on ->
            SessionPrefs.setAllowScreenLock(ctx, on)
        }

        // Help.
        val help = Cards.section(column, getString(R.string.advanced_card_help), rows = true)
        Cards.row(help, OuiIcons.drawable.ic_oui_help_outline, getString(R.string.before_title), getString(R.string.advanced_before_summary), open(BeforeYouStartActivity::class.java))
        Cards.row(help, OuiIcons.drawable.ic_oui_info_outline, getString(R.string.about_title), getString(R.string.advanced_about_summary), open(AboutActivity::class.java))

        // The technical screens, behind one entry.
        val team = Cards.section(column, title = null, rows = true)
        Cards.row(team, OuiIcons.drawable.ic_oui_tool_outline, getString(R.string.team_title), getString(R.string.advanced_team_summary), open(TeamActivity::class.java))
    }

    private fun confirmRemove() {
        val ctx = context ?: return
        if (!RemoveJob.canStart()) {
            Toast.makeText(ctx, R.string.remove_busy, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.remove_confirm_title)
            .setMessage(R.string.remove_confirm_message)
            .setPositiveButton(R.string.remove_confirm_run) { _, _ -> RemoveJob.start(ctx) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Free space, and the old copies when they were read (the Free up space screen reads them as root). */
    private fun spaceSummary(): String {
        val free = CardRows.storage()?.first ?: return getString(R.string.advanced_space_summary)
        val old = OldStagesJob.state.stages
        val oldNames = StageCheck.state.result?.status?.oldStages
        return when {
            old != null && old.isEmpty() -> getString(R.string.advanced_space_none, Units.bytes(free))
            old != null -> getString(
                R.string.advanced_space_old,
                Units.bytes(free),
                resources.getQuantityString(R.plurals.advanced_space_copies, old.size, old.size),
                Units.bytes(old.sumOf { it.kib } * 1024),
            )
            !oldNames.isNullOrEmpty() ->
                getString(R.string.advanced_space_count, Units.bytes(free), resources.getQuantityString(R.plurals.advanced_space_copies, oldNames.size, oldNames.size))
            else -> getString(R.string.advanced_space_free, Units.bytes(free))
        }
    }

    /** When the last session ran and how it went (as Home's "Last time"), or that there was none. */
    private fun lastSummary(): String {
        val ctx = requireContext()
        val launch = SessionPrefs.lastLaunch(ctx) ?: return getString(R.string.advanced_last_none)
        val logs = LogJob.state
        if (logs.busy && !logs.recovering) return getString(R.string.home_row_last_reading)
        val system = Images.systemName(Images.byId(launch.imageId))
        val outcome = launch.outcome?.let { o -> OutcomeKind.entries.firstOrNull { it.name == o } }
        val date = CardRows.date(ctx, launch.wallMs)
        return when {
            outcome != null -> getString(R.string.advanced_last_outcome, date, outcome.title(system).trimEnd('.'))
            launch.dryRun -> getString(R.string.advanced_last_test, date)
            else -> getString(R.string.home_row_last_unread, date)
        }
    }
}
