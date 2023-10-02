package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.openclassrooms.tourguide.controller.NearByAttractionsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import static com.openclassrooms.tourguide.constant.Constants.STATUTE_MILES_PER_NAUTICAL_MILE;

/**
 * Service that distributes rewards to user based on their location
 * Regroup the methods linked to attractions and the distance between users and attractions
 */
@Service
public class RewardsService {

	private Logger logger = LoggerFactory.getLogger(RewardsService.class);

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	//Method getAttractions from gpsUtil is slow so it's called only once
	private final List<Attraction> allAttractions;

	private final ExecutorService executorService = Executors.newFixedThreadPool(6000);

	private final Set<String> addedAttractionNames = Collections.synchronizedSet(new HashSet<>());
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		this.allAttractions = Collections.unmodifiableList(gpsUtil.getAttractions());
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public void shutDowExecutorService() {
		executorService.shutdown();
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Call the method calculateRewards on a list of user in parallel with the executorService for the multithreading
	 *
	 * @param users to calculate the rewards
	 */
	public void calculateRewardsForAllUsers(List<User> users) {
		logger.info("calculating rewards for "+users.size()+" users");
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		users.forEach(user -> futures.add(CompletableFuture.runAsync(() -> calculateRewards(user), executorService)));

		futures.forEach(CompletableFuture::join);
	}


	/**
	 * Calculate rewards and call the add method to reward the user based on all known location of the specified user
	 *
	 * @param user to add rewards
	 */
	public void calculateRewards(User user) {

		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
		logger.debug("calculate reward for "+user.getUserName()+" on his "+userLocations.size()+" known locations");

		List<Attraction> attractions = new ArrayList<>(this.allAttractions);

		List<UserReward> existingRewards = new ArrayList<>(user.getUserRewards());
		attractions.removeIf(attraction -> existingRewards.stream()
				.anyMatch(reward -> reward.attraction.attractionName.equals(attraction.attractionName)));

		List<UserReward> rewardsToAdd = new ArrayList<>();
		for(VisitedLocation visitedLocation : userLocations) {
			for(Attraction attraction : attractions) {
				if(nearAttraction(visitedLocation, attraction)) {
					rewardsToAdd.add(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user.getUserId())));
				}

			}
		}
		logger.debug("Trying to add "+rewardsToAdd.size()+" rewards");
		addCalculatedRewards(user, rewardsToAdd);
	}


	 /**
	 * Synchronized method to avoid doubloons
	 * Compare the list of reward with the last version of userRewards and add the new rewards
	 *
	 * @param user to add rewards
	 * @param rewardsToAdd List of reward to verify then to add
	 */
	private synchronized void addCalculatedRewards(User user, List<UserReward> rewardsToAdd) {

		Set<String> alreadyRewardedAttractionName = new HashSet<>();
		for (UserReward alreadyRewarded : user.getUserRewards().stream().toList()) {
			alreadyRewardedAttractionName.add(alreadyRewarded.attraction.attractionName);
			logger.debug(alreadyRewarded.attraction.attractionName);
		}

		for (UserReward reward : rewardsToAdd) {
			if (!(alreadyRewardedAttractionName.contains(reward.attraction.attractionName))) {
				logger.debug("Adding reward "+reward.attraction.attractionName+" to "+user.getUserName() );
				user.addUserReward(reward);
				alreadyRewardedAttractionName.add(reward.attraction.attractionName);

			}
		}
	}


	/**
	 * Verify the attraction is in range (attractionProximityRange) of the location
	 *
	 * @param attraction to check the range
	 * @param location starting point for the range to verify
	 * @return
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		logger.debug("Checking attraction proximity of "+attraction.attractionName+" and location "+location.longitude+"/"+location.latitude);
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	/**
	 * Verify the user went near the attraction
	 *
	 * @param visitedLocation address visited by a user
	 * @param attraction to check the proximity
	 * @return true if the user already get close enough of the attraction
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		logger.debug("Checking if the user "+visitedLocation.userId+" already get close from "+attraction.attractionName);
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	/**
	 * Ask the RewardCentral to assign reward points for the specified attraction to the specified user
	 *
	 * @param attraction to be rewarded
	 * @param userId user to reward
	 * @return Int number of points
	 */
	public int getRewardPoints(Attraction attraction, UUID userId) {
		logger.debug("Assign reward points to "+userId+" for attraction "+attraction.attractionName);
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, userId);
	}

	/**
	 * Ask the RewardCentral to assign reward points for the specified NearByAttractionDTO to the specified user
	 *
	 * @param attractionDTO to be rewarded
	 * @param userId user to reward
	 * @return Int number of points
	 */
	public int getRewardPoints(NearByAttractionsDTO attractionDTO, UUID userId) {
		logger.debug("Assign reward points to "+userId+" for attractionDTO "+attractionDTO.getAttractionName());
		return rewardsCentral.getAttractionRewardPoints(attractionDTO.getAttractionId(), userId);
	}

	/**
	 * Calculate the distance in miles between 2 points
	 *
	 * @param loc1 longitude and latitude of the first point
	 * @param loc2 longitude and latitude of the second point
	 * @return the distance in miles
	 */
	public double getDistance(Location loc1, Location loc2) {
		logger.debug("Calculate distance between "+loc1.longitude+"/"+loc1.latitude+" and "+loc2.longitude+"/"+loc2.latitude);
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
