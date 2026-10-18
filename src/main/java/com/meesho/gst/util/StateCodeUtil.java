package com.meesho.gst.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for Indian Goods and Services Tax (GST) State Codes mapping and resolution.
 * Supports resolution by 2-digit numeric code, state name (case-insensitive), or prefixed formats.
 */
public final class StateCodeUtil {

    private static final Map<String, String> STATE_NAME_TO_CODE_MAP;
    private static final Map<String, String> CODE_TO_STATE_NAME_MAP;
    private static final Pattern LEADING_DIGITS_PATTERN = Pattern.compile("^([0-9]{1,2})(?:[^0-9].*)?$");

    static {
        Map<String, String> nameToCode = new HashMap<>();
        Map<String, String> codeToName = new HashMap<>();

        registerState(nameToCode, codeToName, "01", "JAMMU AND KASHMIR", "JAMMU & KASHMIR", "J&K", "JK");
        registerState(nameToCode, codeToName, "02", "HIMACHAL PRADESH", "HP");
        registerState(nameToCode, codeToName, "03", "PUNJAB", "PB");
        registerState(nameToCode, codeToName, "04", "CHANDIGARH", "CH");
        registerState(nameToCode, codeToName, "05", "UTTARAKHAND", "UTTARANCHAL", "UK");
        registerState(nameToCode, codeToName, "06", "HARYANA", "HR");
        registerState(nameToCode, codeToName, "07", "DELHI", "NEW DELHI", "DL");
        registerState(nameToCode, codeToName, "08", "RAJASTHAN", "RJ");
        registerState(nameToCode, codeToName, "09", "UTTAR PRADESH", "UP");
        registerState(nameToCode, codeToName, "10", "BIHAR", "BR");
        registerState(nameToCode, codeToName, "11", "SIKKIM", "SK");
        registerState(nameToCode, codeToName, "12", "ARUNACHAL PRADESH", "AR");
        registerState(nameToCode, codeToName, "13", "NAGALAND", "NL");
        registerState(nameToCode, codeToName, "14", "MANIPUR", "MN");
        registerState(nameToCode, codeToName, "15", "MIZORAM", "MZ");
        registerState(nameToCode, codeToName, "16", "TRIPURA", "TR");
        registerState(nameToCode, codeToName, "17", "MEGHALAYA", "ML");
        registerState(nameToCode, codeToName, "18", "ASSAM", "AS");
        registerState(nameToCode, codeToName, "19", "WEST BENGAL", "WB");
        registerState(nameToCode, codeToName, "20", "JHARKHAND", "JH");
        registerState(nameToCode, codeToName, "21", "ODISHA", "ORISSA", "OD", "OR");
        registerState(nameToCode, codeToName, "22", "CHHATTISGARH", "CHATTISGARH", "CG");
        registerState(nameToCode, codeToName, "23", "MADHYA PRADESH", "MP");
        registerState(nameToCode, codeToName, "24", "GUJARAT", "GJ");
        registerState(nameToCode, codeToName, "26", "DADRA AND NAGAR HAVELI AND DAMAN AND DIU",
                "DADRA & NAGAR HAVELI AND DAMAN & DIU", "DAMAN AND DIU", "DADRA AND NAGAR HAVELI",
                "DNHDD", "DD", "DN");
        registerState(nameToCode, codeToName, "27", "MAHARASHTRA", "MH");
        registerState(nameToCode, codeToName, "28", "ANDHRA PRADESH (BEFORE BIFURCATION)");
        registerState(nameToCode, codeToName, "29", "KARNATAKA", "KA");
        registerState(nameToCode, codeToName, "30", "GOA", "GA");
        registerState(nameToCode, codeToName, "31", "LAKSHADWEEP", "LD");
        registerState(nameToCode, codeToName, "32", "KERALA", "KL");
        registerState(nameToCode, codeToName, "33", "TAMIL NADU", "TAMILNADU", "TN");
        registerState(nameToCode, codeToName, "34", "PUDUCHERRY", "PONDICHERRY", "PY");
        registerState(nameToCode, codeToName, "35", "ANDAMAN AND NICOBAR ISLANDS",
                "ANDAMAN & NICOBAR ISLANDS", "ANDAMAN AND NICOBAR", "AN");
        registerState(nameToCode, codeToName, "36", "TELANGANA", "TS", "TG");
        registerState(nameToCode, codeToName, "37", "ANDHRA PRADESH", "AP");
        registerState(nameToCode, codeToName, "38", "LADAKH", "LA");
        registerState(nameToCode, codeToName, "97", "OTHER TERRITORY", "OTHER");

        STATE_NAME_TO_CODE_MAP = Collections.unmodifiableMap(nameToCode);
        CODE_TO_STATE_NAME_MAP = Collections.unmodifiableMap(codeToName);
    }

    private StateCodeUtil() {
        // Private constructor for utility class
    }

    private static void registerState(Map<String, String> nameToCode, Map<String, String> codeToName,
                                      String code, String primaryName, String... aliases) {
        codeToName.put(code, primaryName);
        nameToCode.put(normalizeKey(primaryName), code);
        for (String alias : aliases) {
            nameToCode.put(normalizeKey(alias), code);
        }
    }

    private static String normalizeKey(String input) {
        if (input == null) {
            return "";
        }
        return input.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "");
    }

    /**
     * Resolves an input state representation (e.g. "Karnataka", "29", "29-Karnataka", "KA", "29.0")
     * into a standard 2-digit GST state code string (e.g. "29").
     *
     * @param stateInput raw string from Excel cell or API parameter
     * @return 2-digit zero-padded GST State Code string
     * @throws IllegalArgumentException if the state cannot be identified
     */
    public static String resolveStateCode(String stateInput) {
        if (stateInput == null || stateInput.trim().isEmpty()) {
            throw new IllegalArgumentException("State/POS input cannot be blank");
        }

        String trimmed = stateInput.trim();

        // 1. Check if input starts with or is numeric digits (e.g., "29", "7", "29-Karnataka", "29.0")
        if (trimmed.endsWith(".0")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }

        Matcher matcher = LEADING_DIGITS_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            int numericCode = Integer.parseInt(matcher.group(1));
            String formattedCode = String.format("%02d", numericCode);
            if (CODE_TO_STATE_NAME_MAP.containsKey(formattedCode)) {
                return formattedCode;
            }
        }

        // 2. Direct lookup in normalized name/alias map
        String normalized = normalizeKey(trimmed);
        String resolvedCode = STATE_NAME_TO_CODE_MAP.get(normalized);
        if (resolvedCode != null) {
            return resolvedCode;
        }

        // 3. Fallback: Check if trimmed input is already a 2-digit code
        if (trimmed.length() == 2 && CODE_TO_STATE_NAME_MAP.containsKey(trimmed)) {
            return trimmed;
        }

        throw new IllegalArgumentException("Unknown or unmappable GST state: '" + stateInput + "'");
    }

    /**
     * Returns true if the given code is a recognized 2-digit Indian GST state code.
     */
    public static boolean isValidStateCode(String code) {
        if (code == null) {
            return false;
        }
        String formatted = code.length() == 1 ? "0" + code : code;
        return CODE_TO_STATE_NAME_MAP.containsKey(formatted);
    }

    /**
     * Retrieves primary state name by 2-digit code.
     */
    public static String getStateName(String code) {
        return CODE_TO_STATE_NAME_MAP.get(code);
    }
}
