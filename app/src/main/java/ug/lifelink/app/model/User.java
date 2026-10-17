package ug.lifelink.app.model;

/**
 * A registered LifeLink user. The password hash and salt are deliberately NOT part of this
 * class: they stay inside the data layer so they cannot leak into screens or logs.
 */
public class User {

    public static final String ROLE_DONOR = "DONOR";
    public static final String ROLE_HOSPITAL = "HOSPITAL";

    private final long id;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String bloodGroup;
    private final String role;

    public User(long id, String fullName, String email, String phone, String bloodGroup, String role) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.bloodGroup = bloodGroup;
        this.role = role;
    }

    public long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public String getRole() {
        return role;
    }

    public boolean isHospital() {
        return ROLE_HOSPITAL.equals(role);
    }

    /** First word of the full name, used for friendly greetings. */
    public String getFirstName() {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }
}
