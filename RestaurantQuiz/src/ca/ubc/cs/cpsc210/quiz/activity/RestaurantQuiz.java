package ca.ubc.cs.cpsc210.quiz.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import ca.ubc.cs.cpsc210.quiz.google.GoogleAPI;
import ca.ubc.cs.cpsc210.quiz.google.GoogleDirectionsParser;
import ca.ubc.cs.cpsc210.quiz.model.*;
import ca.ubc.cs.cpsc210.quiz.util.Constants;
import ca.ubc.cs.cpsc210.quiz.yelp.YelpAPI;
import ca.ubc.cs.cpsc210.quiz.yelp.YelpDataParser;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;

import java.io.IOException;
import java.util.*;

import static java.util.Collections.shuffle;


public class RestaurantQuiz extends Activity {
    // Constants
    private static final String TAG = "RestaurantQuiz";
    private static final int INIT_POINTS = 1000;
    private static final int DECREMENT = 5;
    private static final double GUESS_COST = 0.05;  // lose GUESS_COST% points on each guess

    //Game related
    private final Handler mainThreadHandler = new Handler();
    private Random rand;
    private GoogleMap mMap;
    private YelpAPI mYelpApi;
    private ScoreTracker scoreTracker;

    private Geocoder geocoder;
    private Spinner mSpinner;
    private int points;
    private TextView pointsView;
    private TextView distanceView;
    private TextView totalPointsView;
    private List<Restaurant> recentRestaurants;

    private Button mPlayButton;
    private ImageView yelpView;
    private TextView mRestDialog;
    private Timer myTimer;
    private View initialDialog;
    private AutoCompleteTextView mAutocompleteLocation;
    private String mRestDialogQuestion;
    private Menu menu;

    private MarkerManager markerManager;
    private LatLng fromLL;
    private LatLng toLL;
    private double totalDistance;
    private Restaurant currentRestaurant;
    private int totalPoints;
    private int numAttempts;
    private boolean hasRestaurants;


    /// ***********************************************************************************************************
    ///
    /// Android lifecycle methods
    ///
    /// ***********************************************************************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_chooser);
        mYelpApi = YelpAPI.getInstance(this);
        rand = new Random();
        Log.d(TAG, "Done initial setup");

