package com.cs110.lit.adventour;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import com.github.clans.fab.FloatingActionButton;
import com.cs110.lit.adventour.model.ActiveTourCheckpoint;
import com.cs110.lit.adventour.model.Checkpoint;
import com.cs110.lit.adventour.model.Tour;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;

/**
 * Created by Izhikevich on 5/20/16.
 */
public class TakeTourActivity extends AppCompatActivity implements OnMapReadyCallback{

    // Request Constant.
    private static final int LOCATION_REQUEST_CODE = 0;
    // Distance change before refresh (meters).
    private static final float LOCATION_REFRESH_DISTANCE = 1.0f;

    private GoogleMap mMap;
    private LocationManager locationManager;

    //Make a list of active checkpoints
    private ArrayList<ActiveTourCheckpoint> activePointList = new ArrayList<>();
    private ArrayList<Checkpoint> checkpointList = new ArrayList<>();
    private ArrayList<Marker> markerList = new ArrayList<>();

    //To keep track of which checkpoint we are on
    private int upComingCheckpoint = 0;

    public static final String TOUR_ID = "tour_id";
    public static final String TOUR_TITLE = "tour_title";

    /* Tour Model Related */
    private int tourID;
    private String tourTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // launch map view
        setContentView(R.layout.activity_take_tour);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // for communication with the last/previews view
        Intent intent = getIntent();
        //get the tour id entered, -1 for bad input
        tourID = intent.getIntExtra(TOUR_ID, -1);
        tourTitle = intent.getStringExtra(TOUR_TITLE);
        getSupportActionBar().setTitle(tourTitle);

    }


    /* Render the Map*/
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setInfoWindowAdapter(new MyInfoWindowAdapter(this));

        //check fine
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED /*&& ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED*/) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            return;
        }
        //check coarse
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST_CODE);
            return;
        }

        mMap.setMyLocationEnabled(true);

        /* set up refresh and zoom buttons */
        setUpZoomButtonsOnMap();

        /* display the checkpoints for the particular tour */
        displayNearbyCheckpoints(mMap);

        /* set up FloatingActionButton clicks */
        actionsForFABs();

        ///// -------------- adding listener ---------------///
        // Acquire a reference to the system Location Manager
        //this will help us determine where to render the map near
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        //this will check whether we have reached a checkpoint
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                System.err.println("Location listener was used!!");

                checkUsersLocation(location);
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };


        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    private void setUpZoomButtonsOnMap() {
        ImageButton zoomIn = (ImageButton) findViewById(R.id.zoomIn);
        ImageButton zoomOut = (ImageButton) findViewById(R.id.zoomOut);

        assert zoomIn != null;
        zoomIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        assert zoomOut != null;
        zoomOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });
    }

    private void displayNearbyCheckpoints(final GoogleMap mMap) {

        DB.getTourById(tourID,  this, new DB.Callback<Tour>() {
            @Override
            public void onSuccess(Tour tour) {
                //fill up the checkpoint lists
                populateCheckpointLists(tour);

                // Create lines.
                PolylineOptions lineOptions = new PolylineOptions();

                boolean startPoint = false;
                boolean endPoint = false;

                // Display markers.
                for(ActiveTourCheckpoint points : activePointList) {

                    LatLng latLng = new LatLng(points.getLatitude(), points.getLongitude());
                    addMarkerAtMyLocation(latLng,points);
                    lineOptions.add(latLng);
                }

                // Draw the entire line.
                Polyline line = mMap.addPolyline(lineOptions);
            }

            @Override
            public void onFailure(Tour tour) {
                System.out.println("On failure happened when rendering the checkpoints on the" +
                        "take tour map\n");
            }
        });

    }

    /* Used to fill up the checkpoint lists with the tour's checkpoint */
    private void populateCheckpointLists(Tour tour) {

        //access the list of checkpoints
        checkpointList = tour.getListOfCheckpoints();

        // go through all the checkpoints and make them activeCheckpoints
        for(Checkpoint points : checkpointList) {

            //Make the checkpoint list a list of active checkpoints
            activePointList.add(new ActiveTourCheckpoint(points.getCheckpoint_id(),
                    points.getLatitude(), points.getLongitude(), points.getTour_id(),
                    points.getTitle(), points.getDescription(),
                    points.getPhoto(),
                    points.getOrder_num(),false, false, false,true));
            //bool startPoint, bool FinishPoint, bool Finished, bool Upcoming
        }

        //Now go back and set the first and last activeCheckpoints as the start and finish
        //point
        (activePointList.get(0)).setStartPoint(true);
        (activePointList.get(activePointList.size() - 1)).setFinishPoint(true);

    }

    /* Used to see if a user is near a checkpoint */
    private void checkUsersLocation(Location location) {
        //get the location's current coordinates
        double currentLat = location.getLatitude();
        double currentLong = location.getLongitude();

        // declare an array to store the distance in between
        float[] results = new float[4];

        //Check to see if we are near the next checkpoint
        //results[0] will store the distance between the two
        location.distanceBetween(currentLat, currentLong,
                (activePointList.get(upComingCheckpoint)).getLatitude(),
                (activePointList.get(upComingCheckpoint)).getLongitude(), results);

        //TODO: figure out which distance we need to be away from to get a notification
        if (results[0] < LOCATION_REFRESH_DISTANCE &&
                !((activePointList.get(upComingCheckpoint).getReachedPoint()))) {

            movetoNextCheckpoint();
            //reset the distance
            results[0] = 0;

        }
    }

    /* button listener for the  floating action bar */
    private void actionsForFABs() {
        FloatingActionButton skipFab = (FloatingActionButton) findViewById(R.id.skip_checkpoint_button);
        if (skipFab != null)
            skipFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    System.err.println("Button has been clicked!");
                    movetoNextCheckpoint();
                }
            });

        FloatingActionButton undoFab = (FloatingActionButton) findViewById(R.id.undo_skip_checkpoint);
        if (undoFab != null)
            undoFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    System.err.println("Button has been clicked!");
                    movetoBackACheckpoint();
                }
            });
    }

    private void movetoNextCheckpoint(){
        //notify the user they are approaching a checkpoint
        //TODO: Insert pop up fragment here to display checkpoint info

        //To avoid out of index errors
        if(upComingCheckpoint >= activePointList.size() -1){
            return;
        }

        System.out.println("Suh' dude you're near " +
                (activePointList.get(upComingCheckpoint)).getTitle() +
                ". It's getting LIT fam! ");


        //Only update the markers if they are not the starting or end ones
        if(!(upComingCheckpoint == 0 || upComingCheckpoint == activePointList.size()-1)) {
            //edit info for that checkpoint
            (activePointList.get(upComingCheckpoint)).setReachedPoint(true);
            (activePointList.get(upComingCheckpoint)).setuUpcomingPoint(false);

            // move the view camera to the next checkpoint
            LatLng upcoming_latLng = new LatLng((activePointList.get(upComingCheckpoint+1)).getLatitude(), (activePointList.get(upComingCheckpoint+1)).getLongitude());
            LatLng current_latLng = new LatLng((activePointList.get(upComingCheckpoint)).getLatitude(), (activePointList.get(upComingCheckpoint)).getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(upcoming_latLng, 16));

            //Rerender the map with the updated checkpoints (Whether visited or not)
            (markerList.get(upComingCheckpoint)).remove();
            mMap.addMarker(new MarkerOptions().position(current_latLng)
                    .title((activePointList.get(upComingCheckpoint)).getTitle())
                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher)));
        }

        //move onto the next checkpoint
        upComingCheckpoint++;

    }

    private void movetoBackACheckpoint(){
        //notify the user they are approaching a checkpoint
        //TODO: Insert pop up fragment here to display checkpoint info

        //To avoid out of index errors
        if(upComingCheckpoint < 1) return;

        //move back a checkpoint
        upComingCheckpoint--;

        //edit info for that checkpoint
        (activePointList.get(upComingCheckpoint)).setReachedPoint(false);
        //(activePointList.get(upComingCheckpoint)).setuUpcomingPoint(false);

        // move the view camera to the next checkpoint
        LatLng previous_latLng = new LatLng((activePointList.get(upComingCheckpoint)).getLatitude(), (activePointList.get(upComingCheckpoint)).getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(previous_latLng, 16));

        if(upComingCheckpoint < 1) return;
        //Rerender the map with the updated checkpoints (Whether visited or not)
        (markerList.get(upComingCheckpoint)).remove();

        mMap.addMarker(new MarkerOptions().position(previous_latLng)
                .title((activePointList.get(upComingCheckpoint)).getTitle())
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher_gray)));

    }


    /**
     * Simple helper function to set a marker at your current location.
     */
    private void addMarkerAtMyLocation(LatLng latLng, ActiveTourCheckpoint point) {

        Marker marker;

        if(latLng == null)
            return;

        if(point.getStartPoint()){
            marker = mMap.addMarker(new MarkerOptions().position(latLng)
                    .title(point.getTitle())
                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.start_flag)));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
        }
        else if(point.getFinishPoint()){
            marker = mMap.addMarker(new MarkerOptions().position(latLng)
                    .title(point.getTitle())
                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.end_flag)));
        }

        else if(point.getReachedPoint()){
            marker = mMap.addMarker(new MarkerOptions().position(latLng)
                    .title(point.getTitle())
                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher)));
        }
        else { //else this must be an unvisited checkpoint
            marker = mMap.addMarker(new MarkerOptions().position(latLng)
                    .title(point.getTitle())
                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher_gray)));
        }

        //Now add this marker into a marker array so we can later update it
        markerList.add(marker);

        // zoom in to the current location in camera view
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13));
        System.err.println("The checkpoint you just added is at " + latLng);
    }

}
