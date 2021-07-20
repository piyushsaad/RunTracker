package com.example.runningtracker.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController

import com.example.runningtracker.R
import com.example.runningtracker.ViewModel.MainViewModel
import com.example.runningtracker.db.Run
import com.example.runningtracker.other.Constants.ACTION_PAUSE_SERVICE
import com.example.runningtracker.other.Constants.ACTION_START_OR_RESUME_SERVICE
import com.example.runningtracker.other.Constants.ACTION_STOP_SERVICE
import com.example.runningtracker.other.Constants.MAP_ZOOM
import com.example.runningtracker.other.Constants.POLYLINE_COLOR
import com.example.runningtracker.other.Constants.POLYLINE_WIDTH
import com.example.runningtracker.other.TrackingUtility
import com.example.runningtracker.services.Polyline
import com.example.runningtracker.services.TrackingService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLngBounds

import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_tracking.*
import java.util.*
import javax.inject.Inject
import kotlin.math.round


@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_tracking) {

    private val viewModel: MainViewModel by viewModels()
 //   map fragment is map view inside a fragment
 //   in map view we can show maps
 //    here in our map we already use tracking fragment so no need for extra fragment
 //    fragment is use with map view because map view have their own life cycle
 //    and we are connecting map view life cycle with fragment life cycle
    private var map: GoogleMap? = null   //if we want to change something in map we will do it using map variable

    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()

    private var curTimeInMillis = 0L   //total time for calculations

    private var menu:Menu?=null

    @set:Inject     //float is premmitive data type
    var weight=80f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)    //we want to show menu only in tracking fragment
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView.onCreate(savedInstanceState) //tells what to do when we map is created

        //services can run in background
        /*to start location tracking services*/
        btnToggleRun.setOnClickListener {
            //sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
            toggleRun()
        }

        btnFinishRun.setOnClickListener{
            zoomToSeeWholeTrack()
            endRunAndSaveToDb()
        }

        mapView.getMapAsync {
            map = it
            addAllPolylines()  //only called when fragment is created or device is rotated
        }
        subscribeToObservers()
    }

    /*track data from tracking service we created*/
    private fun subscribeToObservers() {
        TrackingService.isTracking.observe(viewLifecycleOwner, Observer {
            updateTracking(it)
        })

        TrackingService.pathPoints.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyline()
            moveCameraToUser()
        })

        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, Observer {
            curTimeInMillis = it
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(curTimeInMillis, true)
            tvTimer.text = formattedTime
        })
    }

    /*create line from last 2 coordinates*/
    private fun addLatestPolyline() {
        if(pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng = pathPoints.last().last()
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)
            map?.addPolyline(polylineOptions)
        }
    }

    /*will form line using all of our coordinates saved in pathpoint if screen is rotated*/
    private fun addAllPolylines() {
        for(polyline in pathPoints) {
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)
            map?.addPolyline(polylineOptions)
        }
    }

    //this will move our camera icon in map to latest coordinate with zoom animation
    private fun moveCameraToUser() {
        if(pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    // this function will manage zoom according to our coordinates
    // so that full track is visible
    //will be call when we finish run
    private fun zoomToSeeWholeTrack() {
        //in bound we put our all coordinates
        val bounds = LatLngBounds.Builder()  //will generate bounds according to coordinates put in it
        for(polyline in pathPoints) {
            for(pos in polyline) {
                bounds.include(pos)        //put all coordinates to generate bound
            }
        }

        //move camera and not zoom animation cause we want to take screenshot imidiately
        // and animation will take time to zoom
        //generate map according to bounds
        map?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                mapView.width,
                mapView.height,
                (mapView.height * 0.05f).toInt()
            )
        )
    }

    //save run to our database
    //call after zoom to see whole track cause
    //we will save screenshot of whole track we run
    private fun endRunAndSaveToDb() {
        map?.snapshot { bmp ->
            var distanceInMeters = 0       //total distance sum of all polyline
            for(polyline in pathPoints) {
                distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()   //one by one adding all polyline
            }
            val avgSpeed = round((distanceInMeters / 1000f) / (curTimeInMillis / 1000f / 60 / 60) *10 )/10f //round will round off to only 1 decimal place
            val dateTimestamp = Calendar.getInstance().timeInMillis  //date in milli sec as room does not support calendar
            val caloriesBurned = ((distanceInMeters / 1000f) * weight).toInt()
            val run = Run(bmp, dateTimestamp, avgSpeed, distanceInMeters, curTimeInMillis, caloriesBurned)
            viewModel.insertRun(run)
            //It is Android material design UI component.
            // It is used to show popup message to user that requires some user action.
            // It can disappear automatically after set time or can be dismissed by user.
            Snackbar.make(
                requireActivity().findViewById(R.id.rootView),  //rootview of activity cause when we save our run we will be navigated back to run fragment
                                                                //and if we pass our tracking fragment and it dosent exist then our app will crash
                "Run saved successfully",
                Snackbar.LENGTH_LONG
            ).show()
            stopRun()
        }
    }

    //change the text of button accoring to state
    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking
        if(!isTracking && curTimeInMillis > 0L) {
            btnToggleRun.text = "Start"
            btnFinishRun.visibility = View.VISIBLE
        } else if(isTracking) {
            menu?.getItem(0)?.isVisible = true    //menu item should be visible when we are tracking
            btnToggleRun.text = "Stop"
            btnFinishRun.visibility = View.GONE
        }
    }

    // function to decide which command to send to service
    private fun toggleRun() {
        //currently tracking and click again on start
        if(isTracking) {
            menu?.getItem(0)?.isVisible = true    //menu item should be visible when we pause
            sendCommandToService(ACTION_PAUSE_SERVICE)
        } else{
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    /* attach menu with toolbar*/
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.toolbar_tracking_menu,menu)
        this.menu=menu
    }

    /* We can change visibility of our menu option here*/
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        //cancle icon or menu is visible only if we started tracking and have some data
        if(curTimeInMillis > 0L){
            this.menu?.getItem(0)?.isVisible = true
        }
    }

    /* actions on clicking on menu items */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.miCancelTracking -> {
                showCancelTrackingDialog()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /* For creating dialog box when we click on cancel menu*/
    private fun showCancelTrackingDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)  //style declared in themes
            .setTitle("Cancel the Run?")
            .setMessage("Are you sure to cancel the current run and delete all its data?")
            .setIcon(R.drawable.ic_delete)
            .setPositiveButton("Yes") { _, _ ->
                stopRun()
            }
            .setNegativeButton("No") { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
        dialog.show()
    }

    private fun stopRun() {
        tvTimer.text="00:00:00:00"
        sendCommandToService(ACTION_STOP_SERVICE)
        findNavController().navigate(R.id.action_runTracking_to_runFragment)
    }

    /* we pass intent to service based upon our action*/
    //like start or stop or pause navigation
    private fun sendCommandToService(action: String) =
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = action //action which we pass in form of string
            requireContext().startService(it)
        }


    override fun onResume() {
        super.onResume()
        mapView?.onResume()    //we link lifecycle of map with fragment
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
   //Any view you try to access in the fragment's onDestroy() is going to be null.
    //Don't forget that a fragment has 2 lifecycles: the lifecycle of its instance (onCreate -> onDestroy)
    // and the lifecycle its view (onViewCreated -> onDestroyView)
    //
    //The mapView lifecycle (just like any other view of the fragment) is bound to the lifecycle of the fragment's view.
    // Meaning you should call mapView.onCreate() in the fragment's onViewCreated() (which you already do)
    // & mapView.onDestroy() in the fragment's onDestroyView() (not in onDestroy())

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)  //save instance to cache memory
    }
}