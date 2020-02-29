package com.cornellappdev.coffee_chats_android.models

import java.io.Serializable

class UserProfile(val userName: String, val userEmail: String): Serializable{
    // sign-in info
    var name = userName
    var email = userEmail

    // demographics
    var classOf = 0
    var major = ""
    var hometown = ""
    var pronoun = ""

    // interests and clubs
    var interests = arrayListOf<String>()
    var clubs = arrayListOf<String>()
}