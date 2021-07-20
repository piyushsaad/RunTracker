package com.example.runningtracker.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.runningtracker.R
import com.example.runningtracker.other.Constants.KEY_FIRST_TIME_TOGGLE
import com.example.runningtracker.other.Constants.KEY_NAME
import com.example.runningtracker.other.Constants.KEY_WEIGHT
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_setup.*
import javax.inject.Inject


@AndroidEntryPoint
class SetupFragment : Fragment(R.layout.fragment_setup) {

    @Inject
    lateinit var sharedPref: SharedPreferences

    @set:Inject             //we cant use lateinit with boolean and inject works only with lateinit so set inject
    var isFirstAppOpen = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //if false user has filled its details earlier
        //we will be navigated directly to run fragment
        if(!isFirstAppOpen) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.runSetup, true)    //if user press back from run fragment then we dont want him to reach
                                                            // setup fragment as user has already filled data
                                                            //remove run fragment from stack
                .build()

            findNavController().navigate(
                R.id.action_runSetup_to_runFragment,
                savedInstanceState,
                navOptions
            )
        }

        tvContinue.setOnClickListener {
            //check user entered all fields or not
            val success = writePersonalDataToSharedPref()
            if(success) {
                findNavController().navigate(R.id.action_runSetup_to_runFragment)
            } else {
                Snackbar.make(requireView(), "Please enter all the fields", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    //function to write name and weight to shared preference
    //return true if saved in shared pref else false
    private fun writePersonalDataToSharedPref(): Boolean {
        val name = etName.text.toString()
        val weight = etWeight.text.toString()
        if(name.isEmpty() || weight.isEmpty()) {
            return false
        }
        sharedPref.edit()
            .putString(KEY_NAME, name)
            .putFloat(KEY_WEIGHT, weight.toFloat())   //in xml we set that user can only enter decimal numbers
            .putBoolean(KEY_FIRST_TIME_TOGGLE, false)  //false will act as a flag //when user open app 2nd time false means we have entered our name and weight earlier
            .apply()         //apply is asyn ,commit is sync

        //change main toolbar text
        val toolbarText = "Let's go, $name!"
        requireActivity().tvToolbarTitle.text = toolbarText
        return true
    }

}