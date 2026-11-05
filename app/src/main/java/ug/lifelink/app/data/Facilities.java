package ug.lifelink.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ug.lifelink.app.util.Geo;

/**
 * The hospitals and blood banks a record can name. Choosing from this list (instead of typing)
 * stops invented or misspelt facilities. It is a bundled starter list: names are real
 * facilities, but the coordinates are approximate (the centre of the town), so distances are
 * town-level. A later version would load the list from the server and keep it up to date.
 *
 * Pure Java (no Android classes), so it is unit tested on a laptop.
 */
public final class Facilities {

    public static final class Facility {
        public final String name;
        public final double lat;
        public final double lng;

        Facility(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private static final List<Facility> ALL = Collections.unmodifiableList(java.util.Arrays.asList(
        new Facility("Nakasero Blood Bank (UBTS)", 0.319, 32.577),
        new Facility("Mulago National Referral Hospital", 0.3376, 32.5763),
        new Facility("Nsambya Hospital", 0.299, 32.589),
        new Facility("Lubaga Hospital", 0.3027, 32.5527),
        new Facility("Mengo Hospital", 0.3, 32.56),
        new Facility("Nakasero Hospital", 0.318, 32.578),
        new Facility("Mbarara Regional Blood Bank (UBTS)", -0.6072, 30.6545),
        new Facility("Mbarara Regional Referral Hospital", -0.6072, 30.6545),
        new Facility("Mbale Regional Blood Bank (UBTS)", 1.0775, 34.1753),
        new Facility("Mbale Regional Referral Hospital", 1.0775, 34.1753),
        new Facility("Bududa Health Centre", 0.998, 34.334),
        new Facility("Gulu Regional Blood Bank (UBTS)", 2.7746, 32.299),
        new Facility("Gulu Regional Referral Hospital", 2.7746, 32.299),
        new Facility("Arua Regional Blood Bank (UBTS)", 3.02, 30.91),
        new Facility("Arua Regional Referral Hospital", 3.02, 30.91),
        new Facility("Fort Portal Regional Blood Bank (UBTS)", 0.671, 30.275),
        new Facility("Fort Portal Regional Referral Hospital", 0.671, 30.275),
        new Facility("Kabale Regional Blood Bank (UBTS)", -1.249, 29.9899),
        new Facility("Kabale Regional Referral Hospital", -1.249, 29.9899),
        new Facility("Hoima Regional Blood Bank (UBTS)", 1.4331, 31.3524),
        new Facility("Hoima Regional Referral Hospital", 1.4331, 31.3524),
        new Facility("Soroti Regional Blood Bank (UBTS)", 1.715, 33.611),
        new Facility("Soroti Regional Referral Hospital", 1.715, 33.611),
        new Facility("Lira Regional Referral Hospital", 2.2499, 32.8998),
        new Facility("Jinja Regional Referral Hospital", 0.4244, 33.2041),
        new Facility("Masaka Regional Referral Hospital", -0.3136, 31.7413),
        new Facility("Kitovu Hospital", -0.33, 31.73)
    ));

    private Facilities() {
    }

    public static List<Facility> all() {
        return ALL;
    }

    public static boolean isKnown(String name) {
        for (Facility f : ALL) {
            if (f.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Facility names, nearest first when the position is known, alphabetical otherwise.
     * Equal distances (several facilities in one town) keep alphabetical order.
     */
    public static List<String> namesNear(final boolean hasLocation, final double lat, final double lng) {
        List<Facility> sorted = new ArrayList<>(ALL);
        Collections.sort(sorted, new Comparator<Facility>() {
            @Override
            public int compare(Facility a, Facility b) {
                if (hasLocation) {
                    int byDistance = Double.compare(Geo.distanceKm(lat, lng, a.lat, a.lng),
                            Geo.distanceKm(lat, lng, b.lat, b.lng));
                    if (byDistance != 0) {
                        return byDistance;
                    }
                }
                return a.name.compareTo(b.name);
            }
        });
        List<String> names = new ArrayList<>();
        for (Facility f : sorted) {
            names.add(f.name);
        }
        return names;
    }
}
