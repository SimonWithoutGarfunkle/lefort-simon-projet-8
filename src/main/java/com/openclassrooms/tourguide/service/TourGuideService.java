package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.controller.NearByAttractionsDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

import static com.openclassrooms.tourguide.constant.Constants.NUMBER_OF_CLOSE_ATTRACTIONS;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	/**
	 * Start the TourGuideService
	 * If test mode is ON, generate the number of user defined if InternalTestHelper
	 * Each user has 3 random locations in their history
	 *
	 * @param gpsUtil Lib for the geolocation of users
	 * @param rewardsService Service that distributes rewards to user based on their location
	 */
	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	/**
	 * Get all rewards of the user
	 *
	 * @param user to get the list of reward
	 * @return List of rewards of the user
	 */
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * Return the last known location of the user.
	 * If no known location, track the current location of the user.
	 *
	 * @param user to locate
	 * @return most recent user location
	 */
	public VisitedLocation getUserLocation(User user) {
		logger.debug("User already visited " +user.getVisitedLocations().size()+" locations");
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	/**
	 * Get the User from his username
	 *
	 * @param userName of the user
	 * @return user
	 */
	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	/**
	 * Return all the user from the test database in memory
	 *
	 * @return all the user from the test database in memory
	 */
	public List<User> getAllUsers() {
		return new ArrayList<>(internalUserMap.values());
	}

	/**
	 * Verify the username is not already registered then add the user in the memory database
	 *
	 * @param user to add
	 */
	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			logger.info("Adding the user to the database : "+user.getUserName());
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/**
	 * Retrieves trip deals for a given user based on their reward points.
	 *
	 * @param user The user for whom the trip deals are to be retrieved.
	 * @return A list of trip deals available to the user.
	 */
	public List<Provider> getTripDeals(User user) {
		int cumulativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * Add the current location to the user and update the rewards with the new location
	 *
	 * @param user
	 * @return the current location
	 */
	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		logger.info("Adding the current location to the user "+user.getUserName());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/**
	 * Return the 5 closest attraction of the location
	 *
	 * @param visitedLocation starting point for the 5 closest attractions
	 * @return DTO with 5 closest attraction of the location
	 */
	public List<NearByAttractionsDTO> getNearByAttractions(VisitedLocation visitedLocation) {

		List<Attraction> attractions = gpsUtil.getAttractions();
		List<NearByAttractionsDTO> result = new ArrayList<>();
		for (Attraction attraction : attractions) {
			NearByAttractionsDTO nearByAttractionsDTO = new NearByAttractionsDTO();
			nearByAttractionsDTO.setAttractionName(attraction.attractionName);
			nearByAttractionsDTO.setAttractionLongitude(attraction.longitude);
			nearByAttractionsDTO.setAttractionLatitude(attraction.latitude);
			nearByAttractionsDTO.setUserLongitude(visitedLocation.location.longitude);
			nearByAttractionsDTO.setUserLatitude(visitedLocation.location.latitude);
			nearByAttractionsDTO.setDistance(rewardsService.getDistance(visitedLocation.location, attraction));
			nearByAttractionsDTO.setRewardPoints(rewardsService.getRewardPoints(attraction, visitedLocation.userId ));
			result.add(nearByAttractionsDTO);
		}

		result.sort(Comparator.comparingDouble(NearByAttractionsDTO::getDistance));
		//Keep only the 5 first(=closest) attractions of the list
		result = result.subList(0, Math.min(NUMBER_OF_CLOSE_ATTRACTIONS, result.size()));
		return result;
	}


	/**
	 * Adds a shutdown hook to stop tracking when the application is terminated.
	 */
	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
