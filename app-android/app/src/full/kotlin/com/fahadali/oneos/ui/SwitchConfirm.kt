package com.fahadali.oneos.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.fahadali.oneos.R
import com.fahadali.oneos.core.device.FactProbe
import com.fahadali.oneos.core.model.CarrierRules
import com.fahadali.oneos.core.model.GatePhase
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.SwitchGates
import com.fahadali.oneos.session.LaunchJob
import com.fahadali.oneos.session.Readiness
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck

/**
 * The Switch confirm (the redesign, step 4), a One UI bottom dialog from Home
 * that replaces the Switch screen for everyday use (SwitchSheet.dc.html):
 * what happens, how to come back as tiles (Back to One UI in the other system
 * only when its file set stages that app,
 * [com.fahadali.oneos.core.model.PackageSet.hasReturnApp]; the volume keys;
 * Power + Volume Down when stuck on the logo), and a box for every warning
 * not ticked before for this phone and system. Switch is enabled only when
 * every box is ticked. LaunchJob then checks everything again (the screen
 * lock read fresh, every gate, every tick including the remembered ones) and
 * root once more; a refusal comes back to Home in plain words.
 */
internal object SwitchConfirm {
    fun show(a: FragmentActivity) {
        val r = StageCheck.state.result?.takeIf { it.installd != null } ?: return Readiness.tick(a, force = true)
        val facts = FactProbe(a).refresh(r.facts)
        val report = StageCheck.gates(a, r, GatePhase.SWITCH, screenLock = facts.screenLockSet, facts = facts)
        // Home offers Switch only when nothing blocks; if that changed since, Home shows why.
        if (report.blocked) return
        val profile = report.profile ?: return
        val image = report.image ?: return
        val system = image.name
        val remembered = SessionPrefs.acks(a, profile.id, image.id)
        val asks = report.toTick.filter { it.tickKey !in remembered }
        val hideGms = SessionPrefs.hideGms(a) && image.gmsHideable
        val secIms = SessionPrefs.secIms(a)
        val edmStub = SessionPrefs.edmStub(a)

        val inflater = LayoutInflater.from(a)
        val view = inflater.inflate(R.layout.dialog_switch, null)
        val title = a.getString(R.string.confirm_switch_title, system)
        view.findViewById<TextView>(R.id.switch_title).text = title
        view.accessibilityPaneTitle = title
        view.findViewById<TextView>(R.id.switch_message).text = a.getString(R.string.confirm_switch_msg, system)
        val notes = buildList {
            // Calls over 2G or 3G (a carrier with neither needs the tick below instead).
            report.find(SwitchGates.ID_CARRIER)?.finding?.takeIf { it.level == Level.INFO && it.reason == CarrierRules.R_2G_3G }?.let {
                add(a.getString(R.string.confirm_calls_2g, system))
            }
            if (hideGms) add(a.getString(R.string.confirm_gms_hidden))
            if (!SessionPrefs.heatCap(a)) add(a.getString(R.string.confirm_heat_cap_off))
            if (secIms) add(a.getString(R.string.confirm_sec_ims, system))
            if (!edmStub) add(a.getString(R.string.confirm_edm_stub, system))
            // An image whose sessions present Google services runs its own setup wizard on a new stage:
            // sign-in and a screen lock are skipped there (docs/pixel-guest.md, "Setup").
            if (image.presentsGms) add(a.getString(R.string.confirm_setup_wizard, system))
        }
        view.findViewById<TextView>(R.id.switch_notes).apply {
            text = notes.joinToString("\n")
            visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        }

        // The ways back to One UI, always listed.
        val ways = buildList {
            if (report.set?.hasReturnApp == true) {
                add(Way(R.drawable.ic_home_back_app, a.getString(R.string.home_sheet_back_app), a.getString(R.string.home_sheet_back_app_line, system), primary = true))
            }
            add(Way(R.drawable.ic_home_back_keys, a.getString(R.string.home_sheet_back_keys), a.getString(R.string.home_sheet_back_keys_line)))
            add(Way(R.drawable.ic_home_back_power, a.getString(R.string.home_sheet_back_stuck), a.getString(R.string.home_sheet_back_stuck_line)))
        }
        view.findViewById<TextView>(R.id.switch_ways_title)
            .setText(if (ways.size == 3) R.string.home_sheet_ways_three else R.string.home_sheet_ways_two)
        val wayList = view.findViewById<LinearLayout>(R.id.switch_ways)
        for (w in ways) {
            val tile = inflater.inflate(R.layout.home_way_tile, wayList, false)
            tile.findViewById<View>(R.id.way_chip).setBackgroundResource(if (w.primary) R.drawable.home_way_chip_primary_bg else R.drawable.home_way_chip_bg)
            tile.findViewById<ImageView>(R.id.way_icon).apply {
                setImageResource(w.icon)
                imageTintList = ColorStateList.valueOf(a.getColor(if (w.primary) R.color.home_way_icon_on_primary else R.color.home_way_icon))
            }
            tile.findViewById<TextView>(R.id.way_title).text = w.title
            tile.findViewById<TextView>(R.id.way_line).text = w.line
            wayList.addView(tile)
        }

        // A box for every warning not ticked before (the One UI check box).
        val ackList = view.findViewById<LinearLayout>(R.id.switch_acks)
        val boxes = asks.map { g ->
            (inflater.inflate(R.layout.home_switch_ack, ackList, false) as CheckBox).apply {
                text = g.ack
                tag = g.tickKey
                ackList.addView(this)
            }
        }
        ackList.visibility = if (boxes.isEmpty()) View.GONE else View.VISIBLE

        val dialog = AlertDialog.Builder(a)
            .setView(view)
            .create()
        val go = view.findViewById<Button>(R.id.switch_go)
        fun update() {
            go.isEnabled = boxes.all { it.isChecked }
        }
        boxes.forEach { b -> b.setOnCheckedChangeListener { _, _ -> update() } }
        update()
        view.findViewById<Button>(R.id.switch_cancel).setOnClickListener { dialog.dismiss() }
        go.setOnClickListener {
            if (!boxes.all { it.isChecked }) return@setOnClickListener
            // A check started meanwhile (e.g. after the logs were collected): Switch
            // would do nothing, so keep the dialog and say why.
            if (Readiness.anyBusy()) {
                android.widget.Toast.makeText(a, a.getString(R.string.home_checking), android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ticked = remembered + boxes.mapNotNull { it.tag as? String }
            dialog.dismiss()
            LaunchJob.start(a, dryRun = false, ticked = ticked, hideGms = hideGms, secIms = secIms, edmStub = edmStub)
        }
        dialog.show()
    }

    /** One way back to One UI: its icon, what to do, and one line. The first (the app) has the blue chip. */
    private class Way(@DrawableRes val icon: Int, val title: String, val line: String, val primary: Boolean = false)
}
