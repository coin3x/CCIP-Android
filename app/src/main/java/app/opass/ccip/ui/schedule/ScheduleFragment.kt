package app.opass.ccip.ui.schedule

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.get
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.opass.ccip.R
import app.opass.ccip.extension.doOnApplyWindowInsets
import app.opass.ccip.extension.dpToPx
import app.opass.ccip.extension.updateMargin
import app.opass.ccip.model.Session
import app.opass.ccip.ui.sessiondetail.SessionDetailActivity
import app.opass.ccip.util.AlarmUtil
import app.opass.ccip.util.PreferenceUtil

class ScheduleFragment : Fragment() {
    companion object {
        private const val ARG_DATE = "ARG_DATE"

        fun newInstance(date: String) =
            ScheduleFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DATE, date)
                }
            }
    }

    private lateinit var adapter: ScheduleAdapter
    private lateinit var mActivity: Activity
    private lateinit var date: String
    private lateinit var vm: ScheduleViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        mActivity = requireActivity()
        date = requireArguments().getString(ARG_DATE)!!
        vm = ViewModelProvider(requireParentFragment()).get()

        val view = inflater.inflate(R.layout.fragment_schedule, container, false)
        val emptyView = view.findViewById<TextView>(R.id.emptyView)
        val scheduleView = view.findViewById<RecyclerView>(R.id.schedule)
        scheduleView.layoutManager = LinearLayoutManager(mActivity)
        val tagViewPool = RecyclerView.RecycledViewPool()
        adapter = ScheduleAdapter(
            mActivity,
            tagViewPool,
            ::onSessionClicked,
            ::onToggleStarState
        )
        scheduleView.adapter = adapter

        vm.groupedSessionsToShow.observe(viewLifecycleOwner) { sessionsMap ->
            val grouped = sessionsMap?.get(date)?.let(::toSessionsGroupedByTime).orEmpty()
            adapter.update(grouped)
            val shouldShowEmptyView = sessionsMap != null && grouped.isEmpty()
            val confSpansMultipleDay = sessionsMap?.size?.let { size -> size > 1 } ?: false
            emptyView.isVisible = shouldShowEmptyView
            emptyView.text = if (confSpansMultipleDay) {
                getString(R.string.no_matching_sessions_multiple_day)
            } else {
                getString(R.string.no_matching_sessions)
            }
        }
        vm.shouldShowSearchPanel
            .distinctUntilChanged()
            .observe(viewLifecycleOwner) {
                requireView().requestApplyInsets()
            }

        scheduleView.doOnApplyWindowInsets { v, insets, padding, _ ->
            val panelInset =
                if (vm.shouldShowSearchPanel.value == true) 56F.dpToPx(resources) else 0
            v.updatePadding(bottom = insets.systemGestureInsets.bottom + padding.bottom + panelInset)
        }
        emptyView.doOnApplyWindowInsets { v, insets, _, margin ->
            val panelInset =
                if (vm.shouldShowSearchPanel.value == true) 56F.dpToPx(resources) else 0
            v.updateMargin(bottom = insets.systemGestureInsets.bottom + margin.bottom + panelInset)
        }

        return view
    }

    private fun onSessionClicked(session: Session) {
        val intent = Intent(mActivity, SessionDetailActivity::class.java).apply {
            putExtra(SessionDetailActivity.INTENT_EXTRA_SESSION_ID, session.id)
        }
        startActivity(intent)
    }

    private fun onToggleStarState(session: Session) {
        if (vm.registeredSessionIds.value!!.contains(session.id)) return

        val sessionIds = PreferenceUtil.loadStarredIds(mActivity).toMutableList()
        val isAlreadyStarred = sessionIds.contains(session.id)
        if (isAlreadyStarred) {
            sessionIds.remove(session.id)
            AlarmUtil.cancelSessionAlarm(mActivity, session)
        } else {
            sessionIds.add(session.id)
            AlarmUtil.setSessionAlarm(mActivity, session)
        }
        PreferenceUtil.saveStarredIds(mActivity, sessionIds)
        vm.reloadStars()
    }

    private fun toSessionsGroupedByTime(sessions: List<SessionItem>): List<List<SessionItem>> {
        return sessions
            .filter { it.inner.start != null }
            .groupBy { it.inner.start }
            .values
            .sortedBy { it[0].inner.start }
            .map { it.sortedWith(Comparator { (session1, _), (session2, _) -> session1.room.id.compareTo(session2.room.id) }) }
    }
}
