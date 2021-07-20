package com.example.runningtracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BaseApplication: Application()

//add in manifest as we extend it with application class