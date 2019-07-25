package app.opass.ccip.activity

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.transaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.opass.ccip.R
import app.opass.ccip.adapter.DrawerMenuAdapter
import app.opass.ccip.adapter.IdentityAction
import app.opass.ccip.fragment.*
import app.opass.ccip.model.EventConfig
import app.opass.ccip.model.FeatureType
import app.opass.ccip.util.PreferenceUtil
import com.google.android.material.navigation.NavigationView
import com.google.zxing.integration.android.IntentIntegrator
import com.squareup.picasso.Picasso

private const val STATE_ACTION_BAR_TITLE = "ACTION_BAR_TITLE"
private const val STATE_SELECTED_FEATURE = "SELECTED_FEATURE"

class MainActivity : AppCompatActivity() {
    companion object {
        const val ARG_IS_FROM_NOTIFICATION = "isFromNotification"
    }

    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerMenu: RecyclerView
    private lateinit var drawerMenuAdapter: DrawerMenuAdapter
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var mActivity: Activity
    private lateinit var confLogoImageView: ImageView
    private lateinit var userTitleTextView: TextView
    private lateinit var userIdTextView: TextView

    private var selectedFeature: FeatureType = FeatureType.FAST_PASS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mActivity = this

        mDrawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        drawerMenu = findViewById(R.id.drawer_menu)
        confLogoImageView = navigationView.getHeaderView(0).findViewById(R.id.conf_logo)
        userTitleTextView = navigationView.getHeaderView(0).findViewById(R.id.user_title)
        userIdTextView = navigationView.getHeaderView(0).findViewById(R.id.user_id)

        val toolbar: Toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)
        PreferenceUtil.getCurrentEvent(this).run {
            if (eventId.isNotEmpty()) {
                setupDrawerContent(this)
            }
        }

        drawerToggle = ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close)

        val isFromNotification = intent.getBooleanExtra(ARG_IS_FROM_NOTIFICATION, false)
        val isLaunchedFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
        when {
            isFromNotification and !isLaunchedFromHistory -> getFeatureItemByFeatureType(FeatureType.ANNOUNCEMENT)?.let(::onDrawerItemClick)
            savedInstanceState != null -> {
                savedInstanceState.getString(STATE_ACTION_BAR_TITLE)?.let(::setTitle)
                savedInstanceState.getString(STATE_SELECTED_FEATURE)?.let {
                    selectedFeature = FeatureType.fromString(it)!!
                }
            }
            else -> getFeatureItemByFeatureType(FeatureType.FAST_PASS)?.let(::onDrawerItemClick)
        }

        if (PreferenceUtil.getCurrentEvent(applicationContext).displayName != null) {
            Picasso.get().load(PreferenceUtil.getCurrentEvent(mActivity).logoUrl).into(confLogoImageView)
        }

        // Beacon need location access
        if (!PreferenceUtil.isBeaconPermissionRequested(mActivity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.beacon_request_permission_title)
                    .setMessage(R.string.beacon_request_permission_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        requestPermissions(arrayOf(ACCESS_COARSE_LOCATION), 1)
                    }
                    .setNegativeButton(getString(R.string.no_thanks), null)
                    .setOnDismissListener {
                        PreferenceUtil.setBeaconPermissionRequested(mActivity)
                    }
                    .show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ACTION_BAR_TITLE, title as String)
        outState.putString(STATE_SELECTED_FEATURE, selectedFeature.type)
    }

    override fun onBackPressed() {
        when {
            mDrawerLayout.isDrawerOpen(GravityCompat.START) -> mDrawerLayout.closeDrawers()
            selectedFeature == FeatureType.FAST_PASS -> super.onBackPressed()
            else -> getFeatureItemByFeatureType(FeatureType.FAST_PASS)?.let(::onDrawerItemClick)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            PreferenceUtil.setIsNewToken(this, true)
            PreferenceUtil.setToken(this, result.contents)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(ARG_IS_FROM_NOTIFICATION, false) == true) {
            getFeatureItemByFeatureType(FeatureType.ANNOUNCEMENT)?.let(::onDrawerItemClick)
        }
    }

    override fun onResume() {
        super.onResume()
        val isSignedIn = PreferenceUtil.getToken(this) != null
        drawerMenuAdapter.isSignedIn = isSignedIn
    }

    fun setUserTitle(userTitle: String) {
        userTitleTextView.visibility = View.VISIBLE
        userTitleTextView.text = userTitle
    }

    fun setUserId(userId: String) {
        userIdTextView.text = userId
    }

    private fun getFeatureItemByFeatureType(type: FeatureType): DrawerMenuAdapter.FeatureItem? {
        if (type == FeatureType.WEBVIEW) throw IllegalArgumentException("shouldn't use webview at this time")
        val feature = PreferenceUtil.getCurrentEvent(this).features.firstOrNull { it.feature == type } ?: return null
        return DrawerMenuAdapter.FeatureItem.fromFeature(feature)
    }

    private fun setupDrawerContent(event: EventConfig) {
        val isSignedIn = PreferenceUtil.getToken(this) != null
        drawerMenuAdapter = DrawerMenuAdapter(this, event.features, ::onDrawerItemClick, isSignedIn)
        drawerMenu.adapter = drawerMenuAdapter
        drawerMenu.layoutManager = LinearLayoutManager(this)
        navigationView.getHeaderView(0).findViewById<RelativeLayout>(R.id.nav_header_info)
            .setOnClickListener { headerView ->
                drawerMenuAdapter.apply {
                    shouldShowIdentities = !shouldShowIdentities
                    headerView.findViewById<ImageView>(R.id.identities_shown_indicator)
                        .animate()
                        .rotation(if (shouldShowIdentities) 180F else 0F)
                }
            }
    }

    private fun onDrawerItemClick(item: Any) {
        when (item) {
            IdentityAction.SWITCH_EVENT -> {
                this.startActivity(Intent(this, EventActivity::class.java))
                finish()
            }
            IdentityAction.SIGN_IN -> {
                this.startActivity(Intent(this, AuthActivity::class.java))
            }
            is DrawerMenuAdapter.FeatureItem -> {
                if (!item.isEmbedded) return this.startActivity(Intent(Intent.ACTION_VIEW, item.url!!.toUri()))

                selectedFeature = item.type
                val fragment = when (item.type) {
                    FeatureType.FAST_PASS -> MainFragment()
                    FeatureType.SCHEDULE -> ScheduleTabFragment()
                    FeatureType.ANNOUNCEMENT -> AnnouncementFragment()
                    FeatureType.TICKET -> MyTicketFragment()
                    FeatureType.PUZZLE -> if (item.url != null) PuzzleFragment.newInstance(item.url) else return
                    else -> WebViewFragment.newInstance(
                        item.url!!
                            .replace("{token}", PreferenceUtil.getToken(mActivity).toString()),
                        item.shouldUseBuiltinZoomControls
                    )
                }
                supportFragmentManager.transaction { replace(R.id.content_frame, fragment) }
            }
        }
        title = when (item) {
            is DrawerMenuAdapter.FeatureItem -> item.displayText.findBestMatch(this)
            is IdentityAction -> title // noop
            else -> this.resources.getString(R.string.app_name)
        }
        mDrawerLayout.closeDrawers()
    }
}
