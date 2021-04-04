package com.cornellappdev.coffee_chats_android

import android.content.Intent
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.cornellappdev.coffee_chats_android.adapters.GroupInterestAdapter
import com.cornellappdev.coffee_chats_android.models.ApiResponse
import com.cornellappdev.coffee_chats_android.models.GroupOrInterest
import com.cornellappdev.coffee_chats_android.networking.*
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.fragment_create_profile.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class GroupInterestActivity : AppCompatActivity() {
    private val preferencesHelper: PreferencesHelper by lazy {
        PreferencesHelper(this)
    }

    enum class CurrentPage {
        INTERESTS,
        GROUPS
    }

    private var currentPage: CurrentPage = CurrentPage.INTERESTS
    lateinit var adapter: GroupInterestAdapter
    private lateinit var interestTitles: Array<String>
    private lateinit var interestSubtitles: Array<String>
    private lateinit var groupTitles: Array<String>

    private lateinit var userInterests: ArrayList<String>
    private lateinit var userGroups: ArrayList<String>

    var selectedColor = 0
    var unselectedColor = 0

    private lateinit var interests: Array<GroupOrInterest>
    private lateinit var groups: Array<GroupOrInterest>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_create_profile)

        interestTitles = resources.getStringArray(R.array.interest_titles)
        interestSubtitles = resources.getStringArray(R.array.interest_subtitles)

        selectedColor = ContextCompat.getColor(this, R.color.onboardingListSelected)
        unselectedColor = ContextCompat.getColor(this, R.color.onboarding_fields)

        if (intent.getIntExtra("page", 1) == 1) {
            currentPage = CurrentPage.INTERESTS
            createProfileFragment.setBackgroundResource(R.drawable.onboarding_background_2)
        } else {
            currentPage = CurrentPage.GROUPS
            createProfileFragment.setBackgroundResource(R.drawable.onboarding_background_3)
        }

        signup_next.setOnClickListener { onNextPage() }
        add_later.visibility = View.INVISIBLE
        add_later.setOnClickListener { onNextPage() }
        nav_button.setOnClickListener { onBackPage() }
        increaseHitArea(nav_button)

        // signup_next is disabled until user has chosen at least one interest
        signup_next.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            if (currentPage == CurrentPage.INTERESTS) {
                val getUserInterestsEndpoint = Endpoint.getUserInterests()
                val interestTypeToken = object : TypeToken<ApiResponse<List<String>>>() {}.type
                userInterests = withContext(Dispatchers.IO) {
                    Request.makeRequest<ApiResponse<List<String>>>(
                        getUserInterestsEndpoint.okHttpRequest(),
                        interestTypeToken
                    )
                }!!.data as ArrayList<String>
                interests = Array(interestTitles.size) {
                    GroupOrInterest()
                }
                for (i in interestTitles.indices) {
                    interests[i] = GroupOrInterest(
                        interestTitles[i],
                        if (i < interestSubtitles.size) interestSubtitles[i] else ""
                    )
                }
            } else {
                val getGroupsEndpoint = Endpoint.getAllGroups()
                val groupTypeToken = object : TypeToken<ApiResponse<List<String>>>() {}.type
                groupTitles = withContext(Dispatchers.IO) {
                    Request.makeRequest<ApiResponse<List<String>>>(
                        getGroupsEndpoint.okHttpRequest(),
                        groupTypeToken
                    )!!.data.toTypedArray()
                }
                val getUserGroupsEndpoint = Endpoint.getUserGroups()
                userGroups = withContext(Dispatchers.IO) {
                    Request.makeRequest<ApiResponse<List<String>>>(
                        getUserGroupsEndpoint.okHttpRequest(),
                        groupTypeToken
                    )
                }!!.data as ArrayList<String>
                groups = Array(groupTitles.size) {
                    GroupOrInterest()
                }
                for (i in groupTitles.indices) {
                    groups[i] = GroupOrInterest(groupTitles[i])
                }
            }
            updatePage()
            interests_or_groups.setOnItemClickListener { _, view, position, _ ->
                val selectedView = view.findViewById<ConstraintLayout>(R.id.group_or_interest_box)
                val selectedText =
                    selectedView.findViewById<TextView>(R.id.group_or_interest_text).text
                val drawableBox = selectedView.background
                val currObj =
                    if (currentPage == CurrentPage.INTERESTS) interests[position]
                    else groups[groupTitles.indexOf(selectedText)]
                currObj.toggleSelected()
                if (currObj.isSelected()) {
                    drawableBox.colorFilter =
                        BlendModeColorFilter(selectedColor, BlendMode.MULTIPLY)
                    if (currentPage == CurrentPage.INTERESTS) userInterests.add(currObj.getText())
                    else userGroups.add(currObj.getText())

                    if (!signup_next.isEnabled) {
                        signup_next.isEnabled = true
                    }
                } else {
                    drawableBox.colorFilter =
                        BlendModeColorFilter(unselectedColor, BlendMode.MULTIPLY)
                    if (currentPage == CurrentPage.INTERESTS) {
                        userInterests.remove(selectedText)
                        if (userInterests.isEmpty()) {
                            signup_next.isEnabled = false
                        }
                    } else {
                        userGroups.remove(selectedText)
                        if (userGroups.isEmpty()) {
                            signup_next.isEnabled = false
                        }
                    }
                }
            }
        }
    }

    private fun updatePage() {
        when (currentPage) {
            CurrentPage.INTERESTS -> {
                group_search.visibility = View.GONE
                adapter =
                    GroupInterestAdapter(
                        this, interests.toList(), false, GroupInterestAdapter.ItemColor.TOGGLE
                    )
                interests_or_groups.adapter = adapter

                signup_header.setText(R.string.interests_header)
                signup_next.setText(R.string.almost_there)
                add_later.visibility = View.INVISIBLE

                for (i in interestTitles.indices) {
                    if (userInterests.contains(interestTitles[i])) {
                        interests[i].setSelected()
                    }
                }

                signup_next.isEnabled = userInterests.isNotEmpty()
            }
            CurrentPage.GROUPS -> {
                group_search.visibility = View.VISIBLE
                group_search.queryHint = getString(R.string.groups_search_query_hint)

                val searchImgId =
                    resources.getIdentifier("android:id/search_button", null, null)
                val searchIcon: ImageView =
                    group_search.findViewById(searchImgId)
                searchIcon.setColorFilter(
                    ContextCompat.getColor(this, R.color.searchHint), PorterDuff.Mode.DARKEN
                )
                adapter =
                    GroupInterestAdapter(
                        this, groups.toList(), true, GroupInterestAdapter.ItemColor.TOGGLE
                    )

                interests_or_groups.adapter = adapter
                // initialize searchview
                group_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextChange(newText: String): Boolean {
                        var outputArr = groups
                        if (newText.isNotBlank()) {
                            val filtered = groups.filter {
                                it.getText().toLowerCase().contains(newText.toLowerCase())
                            }.toTypedArray()
                            outputArr = filtered
                        }
                        adapter =
                            GroupInterestAdapter(
                                applicationContext,
                                outputArr.toList(),
                                true,
                                GroupInterestAdapter.ItemColor.TOGGLE
                            )
                        interests_or_groups.adapter = adapter
                        for (i in groupTitles.indices) {
                            if (userGroups.contains(groupTitles[i])) {
                                groups[i].setSelected()
                            }
                        }
                        return true
                    }

                    override fun onQueryTextSubmit(query: String): Boolean {
                        return false
                    }
                })

                signup_header.setText(R.string.groups_header)
                signup_next.setText(R.string.get_started)
                add_later.visibility = View.VISIBLE

                for (i in groupTitles.indices) {
                    if (userGroups.contains(groupTitles[i])) {
                        groups[i].setSelected()
                    }
                }

                signup_next.isEnabled = userGroups.isNotEmpty()
            }
        }
    }

    private fun onNextPage() {
        // update user interests or groups in backend
        val items = when (currentPage) {
            CurrentPage.INTERESTS -> userInterests
            CurrentPage.GROUPS -> userGroups
        }
        updateInterestOrGroup(applicationContext, items, currentPage == CurrentPage.INTERESTS)
        if (currentPage == CurrentPage.INTERESTS) {
            val intent = Intent(this, GroupInterestActivity::class.java)
            intent.putExtra("page", 2)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else if (currentPage == CurrentPage.GROUPS) {
            // onboarding done, clear all activities on top of SchedulingActivity and launch SchedulingActivity
            preferencesHelper.hasOnboarded = true
            val intent = Intent(this, SchedulingActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }

    private fun onBackPage() {
        if (currentPage == CurrentPage.INTERESTS) {
            finish()
        } else if (currentPage == CurrentPage.GROUPS) {
            currentPage = CurrentPage.INTERESTS
            finish()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}