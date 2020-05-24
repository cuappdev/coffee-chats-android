package com.cornellappdev.coffee_chats_android

import android.app.PendingIntent.getActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.cornellappdev.coffee_chats_android.models.InternalStorage
import com.cornellappdev.coffee_chats_android.models.UserProfile
import kotlinx.android.synthetic.main.activity_scheduling.*


class SchedulingActivity:
    AppCompatActivity(),
    SchedulingTimeFragment.OnFilledOutListener,
    SchedulingPlaceFragment.OnFilledOutListener {
    lateinit var nextButton: Button
    lateinit var backButton: ImageButton
    lateinit var profile: UserProfile
    var page = 0        // 0: no match; 1: time scheduling; 2: place scheduling
    private val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduling)

        try {
            profile = InternalStorage.readObject(this, "profile") as UserProfile
        } catch (e: Exception) {
            // no profile, meaning this app is used for the first time
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // add fragment to body_fragment
        ft.add(body_fragment.id, NoMatchFragment())
        ft.commit()

        back_button.setOnClickListener { onBackPage() }
        back_button.visibility = View.GONE

        scheduling_finish.setOnClickListener {onNextPage()}

        nextButton = findViewById(R.id.scheduling_finish)
        backButton = findViewById(R.id.back_button)
    }

    override fun onAttachFragment(fragment: Fragment) {
        if (fragment is SchedulingTimeFragment) {
            fragment.setOnFilledOutListener(this)
        } else if (fragment is SchedulingPlaceFragment) {
            fragment.setOnFilledOutListener(this)
        }
    }

    override fun onFilledOut() {
        nextButton.isEnabled = true
    }

    override fun onSelectionEmpty() {
        nextButton.isEnabled = false
    }

    private fun onBackPage() {
        page--
        supportFragmentManager.popBackStack()
        if (page == 0) {
            backButton.visibility = View.GONE
            scheduling_header.text = getString(R.string.no_match_header)
            nextButton.text = getString(R.string.no_match_availability)
            nextButton.isEnabled = true
            nextButton.setPadding(100,0,100,0)
        } else if (page == 1) {
            scheduling_header.text = getString(R.string.scheduling_time_header)
            scheduling_finish.isEnabled = false
        }
    }

    private fun onNextPage() {
        if (page == 2) return
        back_button.visibility = View.VISIBLE
        if (page < 2) page++
        val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
        nextButton.text = getString(R.string.scheduling_finish)
        nextButton.isEnabled = false
        nextButton.setPadding(180, 0, 180, 0)
        if (page == 1) {
            scheduling_header.text = getString(R.string.scheduling_time_header)
            ft.replace(body_fragment.id, SchedulingTimeFragment())
        } else {
            scheduling_header.text = getString(R.string.scheduling_place_header)
            ft.replace(body_fragment.id, SchedulingPlaceFragment())
        }
        ft.addToBackStack("ft")
        ft.commit()
    }
}