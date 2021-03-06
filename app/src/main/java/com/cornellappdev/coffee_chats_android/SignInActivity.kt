package com.cornellappdev.coffee_chats_android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cornellappdev.coffee_chats_android.models.ApiResponse
import com.cornellappdev.coffee_chats_android.models.User
import com.cornellappdev.coffee_chats_android.models.UserSession
import com.cornellappdev.coffee_chats_android.networking.Endpoint
import com.cornellappdev.coffee_chats_android.networking.Request
import com.cornellappdev.coffee_chats_android.networking.authenticateUser
import com.cornellappdev.coffee_chats_android.networking.getUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SignInActivity : AppCompatActivity() {

    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 10032

    private val preferencesHelper: PreferencesHelper by lazy {
        PreferencesHelper(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso = Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(BuildConfig.web_client_id)
            .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        val signInButton = findViewById<Button>(R.id.sign_in_button)
        val dr = ContextCompat.getDrawable(this, R.drawable.google_icon)
        dr!!.setBounds(0, 0, 60, 60) //Left,Top,Right,Bottom
        signInButton.setCompoundDrawables(dr, null, null, null)
        signInButton.compoundDrawablePadding = 30
        signInButton.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        // The code below is for testing. Use it instead if you can't sign in
//        val personName: String? = "Preston"
//        val personEmail: String? = "pwr36@cornell.edu"
//        if (personName != null && personEmail != null) {
//            var profile = UserProfile(personName, personEmail)
//            InternalStorage.writeObject(this, "profile", profile as Object)
//        }
//
//        val intent = Intent(this, CreateProfileActivity::class.java) // added to bypass sign in
//        startActivity(intent)

        val signInIntent: Intent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) { // The Task returned from this call is always completed, no need to attach
// a listener.
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            if (account != null) {
                val personName: String? = account.givenName
                val personEmail: String? = account.email
                if (personName != null && personEmail != null) {
                    val index = personEmail.indexOf('@')
                    val domain: String? =
                        if (index == -1) null else personEmail.substring(index + 1)
                    if (domain != null && domain == "cornell.edu") {
                        // authenticate with backend
                        CoroutineScope(Dispatchers.Main).launch {
                            val userAuthEndpoint = Endpoint.authenticateUser(account.idToken!!)
                            val typeToken = object : TypeToken<ApiResponse<UserSession>>() {}.type
                            val userSession = withContext(Dispatchers.IO) {
                                Request.makeRequest<ApiResponse<UserSession>>(
                                    userAuthEndpoint.okHttpRequest(),
                                    typeToken
                                )
                            }!!.data
                            preferencesHelper.accessToken = userSession.accessToken
                            preferencesHelper.refreshToken = userSession.refreshToken
                            preferencesHelper.expiresAt = userSession.sessionExpiration.toLong()
                            UserSession.currentSession = userSession
                            val getUserEndpoint = Endpoint.getUser()
                            val userTypeToken = object : TypeToken<ApiResponse<User>>() {}.type
                            val user = withContext(Dispatchers.IO) {
                                Request.makeRequest<ApiResponse<User>>(
                                    getUserEndpoint.okHttpRequest(),
                                    userTypeToken
                                )
                            }!!.data
                            val intent = if (user.didOnboard) {
                                Intent(applicationContext, SchedulingActivity::class.java)
                            } else {
                                Intent(applicationContext, OnboardingActivity::class.java)
                            }
                            startActivity(intent)
                        }
                    } else {
                        Toast.makeText(
                            applicationContext,
                            "Please sign in using a Cornell account",
                            Toast.LENGTH_LONG
                        ).show()
                        signOut()
                    }
                }
            }
        } catch (e: ApiException) { // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w("account error", "signInResult:failed code=" + e.statusCode)
            Toast.makeText(applicationContext, "Sign-in failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun signOut() {
        mGoogleSignInClient.signOut()
            .addOnCompleteListener(this) {
                // ...
            }
    }

    override fun onBackPressed() {
        // Pressing the back button goes to the home screen
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        super.onBackPressed()
    }
}