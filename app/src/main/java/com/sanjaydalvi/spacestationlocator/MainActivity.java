package com.sanjaydalvi.spacestationlocator;

/*
 * Copyright (C) 2016 Sanjay Dalvi
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;

import android.widget.TextView;
import android.widget.Toast;
import android.location.Address;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import android.location.Geocoder;
import android.util.Log;
import java.net.URI;
import org.apache.http.client.methods.HttpGet;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import org.json.JSONException;
import org.json.JSONObject;
import android.widget.Button;
import android.view.View;
import android.os.Handler;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    LatLng currentLocation;
    MapFragment mapFragment;
    Button refreshLocation;
    TextView locationTextView;
    Boolean recursiveLocate = false;
    int recursiveCheck_Interval = 2000;
    private Handler counterHandler;

    // ISS Location URL
    private static final String ISS_URL = "http://api.open-notify.org/iss-now.json";
    private static final String ACTION_FOR_INTENT_CALLBACK = "UNIQUE_KEY_TO_COMMUNICATE";
    ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize map fragment and refresh button
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        locationTextView = (TextView) findViewById(R.id.locationTextView);
        refreshLocation = (Button) findViewById(R.id.refreshButton);
        refreshLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // refresh button on click listener
                getISS_location();
            }
        });
        // initialize space ship when app starts
        getISS_location();
    }

    @Override
    public void onMapReady(GoogleMap map) {

        // clear previous marker
        map.clear();
        // Move the camera instantly to hamburg with a zoom of 5
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 3));
        // Zoom in, animating the camera.
        map.animateCamera(CameraUpdateFactory.zoomTo(3), 2000, null);
        // add space ship marker on map
        map.addMarker(new MarkerOptions().position(currentLocation).icon(BitmapDescriptorFactory.fromResource(R.drawable.spaceship2)));
        map.setOnMarkerClickListener(new OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker arg0) {
                // on marker click, call showLocationToast to display location
                showLocation();

                if(recursiveLocate == false){
                    // if show recursive location false, start showing location in intervals
                    recursiveLocate = true;
                    counterHandler = new Handler();
                    counterHandler.postDelayed(locationChanger, recursiveCheck_Interval);
                    Snackbar.make(getWindow().getDecorView().getRootView(), "Automatic Update : Started", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                } else {
                    // else stop looking up for location
                    recursiveLocate = false;
                    counterHandler.removeCallbacksAndMessages(null);
                    Snackbar.make(getWindow().getDecorView().getRootView(), "Automatic Update : Stopped", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }

                return true;
            }
        });
    }

    private void getISS_location() {
        // the request
        try {
            HttpGet httpGet = new HttpGet(new URI(ISS_URL));
            LoadISSLocation task = new LoadISSLocation(MainActivity.this, ACTION_FOR_INTENT_CALLBACK);
            task.execute(httpGet);
            // if you wish to show a progress dialog while to location is loaded
            //progress = ProgressDialog.show(MainActivity.this, "ISS Locator", "Fetching location data, please wait..", true);
        }
        catch (Exception e) {
            Log.e("Space Station Locator", e.getMessage());
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity.this.registerReceiver(receiver, new IntentFilter(ACTION_FOR_INTENT_CALLBACK));
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity.this.unregisterReceiver(receiver);
    }

    /**
     * Our Broadcast Receiver. We get notified that the data is ready
     */
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // clear the progress indicator
            if (progress != null) {
                progress.dismiss();
            }
            String response = intent.getStringExtra(LoadISSLocation.HTTP_RESPONSE);
            Log.i("Space Station Locator", "RESPONSE = " + response);

            try{
                // fetch latitude and longitude data from response received
                JSONObject jObject = new JSONObject(response);
                JSONObject iss_position = jObject.getJSONObject("iss_position");

                currentLocation = new LatLng(iss_position.getDouble("latitude"), iss_position.getDouble("longitude"));
                mapFragment.getMapAsync(MainActivity.this);
                showLocation();

            } catch(JSONException e){
                Log.e("Space Station Locator", e.getMessage());
            }
        }
    };

    public void showLocation(){
        // get latitude and longitude values and fetch location data such as city and country
        Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
        try{
            List<Address> addresses = geocoder.getFromLocation(currentLocation.latitude, currentLocation.longitude, 1);
            if (addresses.size() != 0) {
                String city = addresses.get(0).getLocality();
                //String state = addresses.get(0).getAdminArea();
                //String zip = addresses.get(0).getPostalCode();
                String country = addresses.get(0).getCountryName();
                // Toast.makeText(getApplicationContext(), "Current location : " + city + ", " + country, Toast.LENGTH_SHORT).show();
                locationTextView.setText("Current location : " + city + ", " + country);
                //Snackbar.make(getWindow().getDecorView().getRootView(), "Current location : " + city + ", " + country, Snackbar.LENGTH_LONG).setAction("Action", null).show();
            } else {
                // Toast.makeText(getApplicationContext(), "Current location : Ocean.", Toast.LENGTH_SHORT).show();
                locationTextView.setText("Current location : Over a water body.");
            }
        } catch (IOException e) {
            Log.d("space ship locator", e.getMessage());
        }
    }

    private Runnable locationChanger = new Runnable(){
        public void run() {
            // thread to fetch location data in defined intervals
            getISS_location();
            counterHandler.postDelayed(locationChanger, recursiveCheck_Interval);
        }
    };

}
