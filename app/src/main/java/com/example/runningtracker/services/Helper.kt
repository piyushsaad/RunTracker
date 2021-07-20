/*package com.example.runningtracker.services

package com.example.runningtracker.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.runningtracker.MainActivity
import com.example.runningtracker.R
import com.example.runningtracker.other.Constants.ACTION_PAUSE_SERVICE
import com.example.runningtracker.other.Constants.ACTION_SHOW_TRACKING_FRAGMENT
import com.example.runningtracker.other.Constants.ACTION_START_OR_RESUME_SERVICE
import com.example.runningtracker.other.Constants.ACTION_STOP_SERVICE
import com.example.runningtracker.other.Constants.FASTEST_LOCATION_INTERVAL
import com.example.runningtracker.other.Constants.LOCATION_UPDATE_INTERVAL
import com.example.runningtracker.other.Constants.NOTIFICATION_CHANNEL_ID
import com.example.runningtracker.other.Constants.NOTIFICATION_CHANNEL_NAME
import com.example.runningtracker.other.Constants.NOTIFICATION_ID
import com.example.runningtracker.other.TrackingUtility
import com.google.android.gms.common.util.CollectionUtils.listOf
import com.google.android.gms.common.util.CollectionUtils.mutableSetOfWithSize
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber
import java.util.*


typealias Polyline1 = ArrayList<LatLng>  //latlng coordinates for location because earth is round
typealias Polylines1 = ArrayList<Polyline>
var index:Int=0
/*SERVICES*/
//Android service is a component that is used to perform operations on the background such as
// playing music, handle network transactions, interacting content providers etc.
// It doesn't has any UI (user interface).
// The service runs in the background indefinitely even if application is destroyed.


//we use lifecycle service because we need to
//specify live data object which life cycle state we are currenty in
class TrackingService1 : LifecycleService() {
//service will start only when we pass intent from tracking

    var isFirstRun = true
    //val mutableListOf : List<Int> = listOf()
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    companion object {
        val isTracking = MutableLiveData<Boolean>()   //true if service is running
        val pathPoints = MutableLiveData<Polylines>()  //list of list of coordinates//because if we stop service and then start then last and first
        //coordinates will not get join
    }

    private fun postInitialValues() {
        isTracking.postValue(false)

        val mutableList = ArrayList<Polyline>()

        pathPoints.postValue(mutableList)
    }

    override fun onCreate() {
        super.onCreate()
        postInitialValues()
        fusedLocationProviderClient = FusedLocationProviderClient(this)  //use for getting location

        //we can pass this only because we are in a lifecycle service
        isTracking.observe(this, Observer {
            updateLocationTracking(it)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when(it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    //Timber.d("Started or resumed service")
                    if(isFirstRun) {        //start service only when it is our first run
                        /* foreground service*/
                        startForegroundService()

                        isFirstRun = false
                    } else {
                        // Timber.d("Resuming service...")
                    }
                }
                ACTION_PAUSE_SERVICE -> {
                    //  Timber.d("Paused service")
                }
                ACTION_STOP_SERVICE -> {
                    // Timber.d("Stopped service")
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    //returns location call back and put it in locationCallback object
    @SuppressLint("MissingPermission")   //we use easy permission so android dosent know that permission is granted
    private fun updateLocationTracking(isTracking: Boolean) {
        if(isTracking) {
            if(TrackingUtility.hasLocationPermissions(this)) {
                val request = LocationRequest().apply {
                    interval = LOCATION_UPDATE_INTERVAL
                    fastestInterval = FASTEST_LOCATION_INTERVAL
                    priority = PRIORITY_HIGH_ACCURACY
                }
                fusedLocationProviderClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        } else {
            index++
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }


    /**/
    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            super.onLocationResult(result)
            if(isTracking.value!!) {
                result?.locations?.let { locations ->
                    for(location in locations) {
                        // addPathPoint(location)
                        Timber.d("NEW LOCATION: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }


    //add location to last // list[last][last]
    private fun addPathPoint(location: Location?) {
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)

            pathPoints.value?.apply {
                this[index].add(pos)
                Timber.d("NEW LOCATION: ${this[index]}")
                pathPoints.postValue(this)
            }
        }
    }

    //will add list[last][last+1]=null//and if list is empty then will initialize one
    private fun addEmptyPolyline() = pathPoints.value?.apply {
        //index=0     //livedata.value = container inside live data
        // val mutableList2 = ArrayList<Polyline>()

        val mutableList = ArrayList<LatLng>()
        //pathPoints.postValue(mutableList)
        add(mutableList)
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(ArrayList<Polyline>())   //for initial value

    /*background service can be killed by android but foreground service not*/
    //Foreground services are an advanced Android concept which allows you to display
    // notifications to your users when running long lived background tasks.
    // The notification acts like any other notification, however it cannot
    // be removed by the user and lives for the duration of the service.
    private fun startForegroundService() {
        addEmptyPolyline()
        isTracking.postValue(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)   //we dont want notification to disappear if user click on it
            .setOngoing(true)      //notification cant be swiped away
            .setSmallIcon(R.drawable.ic_directions_run_black_24dp)
            .setContentTitle("Running App")
            .setContentText("00:00:00")
            .setContentIntent(getMainActivityPendingIntent())

        startForeground(NOTIFICATION_ID, notificationBuilder.build())  //it will start this service as foreground service
    }

    private fun getMainActivityPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).also {
            it.action = ACTION_SHOW_TRACKING_FRAGMENT   //now main activity will se if this action is attached with it then
            //it will redirect us to tracking fragment using tracking action in nav graph
            //we created global action in nav graph to redirect from anywhere to tracking fragment
            //launch singletop=true because we dont want to create new instance of activity or our data and views will be reinitialized
        },
        FLAG_UPDATE_CURRENT       //  this flag will update previous intent by current if already present
        //   if we click on notification again
    )

    @RequiresApi(Build.VERSION_CODES.O)  //only oreo requires channel
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_LOW   //low because we dont want user to hear notification sound
            //as we will be changing notification frequently
        )
        notificationManager.createNotificationChannel(channel)
    }
}

*/


