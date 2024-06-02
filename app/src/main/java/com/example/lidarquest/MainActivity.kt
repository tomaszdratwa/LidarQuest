package com.example.lidarquest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.lidarquest.ui.theme.LidarQuestTheme
import com.google.android.gms.location.*
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

import android.content.Context
import androidx.compose.ui.Alignment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import java.io.File
import java.io.FileWriter
import java.io.IOException

@Entity
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

@Dao
interface LocationDao {
    @Insert
    fun insert(location: LocationEntity)

    @Query("SELECT * FROM LocationEntity")
    fun getAllLocations(): List<LocationEntity>
}


@Database(entities = [LocationEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "location_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}


class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationRequest: LocationRequest? = null
    private var interval: Int = 60 // Default interval
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val intervalString = sharedPreferences.getString("location_interval", "60")
        interval = intervalString?.toIntOrNull() ?: 60

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Setup location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val locationEntity = LocationEntity(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis()
                    )
                    Thread {
                        AppDatabase.getDatabase(this@MainActivity).locationDao().insert(locationEntity)
                    }.start()
                }

            }
        }

        setContent {
            LidarQuestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var isTracking by remember { mutableStateOf(false) }
                    var buttonText by remember { mutableStateOf("Start") }

                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = {
                            isTracking = !isTracking
                            buttonText = if (isTracking) "Stop" else "Start"
                            if (isTracking) startLocationUpdates() else stopLocationUpdates()
                        }) {
                            Text(text = buttonText)
                        }
                    }
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        locationRequest = LocationRequest.create().apply {
            interval = (interval * 1000).toLong()
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationRequest?.let {
            fusedLocationClient.requestLocationUpdates(
                it,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startLocationUpdates()
        }
    }

    private fun saveLocation(latitude: Double, longitude: Double) {
        // Implement database save logic
    }

    private fun exportToKML() {
        Thread {
            val locations = AppDatabase.getDatabase(this).locationDao().getAllLocations()
            try {
                val file = File(getExternalFilesDir(null), "locations.kml")
                val writer = FileWriter(file)

                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
                writer.write("<Document>\n")
                writer.write("<name>Locations</name>\n")
                writer.write("<Placemark>\n")
                writer.write("<LineString>\n")
                writer.write("<coordinates>\n")

                for (location in locations) {
                    writer.write("${location.longitude},${location.latitude},0\n")
                }

                writer.write("</coordinates>\n")
                writer.write("</LineString>\n")
                writer.write("</Placemark>\n")
                writer.write("</Document>\n")
                writer.write("</kml>\n")

                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

}
