package ca.ubc.cs.cpsc210.quiz.activity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pcarter on 14-11-06.
 *
 * Manager for markers plotted on map
 */
public class MarkerManager {
    private GoogleMap map;
    List<Marker> markers;
    Marker restaurantMarker;
    /**
     * Constructor initializes manager with map for which markers are to be managed.
     * @param map  the map for which markers are to be managed
     */
    public MarkerManager(GoogleMap map) {
        this.map = map;
        markers = new ArrayList<Marker>();

    }

    /**
     * Get last marker added to map
     * @return  last marker added
     */
    public Marker getLastMarker() {
        return markers.get(markers.size() - 1);
    }

    /**
     * Add green marker to show position of restaurant
     * @param point   the point at which to add the marker
     * @param title   the marker's title
     */
    public void addRestaurantMarker(LatLng point, String title) {
        restaurantMarker = map.addMarker(new MarkerOptions().position(point).title(title).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
    }

    /**
     * Add a marker to mark latest guess from user.  Only the most recent two positions selected
     * by the user are marked.  The distance from the restaurant is used to create the marker's title
     * of the form "xxxx m away" where xxxx is the distance from the restaurant in metres (truncated to
     * an integer).
     *
     * The colour of the marker is based on the distance from the restaurant:
     * - red, if the distance is 3km or more
     * - somewhere between red (at 3km) and green (at 0m) (on a linear scale) for other distances
     *
     * @param latLng
     * @param distance
     */
    public void addMarker(LatLng latLng, double distance) {
        if(markers.size() != 0) {
            Marker lastMarker = getLastMarker();
            for (int i = 0; i < markers.size(); i++) {
                Marker n = markers.get(i);
                if (!markers.get(i).equals(lastMarker)) {
                    n.remove();
                    markers.remove(i);
                }

            }
        }
        int i = (int) Math.round(distance);
        String d = Integer.toString(i);
        Marker m = map.addMarker(new MarkerOptions().position(latLng).title(d+"m away").icon(BitmapDescriptorFactory.defaultMarker(getColour(distance))));
        markers.add(m);


    }

    /**
     * Remove markers that mark user guesses from the map
     */
    public void removeMarkers() {

        for (Marker m: markers){
            m.remove();
        }
        markers.clear();
    }

    /**
     * Produce a colour on a linear scale between red and green based on distance:
     *
     * - red, if distance is 3km or more
     * - somewhere between red (at 3km) and green (at 0m) (on a linear scale) for other distances
     * @param distance  distance from restaurant
     * @return  colour of marker
     */
    private float getColour(double distance) {
        float f = (float) distance;
        float colour;
        if (distance > 3000)
            colour = 0.0f;
        else
            colour = 120 - ((f  / 3000)* 120);
        return colour;
    }
}
