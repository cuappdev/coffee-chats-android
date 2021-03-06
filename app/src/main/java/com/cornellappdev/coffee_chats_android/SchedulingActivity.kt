package com.cornellappdev.coffee_chats_android

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.cornellappdev.coffee_chats_android.models.*
import com.cornellappdev.coffee_chats_android.networking.Endpoint
import com.cornellappdev.coffee_chats_android.networking.Request
import com.cornellappdev.coffee_chats_android.networking.getUser
import com.cornellappdev.coffee_chats_android.networking.refreshSession
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.navigation.NavigationView
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_scheduling.*
import kotlinx.android.synthetic.main.fragment_create_profile.*
import kotlinx.android.synthetic.main.nav_header_profile.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class SchedulingActivity :
    AppCompatActivity(),
    OnFilledOutListener {
    private lateinit var nextButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var drawerLayout: DrawerLayout
    private var page = 0        // 0: no match; 1: time scheduling; 2: place scheduling
    private val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
    private val preferencesHelper: PreferencesHelper by lazy {
        PreferencesHelper(this)
    }
    private val noMatchTag = "NO_MATCH"
    private val scheduleTimeTag = "SCHEDULING_TIME"
    private val schedulePlaceTag = "SCHEDULING_PLACE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduling)

        drawerLayout = findViewById(R.id.drawer_layout)
        // Determine if the app should show scheduling page or sign-in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && preferencesHelper.accessToken != null) {
            val isAccessTokenExpired = Date() >= Date(preferencesHelper.expiresAt * 1000)
            CoroutineScope(Dispatchers.Main).launch {
                // refresh session if necessary
                if (isAccessTokenExpired) {
                    val refreshSessionEndpoint =
                        Endpoint.refreshSession(preferencesHelper.refreshToken!!)
                    val typeToken = object : TypeToken<ApiResponse<UserSession>>() {}.type
                    val response = withContext(Dispatchers.IO) {
                        Request.makeRequest<ApiResponse<UserSession>>(
                            refreshSessionEndpoint.okHttpRequest(),
                            typeToken
                        )
                    }!!
                    if (!response.success) {
                        signIn()
                        return@launch
                    }
                    val userSession = response.data
                    preferencesHelper.accessToken = userSession.accessToken
                    preferencesHelper.refreshToken = userSession.refreshToken
                    preferencesHelper.expiresAt = userSession.sessionExpiration.toLong()
                } else {
                    UserSession.currentSession = UserSession(
                        preferencesHelper.accessToken!!,
                        preferencesHelper.refreshToken!!,
                        preferencesHelper.expiresAt.toString(),
                        true
                    )
                }
                setUpDrawerLayout()
            }
        } else {
            // prompt user to log in
            signIn()
        }

        // add fragment to body_fragment
        ft.add(body_fragment.id, NoMatchFragment()).addToBackStack(noMatchTag)
        ft.commit()

        scheduling_finish.setOnClickListener { onNextPage() }

        // initialize more lateinit vars
        nextButton = findViewById(R.id.scheduling_finish)
        backButton = findViewById(R.id.nav_button)

        // set up drawer
        val content = findViewById<ConstraintLayout>(R.id.activity_main)
        drawerLayout.addDrawerListener(object : ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        ) {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)
                content.translationX = drawerView.width * slideOffset
            }
        })

        // set up navigation view
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.itemIconTintList = null
        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.close()
            val intent = Intent(this, ProfileSettingsActivity::class.java)
            val contentTag = when (menuItem.itemId) {
                R.id.nav_interests -> ProfileSettingsActivity.Content.EDIT_INTERESTS
                R.id.nav_groups -> ProfileSettingsActivity.Content.EDIT_GROUPS
                R.id.nav_settings -> ProfileSettingsActivity.Content.SETTINGS
                else -> null
            }
            contentTag?.let { intent.putExtra("content", contentTag) }
            when (menuItem.itemId) {
                R.id.nav_settings -> startActivityForResult(intent, SETTINGS_CODE)
                R.id.nav_interests, R.id.nav_groups -> startActivity(intent)
            }
            true
        }

        setUpCurrentPage()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setUpDrawerLayout()
    }

    private fun setUpDrawerLayout() {
        CoroutineScope(Dispatchers.Main).launch {
            val getUserEndpoint = Endpoint.getUser()
            val userTypeToken = object : TypeToken<ApiResponse<User>>() {}.type
            val user = withContext(Dispatchers.IO) {
                Request.makeRequest<ApiResponse<User>>(
                    getUserEndpoint.okHttpRequest(),
                    userTypeToken
                )
            }!!.data
            drawerLayout.user_name.text =
                getString(R.string.user_name, user.firstName, user.lastName)
            drawerLayout.user_major_year.text = getString(
                R.string.user_major_year,
                user.major,
                user.graduationYear?.substring(2)
            )
            drawerLayout.user_hometown.text = getString(R.string.user_hometown, user.hometown)
        }
    }

    private fun signIn() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
    }

    override fun onAttachFragment(fragment: Fragment) {
        if (fragment is OnFilledOutObservable) {
            fragment.setOnFilledOutListener(this)
        }
    }

    override fun onFilledOut() {
        nextButton.isEnabled = true
    }

    override fun onSelectionEmpty() {
        nextButton.isEnabled = false
    }

    override fun onBackPressed() {
        if (drawerLayout.isOpen) {
            drawerLayout.close()
        } else if (page > 0) {
            onBackPage()
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // user has logged out from Settings
        if (requestCode == SETTINGS_CODE && resultCode == Activity.RESULT_OK) {
            preferencesHelper.clearLogin()
            signIn()
        }
    }

    private fun onBackPage() {
        page--
        supportFragmentManager.popBackStack()
        setUpCurrentPage()
    }

    private fun onNextPage() {
        if (page == 2) {
            val locationFragment =
                supportFragmentManager.findFragmentByTag(schedulePlaceTag) as SchedulingPlaceFragment
            locationFragment.saveInformation()
            page = 0
            setUpCurrentPage()
            supportFragmentManager.popBackStack(noMatchTag, 0)
            return
        }
        if (page < 2) page++
        val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
        if (page == 1) {
            ft.replace(body_fragment.id, SchedulingTimeFragment(), scheduleTimeTag)
        } else {
            val timeFragment =
                supportFragmentManager.findFragmentByTag(scheduleTimeTag) as SchedulingTimeFragment
            timeFragment.saveInformation()
            ft.replace(body_fragment.id, SchedulingPlaceFragment(), schedulePlaceTag)
        }
        setUpCurrentPage()
        ft.addToBackStack("ft")
        ft.commit()
    }

    private fun setUpCurrentPage() {
        val displayMetrics = Resources.getSystem().displayMetrics
        if (page == 0) {
            backButton.background = ContextCompat.getDrawable(this, R.drawable.ic_sign_in_logo)
            backButton.layoutParams = backButton.layoutParams.apply {
                height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30f, displayMetrics)
                    .toInt()
                width = height
            }
            backButton.setOnClickListener {
                if (drawerLayout.isOpen) {
                    drawerLayout.close()
                } else {
                    drawerLayout.open()
                }
            }
            scheduling_header.text = getString(R.string.no_match_header)
            nextButton.text = getString(R.string.no_match_availability)
            nextButton.isEnabled = true
            nextButton.setPadding(100, 0, 100, 0)
        } else {
            backButton.background = ContextCompat.getDrawable(this, R.drawable.ic_back_carrot)
            backButton.layoutParams = backButton.layoutParams.apply {
                height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, displayMetrics)
                    .toInt()
                width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, displayMetrics)
                    .toInt()
            }
            increaseHitArea(nav_button)
            backButton.setOnClickListener {
                onBackPage()
            }
            nextButton.isEnabled = false
            nextButton.setPadding(180, 0, 180, 0)
            if (page == 1) {
                scheduling_header.text = getString(R.string.scheduling_time_header)
                nextButton.text = getString(R.string.scheduling_time_button)
            } else {
                scheduling_header.text = getString(R.string.scheduling_place_header)
                nextButton.text = getString(R.string.scheduling_place_button)
            }
        }
    }

    companion object {
        private const val SETTINGS_CODE = 10032
    }
}