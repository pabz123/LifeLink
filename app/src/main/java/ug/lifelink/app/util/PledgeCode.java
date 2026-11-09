package ug.lifelink.app.util;

import java.util.Random;

/**
 * The short code a donor shows at the hospital, for example "K7Q-2M4". It is random, so it says
 * nothing about the donor. Letters and digits that are easy to confuse (0, O, 1, I, L) are left out.
 */
public final class PledgeCode {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private PledgeCode() {
    }

    public static String generate(Random random) {
        StringBuilder sb = new StringBuilder(7);
        for (int i = 0; i < 6; i++) {
            if (i == 3) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static boolean isValid(String code) {
        return code != null && code.matches("[A-Z0-9]{3}-[A-Z0-9]{3}");
    }
}
