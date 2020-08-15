package app.opass.ccip.ui.schedule

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.observe
import app.opass.ccip.R
import app.opass.ccip.databinding.FragmentScheduleTabBinding
import app.opass.ccip.extension.*
import app.opass.ccip.model.ConfSchedule
import app.opass.ccip.network.CCIPClient
import app.opass.ccip.ui.MainActivity
import app.opass.ccip.util.AlarmUtil
import app.opass.ccip.util.JsonUtil
import app.opass.ccip.util.PreferenceUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.gson.internal.bind.util.ISO8601Utils
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class ScheduleTabFragment : Fragment(), CoroutineScope, MainActivity.BackPressAwareFragment {
    companion object {
        private val SDF_DATE = SimpleDateFormat("MM/dd", Locale.US)
        private const val EXTRA_URL = "EXTRA_URL"
        private const val EXTRA_REMOTE_USER_SCHEDULE_URL = "remoteUserScheduleUrl"
        fun newInstance(url: String, remoteUserScheduleUrl: String? = null) : ScheduleTabFragment = ScheduleTabFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_URL, url)
                putString(EXTRA_REMOTE_USER_SCHEDULE_URL, remoteUserScheduleUrl)
            }
        }
    }

    private var _binding: FragmentScheduleTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var mActivity: Activity
    private lateinit var tabLayout: TabLayout

    private var scheduleTabAdapter: ScheduleTabAdapter? = null
    private lateinit var mJob: Job
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.Main

    private val scheduleUrl by lazy { requireArguments().getString(EXTRA_URL)!! }
    private val userScheduleUrl by lazy { requireArguments().getString(EXTRA_REMOTE_USER_SCHEDULE_URL) }
    private val vm: ScheduleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentScheduleTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mActivity = requireActivity()
        tabLayout = mActivity.findViewById(R.id.tabs)
        mJob = Job()
        binding.swipeContainer.isEnabled = false
        setHasOptionsMenu(true)

        val sheetBehavior = BottomSheetBehavior.from(requireView().findViewById<View>(R.id.filterSheet))
        binding.fab.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        binding.fab.doOnApplyWindowInsets { v, insets, _, margin ->
            v.updateMargin(bottom = margin.bottom + insets.systemGestureInsets.bottom)
        }

        vm.isScheduleReady
            .distinctUntilChanged()
            .observe(viewLifecycleOwner) { isReady ->
                if (isReady) {
                    setupViewPager()
                }
                requireActivity().invalidateOptionsMenu()
            }
        vm.shouldShowFab.observe(viewLifecycleOwner) { show ->
            if (show) binding.fab.show()
            else binding.fab.hide()
        }

        binding.searchPanel.run {
            val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    translationY = -measuredHeight.toFloat()
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
            viewTreeObserver.addOnGlobalLayoutListener(listener)
        }
        binding.searchPanel.post {
            binding.searchPanel.alpha = 1F
            var firstRender = true

            val animator = binding.searchPanel.animate()
            animator.interpolator = DecelerateInterpolator()
            animator.duration = 150
            vm.shouldShowSearchPanel
                .distinctUntilChanged()
                .observe(viewLifecycleOwner) { shouldShow ->
                    if (!firstRender && shouldShow) binding.searchInput.run {
                        requestFocus()
                        postDelayed({ showIme() }, 200)
                    }
                    binding.searchPanel.run {
                        val targetY = if (shouldShow) 0F else -height.toFloat()
                        if (firstRender) {
                            translationY = targetY
                            firstRender = false
                        } else {
                            animator.translationY(targetY)
                        }
                    }
                }
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                vm.search(binding.searchInput.text.toString())
            }
        })
        binding.searchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                v.clearFocus()
                v.hideIme()
                true
            } else false
        }
        binding.searchClearBtn.setOnClickListener {
            binding.searchInput.run {
                if (text.isNotEmpty()) text.clear()
                else {
                    clearFocus()
                    hideIme()
                    vm.toggleSearchPanel(false)
                }
            }
        }

        launch {
            binding.swipeContainer.isRefreshing = true
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(scheduleUrl)
                    .build()
                client.newCall(request).asyncExecute().run {
                    if (isSuccessful) {
                        // Strings that end with \n may cause problem with SharedPreferences. Trim
                        // first for safety.
                        // See https://issuetracker.google.com/issues/37032278
                        val cached = PreferenceUtil.loadRawSchedule(mActivity).trim()
                        val new = withContext(Dispatchers.IO) { body!!.string().trim() }
                        // try to parse first
                        withContext(Dispatchers.Default) { JsonUtil.GSON.fromJson(new, ConfSchedule::class.java) }

                        if (cached != new) {
                            PreferenceUtil.saveSchedule(mActivity, new)
                            vm.reloadSchedule()
                        }
                    } else {
                        Snackbar.make(binding.root, R.string.cannot_load_schedule, Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.fab)
                            .show()
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (t: Throwable) {
                t.printStackTrace()
                Snackbar.make(binding.root, R.string.offline, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.fab)
                    .show()
            }
            userScheduleUrl?.let { baseUrl ->
                val token = PreferenceUtil.getToken(mActivity) ?: return@let
                try {
                    val client = CCIPClient.withBaseUrl(baseUrl)
                    val (registeredSessionIds) = client.userSchedule(token)
                    val oldIds = PreferenceUtil.getRegisteredIds(mActivity).orEmpty()
                    if (oldIds != registeredSessionIds) {
                        PreferenceUtil.saveRegisteredIds(mActivity, registeredSessionIds)
                        vm.reloadRegisteredSessions()
                    }
                    withContext(Dispatchers.Default) {
                        val sessions = PreferenceUtil.loadSchedule(mActivity)?.sessions ?: return@withContext

                        // Re-register every time lest the schedule fail to load, which prevents
                        // the alarm from being set.
                        val previouslyRegistered = sessions.filter { (id) -> oldIds.contains(id) }
                        val currentlyRegistered = sessions.filter { (id) -> registeredSessionIds.contains(id) }
                        previouslyRegistered.forEach { AlarmUtil.cancelSessionAlarm(mActivity, it) }
                        currentlyRegistered
                            .filter { session -> ISO8601Utils.parse(session.start!!, ParsePosition(0)).time > Date().time }
                            .forEach { AlarmUtil.setSessionAlarm(mActivity, it) }
                    }
                } catch (_: CancellationException) {
                    return@launch
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            binding.swipeContainer.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        tabLayout.setupWithViewPager(null)
        tabLayout.isGone = true
        super.onDestroy()
        mJob.cancel()
    }

    private fun setupViewPager() {
        val dates = vm.sessionsGroupedByDate.value!!.keys
        scheduleTabAdapter = ScheduleTabAdapter(childFragmentManager, dates.toList())
        binding.pager.adapter = scheduleTabAdapter

        val today = SDF_DATE.format(Date())
        val index = dates.indexOfFirst { it == today }
        if (index != -1) {
            binding.pager.currentItem = index
        }

        tabLayout.isGone = dates.size <= 1
        tabLayout.setupWithViewPager(binding.pager)
    }

    override fun onBackPressed(): Boolean {
        val sheetBehavior = BottomSheetBehavior.from(requireView().findViewById<ConstraintLayout>(R.id.filterSheet))
        if (sheetBehavior.state == BottomSheetBehavior.STATE_DRAGGING
            || sheetBehavior.state == BottomSheetBehavior.STATE_SETTLING) return true
        if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            sheetBehavior.state = if (sheetBehavior.skipCollapsed) BottomSheetBehavior.STATE_HIDDEN
                else BottomSheetBehavior.STATE_COLLAPSED
            return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        vm.reloadStars()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.schedule, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val ready = vm.isScheduleReady.value == true
        menu.findItem(R.id.search).isVisible = ready
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.search) {
            vm.toggleSearchPanel(true)
        }
        return super.onOptionsItemSelected(item)
    }
}