        mSpinner = (Spinner) findViewById(R.id.game_spinner);
        mPlayButton = (Button) findViewById(R.id.play_game_button);
        mAutocompleteLocation = (AutoCompleteTextView) findViewById(R.id.find_location_dropdown);
        yelpView = (ImageView) findViewById(R.id.yelp_image);
        totalDistance = 0;
        totalPoints = 0;
        numAttempts = 0;
        hasRestaurants = false;

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setHomeButtonEnabled(false);
        }

        setUpMap();
        setUpYelpButton();
        setUpLocationTextAndPlayButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scoreTracker = ScoreTracker.getInstance(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scoreTracker.saveScores();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.category_chooser, menu);
        this.menu = menu;
        menu.findItem(R.id.category_choice).setEnabled(hasRestaurants);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        stopRound(-1);
        switch (item.getItemId()) {

            case R.id.category_choice:
                handleCategoryChoice();
                return true;

            case R.id.geographical_choice:
                handleGeographicalChoice();
                return true;

            case R.id.recent_scores:
                handleRecentScores();
                return true;

            case R.id.about:
                handleAbout();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void handleRecentScores() {
        Log.d(TAG, "show recent scores");
        mSpinner.setVisibility(View.GONE);
        mAutocompleteLocation.setVisibility(View.GONE);
        mPlayButton.setVisibility(View.GONE);
        initialDialog.setVisibility(View.VISIBLE);
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(this);
        dialogBldr.setAdapter(new ScoreListAdapter(this, scoreTracker), null);
        dialogBldr.setTitle(R.string.recent_scores);
        dialogBldr.setNeutralButton(R.string.ok, null);
        dialogBldr.create().show();
    }

    private void handleCategoryChoice() {
        Log.d(TAG, "start up category round");
        mAutocompleteLocation.setVisibility(View.GONE);
        mSpinner.setVisibility(View.VISIBLE);
        initialDialog.setVisibility(View.GONE);
        mPlayButton.setVisibility(View.VISIBLE);
        setUpCategorySpinner();
        setUpPlayButtonListener();
    }

    private void handleGeographicalChoice() {
        Log.d(TAG, "start up geographical round");
        mSpinner.setVisibility(View.GONE);
        mAutocompleteLocation.setVisibility(View.VISIBLE);
        mAutocompleteLocation.setText("");
        mAutocompleteLocation.requestFocus();
        initialDialog.setVisibility(View.GONE);
        mPlayButton.setVisibility(View.VISIBLE);
        setUpLocationTextAndPlayButton();
    }

    private void handleAbout() {
        Log.d(TAG, "showing about dialog");
        mSpinner.setVisibility(View.GONE);
        mAutocompleteLocation.setVisibility(View.GONE);
        mPlayButton.setVisibility(View.GONE);
        initialDialog.setVisibility(View.VISIBLE);
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(this);
        dialogBldr.setTitle(R.string.about);
        dialogBldr.setView(getLayoutInflater().inflate(R.layout.about_dialog_layout, null));
        dialogBldr.setNeutralButton(R.string.ok, null);
        dialogBldr.create().show();
    }


    /// ***********************************************************************************************************
    ///
    /// UI (Re-) Initialization
    ///
    /// ***********************************************************************************************************

    /**
     * Set up Yelp icon button to line to yelp website
     * (defaults to Vancouver locations)
     */
    private void setUpYelpButton() {
        yelpView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://www.yelp.com/search?find_loc=Vancouver+BC"));
                startActivity(intent);
            }
        });
    }

    /**
     * Set up the map and associated UI components
     */
    private void setUpMap() {
        Log.d(TAG, "setting up map");
        if (initializeMapObject()) {
            Log.d(TAG, "map is set up");
            mMap.getUiSettings().setAllGesturesEnabled(true);
            mMap.getUiSettings().setScrollGesturesEnabled(true);
            markerManager = new MarkerManager(mMap);
        }

        if (Geocoder.isPresent()) {
            geocoder = new Geocoder(this);
            Log.d(TAG, "geocoder initialized");
        }
        else {
            Toast.makeText(this, "Sorry you don't have a geocoder initialized - game ending", Toast.LENGTH_LONG).show();
            Log.d(TAG, "no geocoder initialized");
            System.exit(0);
        }

        findUIComponents();
        Log.d(TAG, "Done setting up Map");
    }

    /**
     * Lookup UI components
     */
    private void findUIComponents() {
        pointsView = (TextView) findViewById(R.id.points_text_view);
        distanceView = (TextView) findViewById(R.id.distance_text_view);
        totalPointsView = (TextView) findViewById(R.id.total_points_text_view);
        totalPointsView.setText("Points: \n" + totalPoints);

        initialDialog = findViewById(R.id.get_started_dialog);
        mPlayButton = (Button) findViewById(R.id.play_game_button);
        mSpinner = (Spinner) findViewById(R.id.game_spinner);
    }

    /**
     * Initialize map and check that Google Play is installed on device
     * @return  true if map initialized and Google Play installed; false otherwise
     */
    private boolean initializeMapObject() {
        if (!checkGooglePlayIsInstalledAndUpToDate())
            return false;

        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        // centre map on lat = 0.0, lng = -90.0
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(0.0, -90.0));
        mMap.animateCamera(cameraUpdate);

        return true;
    }

    /**
     * Check that Google Play is installed on device
     * @return  true if Google Play installed; false otherwise
     */
    private boolean checkGooglePlayIsInstalledAndUpToDate() {
        int googlePlayServicesCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (!(googlePlayServicesCode == ConnectionResult.SUCCESS)) {
            int activityResultCode = 10;
            GooglePlayServicesUtil.getErrorDialog(googlePlayServicesCode, this, activityResultCode);
            return false;
        }
        else {
            return true;
        }
    }

    /**
     * Set up the category spinner with a list of categories for the restaurants recently visited
     */
    private void setUpCategorySpinner() {
        ArrayAdapter<Category> adapter = new ArrayAdapter<Category>(this, R.layout.spinner_item, getCategories());
        mSpinner.setAdapter(adapter);
        Log.d(TAG, "done setting up category spinner");
    }

    /**
     * Get categories for restaurants recently visited.
     * List must not contain duplicate categories and must be shuffled (so that a potentially different category
     * appears first in the list each time this method is called).
     * @return  list of categories
     */
    private List<Category> getCategories() {
        List<Category> categoriesGet = new ArrayList<Category>();
        for (Restaurant restaurantOne: recentRestaurants){
            List<Category> restCategoryList = restaurantOne.getCategories();
            for (Category restOneCategory : restCategoryList){
                if (!categoriesGet.contains(restOneCategory)){
                    categoriesGet.add(restOneCategory);}
            }
        }
        shuffle(categoriesGet);
        System.out.println(categoriesGet.get(0).toString());
        System.out.println(categoriesGet.get(1).toString());
        System.out.println(categoriesGet.size());
        return categoriesGet;
    }

    /**
     * Set up listener for Play button
     */
    private void setUpPlayButtonListener() {
        mPlayButton = (Button) findViewById(R.id.play_game_button);
        mPlayButton.setEnabled(true);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "play button has been clicked");
                Object spinnerItem = mSpinner.getSelectedItem();
                startRound(spinnerItem);
            }
        });
    }



    /// ***********************************************************************************************************
    ///
    /// Game related methods
    ///
    /// ***********************************************************************************************************

    /**
     * Start a new round of the game based on user's selection
     *
     * @param spinnerItem user's selection (a city or a category)
     */
    private void startRound(Object spinnerItem) {
        Log.d(TAG, "starting game");

        // clear route and markers from map
        mMap.clear();

        totalDistance = 0.0;
        fromLL = toLL = null;
        distanceView.setText("Distance: \n" + totalDistance + " km");

        Restaurant r = selectRestaurant(spinnerItem);

        // If none had the category put it into the log
        if (r == null) {
            Log.d(TAG, "couldn't find a restaurant that satisfies user selection");
            Toast.makeText(this, "Sorry, try a different one please", Toast.LENGTH_SHORT).show();
        } else {
            currentRestaurant = r;
            boolean showNeighbourhood = spinnerItem instanceof GeoArea;
            initRound(showNeighbourhood);
        }
    }

    /**
     * Select a restaurant based on user selection (by city or by category)
     *
     * @param spinnerItem   user's selection (a city or a category)
     * @return  restaurant corresponding to user's selection
     */
    private Restaurant selectRestaurant(Object spinnerItem) {
        Restaurant r = null;

        if (recentRestaurants == null)
            Toast.makeText(this, "No restaurants obtained from Yelp service!", Toast.LENGTH_SHORT).show();
        else {
            Collections.shuffle(recentRestaurants, rand);
            Iterator<Restaurant> itr = recentRestaurants.iterator();
            boolean hasItem = false;
            while (itr.hasNext()) {
                r = itr.next();
                if (spinnerItem instanceof Category) {
                    hasItem = r.getCategories().contains(spinnerItem);
                } else if (spinnerItem instanceof GeoArea) {
                    hasItem = r.getGeographicalArea().equals(spinnerItem);
                }

                if (hasItem)
                    break;

                r = null;
            }
        }

        return r;
    }

    /**
     * Initialize new round in game
     * @param showNeighbourhood  true if search for restaurant is by neighbourhood; false if by city
     */
    private void initRound(boolean showNeighbourhood) {
        Log.d(TAG, "initializing a round");

        // hide soft keyboard
        if (getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        try {
            GeoArea geoArea = currentRestaurant.getGeographicalArea();
            Address focusAddress = lookupFocusAddress(showNeighbourhood, geoArea);

            // If its null we can't focus on it, and it means that it couldn't match our address
            // in the geocoder
            if (focusAddress != null) {

                List<Address> addresses = geocoder.getFromLocationName(currentRestaurant.getAddress()
                        + " ," + geoArea.getCityString(), 1);

                if (addresses.size() > 0) {
                    Address restaurantAdd = addresses.get(0);
                    LatLng restaurantLatLng = new LatLng(restaurantAdd.getLatitude(), restaurantAdd.getLongitude());

                    animateCameraToRestaurant(showNeighbourhood, focusAddress);

                    // Start points
                    points = INIT_POINTS;
                    startPointCountdown();

                    // Display restaurant name
                    setUpGameDialog(currentRestaurant.getName());

                    // Set up listener for map
                    mMap.setOnMapClickListener(new MapClickHandler(restaurantLatLng));
                }
                else {
                    Log.d(TAG, "could not get address for restaurant");
                    Toast.makeText(this, "Sorry we couldn't get an address for restaurant", Toast.LENGTH_SHORT).show();
                }
            }
            else {
                Log.d(TAG, "focus address null: can't find geolocation");
                Toast.makeText(this, "Sorry we couldn't get this geographical area", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Sorry, the android geolocation service is not available.", Toast.LENGTH_LONG).show();
            Log.e(TAG,e.getMessage(),e);
            Log.d(TAG, "trying to get address for " + currentRestaurant.getGeographicalArea() + " from geocoder");
        }
    }

    /**
     * Animate camera so that map is centred on restaurant
     *
     * @param showNeighbourhood  true if search for restaurant is by neighbourhood; false if by city
     * @param focusAddress  the address of restaurant (retrieved from GeoCoder)
     */
    private void animateCameraToRestaurant(boolean showNeighbourhood, Address focusAddress) {
        final double BORDER = 0.06;
        final LatLng swVancouverLatLng = new LatLng(49.23356, -123.220378);
        final LatLng neVancouverLatLng = new LatLng(49.295168, -123.023310);
        final LatLngBounds vancouverBounds = new LatLngBounds(swVancouverLatLng, neVancouverLatLng);

        LatLngBounds.Builder builder = LatLngBounds.builder();
        LatLng geoAreaLatLng = new LatLng(focusAddress.getLatitude(), focusAddress.getLongitude());

        boolean inVancouver = vancouverBounds.contains(geoAreaLatLng);
        builder.include(geoAreaLatLng);

        if (!showNeighbourhood && inVancouver) {
            builder.include(vancouverBounds.southwest);
            builder.include(vancouverBounds.northeast);
        } else {
            builder.include(new LatLng(geoAreaLatLng.latitude - BORDER, geoAreaLatLng.longitude - BORDER));
            builder.include(new LatLng(geoAreaLatLng.latitude + BORDER, geoAreaLatLng.longitude + BORDER));
        }

        LatLngBounds bounds = builder.build();
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 10);
        mMap.animateCamera(cameraUpdate);
    }

    /**
     * Get address of restaurant from GeoCoder
     *
     * @param showNeighbourhood  true if search is by neighbourhood, false if search is by city only
     * @param geoArea  geographical area of restaurant
     * @return address of restaurant retrieved from GeoCoder
     * @throws IOException  when GeoCoder cannot find address
     */
    private Address lookupFocusAddress(boolean showNeighbourhood, GeoArea geoArea) throws IOException {
        Address focusAddress = null;

        // If its in a neighbourhood, but we aren't searching by geolocation, only focus on city
        if (!showNeighbourhood) {
            focusAddress = geocoder.getFromLocationName(geoArea.getCityString(), 1).get(0);

        } else {
            List<String> geoStrings = geoArea.getAllGeoStrings();
            // The last string is the most specific, the first is the least,
            // we want the most specific match so we start with the last and stop if we find a match
            List<Address> matches;
            for (int i = geoStrings.size() - 1; i >= 0; i--) {
                matches = geocoder.getFromLocationName(geoStrings.get(i), 1);
                if (matches.size() > 0) {
                    focusAddress = matches.get(0);
                    break;
                }
            }
        }

        return focusAddress;
    }

    /**
     * Update route - plot route on map and update total distance travelled so far in current round
     * @param route  the route to add to the map
     */
    private void updateRoute(Route route) {
        plotRoute(route);
        // convert to kms, rounded to 2 dps.
        totalDistance = ((int) ((totalDistance + route.getDistance() / 1000.0 + 0.5) * 100)) / 100.0;
        distanceView.setText("Distance: \n" + totalDistance + " km");
    }

    /**
     * Plot route on the map.
     * Note: end of one leg is not connected to start of next leg!
     *
     * @param route  the route to be plotted on map
     */
    private void plotRoute(Route route) {
        List<Leg> legs = route.getLegs();
        PolylineOptions options = new PolylineOptions();
        for (int j = 0; j< legs.size(); j++){
            Leg leg = legs.get(j);
            List<LatLng> latLngs = leg.getPoints();
            for (int k = 0;k<latLngs.size(); k++ ){
                LatLng latLng = latLngs.get(k);
                options.add(latLng).width(5).color(Color.RED);

            }
        }
        mMap.addPolyline(options);
    }

    /**
     * Win the round
     * @param restaurantLatLng  location of restaurant
     */
    private void winRound(LatLng restaurantLatLng) {
        Log.d(TAG, "you've won!");
        // Create popup!
        Integer points = Integer.parseInt(String.valueOf(pointsView.getText()));
        Toast toast = Toast.makeText(getApplicationContext(),
                "Congrats you just won! You earned an impressive " + points + " points!", Toast.LENGTH_LONG);
        toast.show();
        stopRound(points);
        getRoute(toLL, restaurantLatLng);

        markerManager.removeMarkers();
        markerManager.addRestaurantMarker(restaurantLatLng, currentRestaurant.getName());

        totalPoints += points;
        totalPointsView.setText("Points: \n" + totalPoints);
    }

    /**
     * Get walking route from Google Directions service
     * @param from  origin of desired route
     * @param to    destination of desired route
     */
    private void getRoute(LatLng from, LatLng to) {
        if (from != null && to != null)
            new DownloadRouteFromGoogleTask().execute(from, to);
    }

    private void loseRound() {
        stopRound(0);
        Toast.makeText(getApplicationContext(),
                "Sorry, your points ran out and you lost!\nTry again?", Toast.LENGTH_LONG).show();
    }

    /**
     * Stop current round and track the score for this round (points earned and number of attempts made)
     * If points is -1, score is not logged (game got interrupted)
     *
     * @param points  points scored (or -1 if game was interrupted)
     */
    private void stopRound(int points) {
        Log.d(TAG, "stopping round");

        if (points >= 0)
            scoreTracker.addScore(new Score(numAttempts, points));

        if (myTimer != null) myTimer.cancel();
        mMap.setOnMapClickListener(null);

        if (mRestDialog != null)
            mRestDialog.setVisibility(View.GONE);

        initialDialog.setVisibility(View.VISIBLE);
        pointsView.setText("");
        numAttempts = 0;
    }

    /**
     * Set up the game dialog that displays name of restaurant to user
     *
     * @param name  name of restaurant
     */
    private void setUpGameDialog(String name) {
        mSpinner.setVisibility(View.GONE);
        mPlayButton.setVisibility(View.GONE);
        mAutocompleteLocation.setVisibility(View.GONE);
        mRestDialog = (TextView) findViewById(R.id.restaurant_name_display);
        mRestDialog.setVisibility(View.VISIBLE);
        mRestDialogQuestion = "Where do you think " + name + " is?";
        mRestDialog.setText(mRestDialogQuestion);
    }

    /**
     * Start timer for points countdown
     */
    private void startPointCountdown() {
        myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updatePoints();
            }
        }, 0, 1000);
    }

    /**
     * Update points available to be won in current round
     */
    private void updatePoints() {
        points -= DECREMENT;
        mainThreadHandler.post(pointsRunnable);
        if (points < DECREMENT)
            mainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    loseRound();
                }
            });
    }

    /**
     * Updates points available in current round
     */
    final Runnable pointsRunnable = new Runnable() {
        public void run() {
            pointsView.setText(String.valueOf(points));
        }
    };

    /**
     * Sets up autocompletion text box and play button
     */
    private void setUpLocationTextAndPlayButton() {
        mPlayButton = (Button) findViewById(R.id.play_game_button);
        mPlayButton.setEnabled(false);
        mAutocompleteLocation.setThreshold(1);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.list_item, Constants.CITIES);
        mAutocompleteLocation.setAdapter(adapter);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mAutocompleteLocation, InputMethodManager.SHOW_IMPLICIT);

        mAutocompleteLocation.setOnItemClickListener(new AutoCompleteSelectionHandler());
    }

    /**
     * Enables category selection menu item
     */
    final Runnable updateMenuRunnable = new Runnable() {
        public void run() {
            if (hasRestaurants) {
                // Got some restaurants, so enable category menu
                menu.findItem(R.id.category_choice).setEnabled(true);
                invalidateOptionsMenu();
            }
        }
    };


    /// ***********************************************************************************************************
    ///
    /// Map geometry methods
    ///
    /// ***********************************************************************************************************

    /**
     * Produce a random tilt in the range [0, 90)
     * @return  a random tilt
     */
    private float randomTilt() {
        return Math.abs(rand.nextInt() % 90);
    }

    /**
     * Produce a random bearing in the range [0, 360)
     * @return  a random bearing
     */
    private float randomBearing() {
        return Math.abs(rand.nextInt() % 360);
    }

    /**
     * Determine the zoom level for the map from lat/lng bounds
     * @param bounds        boundary of region
     * @param mapWidthPx    map width
     * @param mapHeightPx   map height
     * @return  zoom level
     */
    private int getBoundsZoomLevel(LatLngBounds bounds, int mapWidthPx, int mapHeightPx) {
        final int WORLD_PX_HEIGHT = 256;
        final int WORLD_PX_WIDTH = 256;
        final int ZOOM_MAX = 21;
        LatLng ne = bounds.northeast;
        LatLng sw = bounds.southwest;

        double latFraction = (latRad(ne.latitude) - latRad(sw.latitude)) / Math.PI;

        double lngDiff = ne.longitude - sw.longitude;
        double lngFraction = ((lngDiff < 0) ? (lngDiff + 360) : lngDiff) / 360;

        double latZoom = zoom(mapHeightPx, WORLD_PX_HEIGHT, latFraction);
        double lngZoom = zoom(mapWidthPx, WORLD_PX_WIDTH, lngFraction);

        int result = Math.min((int) latZoom, (int) lngZoom);
        return Math.min(result, ZOOM_MAX);
    }

    private double latRad(double lat) {
        double sin = Math.sin(lat * Math.PI / 180);
        double radX2 = Math.log((1 + sin) / (1 - sin)) / 2;
        return Math.max(Math.min(radX2, Math.PI), -Math.PI) / 2;
    }

    private double zoom(int mapPx, int worldPx, double fraction) {
        final double LN2 = Math.log(2.0);
        return Math.floor(Math.log(mapPx / worldPx / fraction) / LN2);
    }

    /**
     * NOTE TO CPSC 210 STUDENTS: this method is needed only if you attempt the bonus exercise.
     *
     * Get bearing from origin to destination on map
     * @param origin  location of origin
     * @param dest    location of destination
     * @return  bearing from origin to destination measured clockwise in degrees from due North
     */
    private float getBearing(LatLng origin, LatLng dest) {
        double originLongRad = Math.toRadians(origin.longitude);
        double originLatRad = Math.toRadians(origin.latitude);
        double destLongRad = Math.toRadians(dest.longitude);
        double destLatRad = Math.toRadians(dest.latitude);

        double deltaLong = destLongRad - originLongRad;
        double y = Math.sin(deltaLong) * Math.cos(destLatRad);
        double x = Math.cos(originLatRad) * Math.sin(destLatRad)
                - Math.sin(originLatRad) * Math.cos(destLatRad) * Math.cos(deltaLong);
        double bearing = Math.atan2(y, x);
        return (float) ((int) Math.toDegrees(bearing) + 360) % 360;
    }


    /// ***********************************************************************************************************
    ///
    /// Background Tasks
    ///
    /// ***********************************************************************************************************

    /**
     * Task that will download restaurant data from Yelp
     */
    private class DownloadRestaurantsFromYelpTask extends AsyncTask<String, Integer, Void> {
        private static final int MAX_RESTAURANTS = 10;
        private ProgressDialog progressDialog;
        private boolean gotData;

        @Override
        protected void onPreExecute() {
            //Create a new progress dialog
            progressDialog = ProgressDialog.show(RestaurantQuiz.this,"Loading...",
                    "Getting a bunch of restaurant data...", true, false);
            gotData = false;
        }

        @Override
        protected Void doInBackground(String... locations) {

            try {
                String response = mYelpApi.queryAPI("food", locations[0] + ", " + locations[1], "", MAX_RESTAURANTS);
                List<Restaurant> restaurants = YelpDataParser.parseRestaurantData(response, locations[0]);
                if (recentRestaurants == null) {
                    recentRestaurants = restaurants;
                }
                else {
                    // add restaurants to recent restaurants (unless already added)
                    for (Restaurant r : restaurants) {
                        if (!recentRestaurants.contains(r))
                            recentRestaurants.add(r);
                    }
                }

                gotData = true;
                hasRestaurants = true;
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
            return null;
        }

        //after executing the code in the thread
        @Override
        protected void onPostExecute(Void result) {
            //close the progress dialog
            progressDialog.dismiss();
            mainThreadHandler.post(updateMenuRunnable);

            if (!gotData)
                Toast.makeText(getApplicationContext(), "Could not obtain Yelp data!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Task that will download route data from Google
     */
    private class DownloadRouteFromGoogleTask extends AsyncTask<LatLng, Integer, Route> {
        private ProgressDialog progressDialog;
        private Route route;

        @Override
        protected void onPreExecute() {
            //Create a new progress dialog
            progressDialog = ProgressDialog.show(RestaurantQuiz.this,"Loading...",
                    "Getting route from Google...", true, false);
        }

        @Override
        protected Route doInBackground(LatLng... locations) {
            try {
                StringBuilder response = GoogleAPI.getRoute(locations[0], locations[1]);
                route = GoogleDirectionsParser.parseRoute(response.toString());
            } catch (Exception e) {
                Log.d(TAG, e.getMessage(), e);
                route = null;
            }

            return route;
        }

        //after executing the code in the thread
        @Override
        protected void onPostExecute(Route result) {
            //close the progress dialog and update the route on the map
            progressDialog.dismiss();
            if (route != null)
                updateRoute(result);
            else
                Toast.makeText(getApplicationContext(), "Could not obtain route!", Toast.LENGTH_SHORT).show();
        }
    }


    /// ***********************************************************************************************************
    ///
    /// Event Listeners
    ///
    /// ***********************************************************************************************************

    /**
     * Handle a click on the map
     */
    private class MapClickHandler implements GoogleMap.OnMapClickListener {
        final double BORDER = 1.1;
        private LatLng restaurantLatLng;

        public MapClickHandler(LatLng restaurantLatLng) {
            this.restaurantLatLng = restaurantLatLng;
        }

        @Override
        public void onMapClick(LatLng latLng) {
            double distance = SphericalUtil.computeDistanceBetween(latLng, restaurantLatLng);

            mRestDialog.setText(mRestDialogQuestion + "\nYour click was: " + ((int) distance) + " metres away");
            numAttempts++;

            // if its short enough trigger a win!
            if (distance < 50) {
                winRound(restaurantLatLng);
                return;
            }

            markerManager.addMarker(latLng, distance);

            fromLL = toLL;
            toLL = latLng;
            getRoute(fromLL, toLL);

            animateCamera(latLng);

            points *= (1 - GUESS_COST);  // lose GUESS-COST% of available points on each guess
        }

        private void animateCamera(LatLng latLng) {
            LatLng swCorner = new LatLng(
                    latLng.latitude - (Math.abs(latLng.latitude - restaurantLatLng.latitude) * BORDER),
                    latLng.longitude - (Math.abs(latLng.longitude - restaurantLatLng.longitude) * BORDER));
            LatLng neCorner = new LatLng(
                    latLng.latitude + (Math.abs(latLng.latitude - restaurantLatLng.latitude) * BORDER),
                    latLng.longitude + (Math.abs(latLng.longitude - restaurantLatLng.longitude) * BORDER));

            LatLngBounds bounds = LatLngBounds.builder()
                    .include(swCorner)
                    .include(neCorner).build();

            CameraPosition cp = new CameraPosition.Builder()
                    .bearing(randomBearing())
                    .tilt(randomTilt())
                    .target(latLng)
                    .zoom(getBoundsZoomLevel(bounds,
                            findViewById(R.id.map).getMeasuredWidth(),
                            findViewById(R.id.map).getMeasuredHeight()))
                    .build();

            CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
            mMap.animateCamera(cu);
        }
    }

    /**
     * Handle selection of an item in the city autocomplete list.
     */
    private class AutoCompleteSelectionHandler implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            String str = (String) adapterView.getItemAtPosition(position);
            Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
            mPlayButton.setEnabled(true);

            final String[] locations = str.split(", ");

            // download restaurant data
            new DownloadRestaurantsFromYelpTask().execute(locations);

            mPlayButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, "selection made from list of cities, starting game");
                    mPlayButton.setEnabled(false);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mAutocompleteLocation.getWindowToken(), 0);
                    City city = new City(locations[0], locations[1], locations[2]);
                    startRound(city);
                }
            });
        }
    }
}
