package com.example.runningtracker

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.runningtracker.db.RunDAO
import com.example.runningtracker.other.Constants.ACTION_SHOW_TRACKING_FRAGMENT
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import javax.inject.Inject

//Once Hilt is set up in your Application class and an
// application-level component is available,
// Hilt can provide dependencies to other Android
// classes that have the @AndroidEntryPoint annotation:
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //1. if activity was destroyed and service is running and notification is clicked or if we removed app from recent
        navigateToTrackingFragmentIfNeeded(intent)  //if intent will be null then no action

        setSupportActionBar(toolbar)  //set out custom toolbar as main toolbar

        //when we click on item of bottom nav we will navigate to that fragment
        //we choose bootom nav item id same as that of fragment id in (check in nav_graph)
        bottomNavigationView.setupWithNavController(navHostFragment.findNavController())

        //we only want to show our bottom nav in run,statistics,settings
        navHostFragment.findNavController()
            .addOnDestinationChangedListener { _, destination, _ ->
                when(destination.id) {
                    R.id.runSettings, R.id.runFragment, R.id.runFragment ->
                        bottomNavigationView.visibility = View.VISIBLE
                    else -> bottomNavigationView.visibility = View.GONE
                }
            }

    }

    //2. activity was not destroyed or we minimize app
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        navigateToTrackingFragmentIfNeeded(intent)
    }

    /*utility function to navigate to tracking fragment on clicking on notification*/
    private fun navigateToTrackingFragmentIfNeeded(intent: Intent?) {
        if(intent?.action == ACTION_SHOW_TRACKING_FRAGMENT) {
            navHostFragment.findNavController().navigate(R.id.action_global_trackingFragment)
        }
    }
}