package dev.kwasi.echoservercomplete

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PermissionActivity : AppCompatActivity() {
    /// The [requestCode] variable acts as an identifier for the app that's requesting the permissions.
    private val requestCode = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_permission)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // If we do not have our permissions, we need to request them. Create an array of the permissions we want,
        // then send a request to the android OS
        if (!hasAllPermissions()){
            var perm = arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            if (SDK_INT >= 33){
                // Android 13 (API 33) requires the NEARBY_WIFI_DEVICES permission
                perm +=Manifest.permission.NEARBY_WIFI_DEVICES
            }

            ActivityCompat.requestPermissions(this, perm, requestCode)

        } else {
            navigateToNextPage()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()){
            navigateToNextPage()
        }
    }

    private fun hasAllPermissions():Boolean{
        var perm = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        if (SDK_INT >= 33){
            // If we're running on android SDK 33 or higher, we also need the NEARBY_WIFI_DEVICES permission
            perm = perm && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        }
        return perm
    }

    /// This function is called by the OS itself after the user interacts with the permissions popups.
    /// We need to iterate through each of the permissions we requested and make sure that ALL are granted.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when(requestCode){
            this.requestCode -> {
                if (hasAllPermissions()){
                    navigateToNextPage()
                }
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    private fun navigateToNextPage(){
        val i = Intent(this,CommunicationActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(i)
        finish()
    }


    fun goToSettings(view: View) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.setData(uri)
        startActivity(intent)
    }

}