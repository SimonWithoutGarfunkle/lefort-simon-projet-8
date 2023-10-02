package com.openclassrooms.tourguide.constant;

import java.util.concurrent.TimeUnit;

public class Constants {

    //The number of closest attractions to return to the user, by default set to 5
    public static final int NUMBER_OF_CLOSE_ATTRACTIONS = 5;

    //Conversion factor from nautical miles to statute miles.
    public static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

    //The trackingPollingInterval constant specifies the interval time in seconds for polling user locations. It is set to 5 minutes here, converted to seconds.
    public static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);
}
