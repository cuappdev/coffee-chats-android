package com.cornellappdev.coffee_chats_android

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.cornellappdev.coffee_chats_android.models.ApiResponse
import com.cornellappdev.coffee_chats_android.models.User
import com.cornellappdev.coffee_chats_android.models.UserField
import com.cornellappdev.coffee_chats_android.networking.Endpoint
import com.cornellappdev.coffee_chats_android.networking.Request
import com.cornellappdev.coffee_chats_android.networking.getUser
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_onboarding.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingActivity : AppCompatActivity(), OnFilledOutListener,
    PromptsFragment.PromptsContainer {
    private var content = Content.CREATE_PROFILE
    private lateinit var user: User
    private val navigationList =
        listOf(
            Content.CREATE_PROFILE,
            Content.INTERESTS,
            Content.GROUPS,
            Content.PROMPTS,
            Content.GOALS,
            Content.TALKING_POINTS,
            Content.SOCIAL_MEDIA
        )
    private val addLaterPages =
        listOf(Content.GROUPS, Content.GOALS, Content.TALKING_POINTS, Content.SOCIAL_MEDIA)

    /** default bottom margin for next button */
    private var defaultButtonBottomMargin = 0

    enum class Content {
        CREATE_PROFILE,
        INTERESTS,
        GROUPS,
        PROMPTS,
        GOALS,
        TALKING_POINTS,
        SOCIAL_MEDIA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        CoroutineScope(Dispatchers.Main).launch {
            val getUserEndpoint = Endpoint.getUser()
            val userTypeToken = object : TypeToken<ApiResponse<User>>() {}.type
            user = withContext(Dispatchers.IO) {
                Request.makeRequest<ApiResponse<User>>(
                    getUserEndpoint.okHttpRequest(),
                    userTypeToken
                )
            }!!.data
            val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
            ft.replace(body_fragment.id, CreateProfileFragment(), content.name)
                .addToBackStack("ft")
                .commit()
            setUpCurrentPage()
        }
        back_button.setOnClickListener { onBackPressed() }
        increaseHitArea(back_button)
        onboarding_next.setOnClickListener { onNextPage(it) }
        add_later.setOnClickListener { onNextPage(it) }

        // shrink bottom margin of next button when the keyboard is displayed, and restore it to the original margin otherwise
        defaultButtonBottomMargin =
            (onboarding_next.layoutParams as ConstraintLayout.LayoutParams).bottomMargin
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            root.getWindowVisibleDisplayFrame(r)
            val heightDiff = root.rootView.height - r.height()
            onboarding_next.layoutParams =
                (onboarding_next.layoutParams as ConstraintLayout.LayoutParams).apply {
                    bottomMargin =
                        if (heightDiff > 0.25 * root.rootView.height) 0 else defaultButtonBottomMargin
                }
        }
    }

    override fun onAttachFragment(fragment: Fragment) {
        if (fragment is OnFilledOutObservable) {
            fragment.setOnFilledOutListener(this)
        }
        if (fragment is PromptsFragment) {
            fragment.setContainer(this)
        }
    }

    override fun onBackPressed() {
        // hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(back_button.applicationWindowToken, 0)
        if (content == navigationList.first()) {
            finish()
        } else {
            if (content == Content.PROMPTS) {
                val currFragment =
                    supportFragmentManager.findFragmentByTag(content.name) as PromptsFragment
                if (currFragment.content != PromptsFragment.Content.DISPLAY_RESPONSES) {
                    currFragment.onBackPressed()
                    return
                }
            }
            // skip prompts if needed
            content =
                if (DISPLAY_PROMPTS && content == navigationList[navigationList.indexOf(Content.PROMPTS) + 1]) {
                    navigationList[navigationList.indexOf(content) - 2]
                } else {
                    navigationList[navigationList.indexOf(content) - 1]
                }
            supportFragmentManager.popBackStack()
            setUpCurrentPage()
        }
    }

    override fun setActionButtonText(text: String) {
        onboarding_next.text = text
    }

    override fun setActionButtonVisibility(isVisible: Boolean) {
        onboarding_next.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    override fun setHeaderText(text: String) {
        onboarding_header.text = text
    }

    private fun onNextPage(view: View) {
        // hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.applicationWindowToken, 0)
        val currFragment =
            supportFragmentManager.findFragmentByTag(content.name) as OnFilledOutObservable
        if (currFragment is PromptsFragment && currFragment.content == PromptsFragment.Content.EDIT_RESPONSE) {
            currFragment.saveCurrentPromptResponse()
            return
        }
        currFragment.saveInformation()
        if (content != navigationList.last()) {
            content = navigationList[navigationList.indexOf(content) + 1]
            // skip prompts if not enabled
            if (content == Content.PROMPTS && !DISPLAY_PROMPTS) {
                content = navigationList[navigationList.indexOf(content) + 1]
            }
            val fragment: Fragment = when (content) {
                Content.CREATE_PROFILE -> CreateProfileFragment()
                Content.INTERESTS -> UserFieldFragment.newInstance(UserField.Category.INTEREST)
                Content.GROUPS -> UserFieldFragment.newInstance(UserField.Category.GROUP)
                Content.PROMPTS -> PromptsFragment()
                Content.GOALS -> UserFieldFragment.newInstance(UserField.Category.GOAL)
                Content.TALKING_POINTS -> UserFieldFragment.newInstance(UserField.Category.TALKING_POINT)
                Content.SOCIAL_MEDIA -> SocialMediaFragment()
            }
            val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
            ft.replace(body_fragment.id, fragment, content.name).addToBackStack("ft").commit()
            setUpCurrentPage()
        } else {
            // onboarding done, launch SchedulingActivity
            val intent = Intent(this, SchedulingActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }

    private fun setUpCurrentPage() {
        onboarding_header.text = when (content) {
            Content.CREATE_PROFILE -> getString(R.string.demographics_header, user.firstName)
            Content.INTERESTS -> getString(R.string.interests_header)
            Content.GROUPS -> getString(R.string.groups_header)
            Content.PROMPTS -> getString(R.string.prompts_header)
            Content.GOALS -> getString(R.string.goals_header)
            Content.TALKING_POINTS -> getString(R.string.talking_pointers_header)
            Content.SOCIAL_MEDIA -> getString(R.string.social_media_header)
        }
        back_button.visibility = if (content == Content.CREATE_PROFILE) View.GONE else View.VISIBLE
        add_later.visibility = if (content in addLaterPages) View.VISIBLE else View.GONE
        onboarding_next.text =
            if (content == navigationList.last()) getString(R.string.ready_for_pear) else getString(
                R.string.next
            )
        if (content == Content.PROMPTS) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }

    }

    override fun onFilledOut() {
        onboarding_next.isEnabled = true
    }

    override fun onSelectionEmpty() {
        onboarding_next.isEnabled = false
    }

    companion object {
        private const val DISPLAY_PROMPTS = true
    }
}