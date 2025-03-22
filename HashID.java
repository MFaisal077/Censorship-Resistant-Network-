// IN2011 Computer Networks
// Coursework 2024/2025
//
// Construct the hashID for a string

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HashID {

	public static String computeHashID(String s) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] hashBytes = md.digest(s.getBytes(StandardCharsets.UTF_8));

		// Convert hash bytes to a hexadecimal string
		StringBuilder hexString = new StringBuilder();
		for (byte b : hashBytes) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}
}
