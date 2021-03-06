package com.cornellappdev.coffee_chats_android

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cornellappdev.coffee_chats_android.models.ApiResponse
import com.cornellappdev.coffee_chats_android.models.Demographics
import com.cornellappdev.coffee_chats_android.models.User
import com.cornellappdev.coffee_chats_android.networking.*
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.fragment_create_profile.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList

class CreateProfileFragment : Fragment(), OnFilledOutObservable {

    // variables to keep track if editTexts are filled out
    private var majorFilled = false
    private var hometownFilled = false
    private var year = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initializing the class spinner
        val classArray = ArrayList<String>()
        val month = Calendar.getInstance().get(Calendar.MONTH)
        if (month >= 6) year++  // sets minimum graduation year to next year
        for (i in 0..4) {
            classArray.add("Class of " + (year + i))
        }
        val classArrayAdapter: ArrayAdapter<String> = ArrayAdapter(
            requireContext(),
            R.layout.profile_spinner_item,
            classArray
        )
        classSpinner.adapter = classArrayAdapter

        CoroutineScope(Dispatchers.Main).launch {
            val getUserEndpoint = Endpoint.getUser()
            val userTypeToken = object : TypeToken<ApiResponse<User>>() {}.type
            val user = withContext(Dispatchers.IO) {
                Request.makeRequest<ApiResponse<User>>(
                    getUserEndpoint.okHttpRequest(),
                    userTypeToken
                )
            }!!.data
            // pre-fills existing user profile information
            if (!user.hometown.isNullOrBlank()) {
                hometownEditText.setText(user.hometown)
                hometownFilled = true
            }
            if (!user.major.isNullOrBlank()) {
                majorACTV.setText(user.major)
                majorFilled = true
            }
            // nextButton is disabled until user has filled out all required info
            if (user.hometown.isNullOrBlank() || user.major.isNullOrBlank()) {
                callback!!.onSelectionEmpty()
            }

            if (!user.graduationYear.isNullOrBlank()) classSpinner.setSelection(
                Integer.parseInt(user.graduationYear) - year
            )

            // Initializing the major AutoCompleteTextView
            val getMajorsEndpoint = Endpoint.getAllMajors()
            val majorsTypeToken = object : TypeToken<ApiResponse<List<String>>>() {}.type
            val majors = withContext(Dispatchers.IO) {
                Request.makeRequest<ApiResponse<List<String>>>(
                    getMajorsEndpoint.okHttpRequest(),
                    majorsTypeToken
                )
            }!!.data
            val majorAdapter: ArrayAdapter<String> = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                majors
            )
            majorACTV.setAdapter(majorAdapter)

            // Initializing the pronoun spinner
            val pronoun = arrayOf("He/Him/His", "She/Her/Hers", "They/Them/Theirs")
            val pronounAdapter: ArrayAdapter<String> = ArrayAdapter(
                requireContext(),
                R.layout.profile_spinner_item,
                pronoun
            )
            pronounSpinner.adapter = pronounAdapter
            pronounSpinner.setSelection(
                when (user.pronouns) {
                    pronoun[0] -> 0
                    pronoun[1] -> 1
                    pronoun[2] -> 2
                    else -> 0
                }
            )
        }

        // monitor changes in major editText and enable button if both major and hometown != empty
        majorACTV.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                majorFilled = s.toString().trim().isNotEmpty()
                toggleNextButton()
            }
        })

        // monitor changes in major editText and enable button if both major and hometown != empty
        hometownEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                hometownFilled = s.toString().trim().isNotEmpty()
                toggleNextButton()
            }
        })
    }

    // checks whether the major and hometown fields are completed, enabling or disabling the next button accordingly
    private fun toggleNextButton() {
        if (majorFilled && hometownFilled) {
            callback!!.onFilledOut()
        } else {
            callback!!.onSelectionEmpty()
        }
    }

    private var callback: OnFilledOutListener? = null

    override fun setOnFilledOutListener(callback: OnFilledOutListener) {
        this.callback = callback
    }

    override fun saveInformation() {
        val pronouns = pronounSpinner.selectedItem as String
        val graduationYear = (classSpinner.selectedItemPosition + year).toString()
        val major = majorACTV.text.toString()
        val hometown = hometownEditText.text.toString()
        val demographics = Demographics(pronouns, graduationYear, major, hometown, "")

        val updateDemographicsEndpoint = Endpoint.updateDemographics(demographics)
        val typeToken = object : TypeToken<ApiResponse<Demographics>>() {}.type
        CoroutineScope(Dispatchers.IO).launch {
            val updateDemographicsResponse = Request.makeRequest<ApiResponse<Demographics>>(
                updateDemographicsEndpoint.okHttpRequest(),
                typeToken
            )
            if (updateDemographicsResponse == null || !updateDemographicsResponse.success) {
                Toast.makeText(requireContext(), "Failed to save information", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}