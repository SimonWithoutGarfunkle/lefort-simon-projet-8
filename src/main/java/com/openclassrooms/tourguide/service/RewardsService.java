package com.openclassrooms.tourguide.service;

import java.util.*;

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

@Service
public class RewardsService {

	private Logger logger = LoggerFactory.getLogger(RewardsService.class);

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	public void calculateRewards(User user) {

		List<VisitedLocation> userLocations = user.getVisitedLocations().stream().toList();
		List<Attraction> attractions = gpsUtil.getAttractions();
		logger.info("userLocation size = "+userLocations.size());

		List<UserReward> rewardsToAdd = new ArrayList<>();

		for(VisitedLocation visitedLocation : userLocations) {

			for(Attraction attraction : attractions) {
				if(nearAttraction(visitedLocation, attraction)) {
					logger.info("about to add reward for "+attraction.attractionName);
					rewardsToAdd.add(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
				}

			}
		}
		addCalculatedRewards(user, rewardsToAdd);
	}

	public synchronized void addCalculatedRewards(User user, List<UserReward> rewardsToAdd) {

		Set<String> alreadyRewardedAttractionName = new HashSet<>();
		logger.info("User already got rewards : "+user.getUserRewards().size());
		for (UserReward alreadyRewarded : user.getUserRewards()) {
			alreadyRewardedAttractionName.add(alreadyRewarded.attraction.attractionName);
			logger.info(alreadyRewarded.attraction.attractionName);
		}
		logger.info("alreadyRewardedAttractionName size = "+alreadyRewardedAttractionName.size());
		logger.info("rewardsToAdd size = "+rewardsToAdd.size());

		for (UserReward reward : rewardsToAdd) {
			if (!(alreadyRewardedAttractionName.contains(reward.attraction.attractionName))) {
				user.addUserReward(reward);
				alreadyRewardedAttractionName.add(reward.attraction.attractionName);

			}
		}
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
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
