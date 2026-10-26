package ug.lifelink.app.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ug.lifelink.app.model.Urgency;
import ug.lifelink.app.model.UrgentRequest;

/**
 * Chooses which requests a donor should see, and in what order. The phone does this itself:
 * the donor's location is never sent anywhere.
 *
 * A request is shown when it
 *   has not expired,
 *   asks for a blood group this donor can give (ABO and Rh rules), and
 *   is inside the radius for its urgency, when both positions are known.
 * When the donor's location is unknown, the radius cannot be checked, so every compatible
 * request is shown without a distance. That is the safe choice: showing a request that is far
 * away costs a donor a glance, hiding one near them could cost a life.
 *
 * Sorted with the most urgent first, then the nearest, then the newest.
 *
 * Pure Java (no Android classes), so it is unit tested on a laptop.
 */
public final class RequestMatcher {

    private RequestMatcher() {
    }

    /**
     * @param hasLocation true when donorLat and donorLng are real
     */
    public static List<UrgentRequest> match(String donorGroup, boolean hasLocation,
                                            double donorLat, double donorLng, long nowMillis,
                                            List<UrgentRequest> requests) {
        List<UrgentRequest> matched = new ArrayList<>();
        for (UrgentRequest request : requests) {
            if (request.isExpired(nowMillis)) {
                continue;
            }
            if (!BloodCompatibility.canDonateTo(donorGroup, request.getBloodGroup())) {
                continue;
            }
            if (hasLocation && request.hasLocation()) {
                double km = Geo.distanceKm(donorLat, donorLng, request.getLat(), request.getLng());
                if (km > Urgency.radiusKm(request.getUrgency(), request.getBloodGroup())) {
                    continue;
                }
                matched.add(request.withDistance(km));
            } else {
                matched.add(request);
            }
        }
        Collections.sort(matched, ORDER);
        return matched;
    }

    private static final Comparator<UrgentRequest> ORDER = (a, b) -> {
        int byUrgency = Integer.compare(Urgency.rank(a.getUrgency()), Urgency.rank(b.getUrgency()));
        if (byUrgency != 0) {
            return byUrgency;
        }
        if (a.hasDistance() && b.hasDistance()) {
            int byDistance = Double.compare(a.getDistanceKm(), b.getDistanceKm());
            if (byDistance != 0) {
                return byDistance;
            }
        } else if (a.hasDistance() != b.hasDistance()) {
            return a.hasDistance() ? -1 : 1; // known distances come before unknown ones
        }
        return Long.compare(b.getPostedAt(), a.getPostedAt());
    };
}
