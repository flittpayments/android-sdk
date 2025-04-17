package com.flitt.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.util.Base64;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Utility class for gathering device information and generating device fingerprints
 */
public class DeviceInfoProvider {
    private final Context context;
    private static final String PREFS_NAME = "payment_prefs";
    private static final String DEVICE_ID_KEY = "device_id";

    /**
     * Constructor requires an Android context
     */
    public DeviceInfoProvider(Context context) {
        this.context = context.getApplicationContext(); // Use application context to prevent leaks
    }

    /**
     * Creates a complete device fingerprint in the required format
     * @return JSONObject containing the device fingerprint data
     */
    public JSONObject createDeviceFingerprint() throws JSONException {
        JSONObject root = new JSONObject();
        JSONObject data = new JSONObject();

        // Generate a UUID for the device
        String deviceId = getDeviceId();

        // Fill device data
        data.put("user_agent", getUserAgent());
        data.put("language", getDeviceLanguage());
        JSONArray resolution = new JSONArray();
        resolution.put(getScreenWidth());
        resolution.put(getScreenHeight());
        data.put("resolution", resolution);
        data.put("timezone_offset", getTimezoneOffset());
        data.put("platform_name", "android_sdk");
        data.put("platform_version", BuildConfig.VERSION_NAME);
        data.put("platform_os", "android");
        data.put("platform_product", Build.VERSION.RELEASE);
        data.put("platform_type", getDeviceType());

        // Set the root fields
        root.put("id", deviceId);
        root.put("data", data);

        return root;
    }

    /**
     * Gets or generates a unique device identifier
     * @return A unique string identifier for the device
     */
    public String getDeviceId() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(DEVICE_ID_KEY, null);

        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString().replace("-", "");
            prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply();
        }

        return deviceId;
    }

    /**
     * Gets the device's user agent string
     */
    public String getUserAgent() {
        return System.getProperty("http.agent", "android");
    }

    /**
     * Gets the device's current language setting
     */
    public String getDeviceLanguage() {
        return Locale.getDefault().toString();
    }
    /**
     * Gets the screen width in pixels
     */
    public int getScreenWidth() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x;
    }

    /**
     * Gets the screen height in pixels
     */
    public int getScreenHeight() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.y;
    }

    /**
     * Gets the timezone offset in minutes
     */
    public int getTimezoneOffset() {
        TimeZone tz = TimeZone.getDefault();
        Calendar cal = GregorianCalendar.getInstance(tz);
        return tz.getOffset(cal.getTimeInMillis()) / (60 * 1000) * -1;
    }

    /**
     * Determines if the device is a phone, tablet, or desktop
     */
    public String getDeviceType() {
        // Basic detection based on screen size
        int screenLayoutSize = context.getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;

        switch (screenLayoutSize) {
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                return "tablet";
            default:
                return "mobile";
        }
    }

    /**
     * Returns the base64 encoded device fingerprint
     */
    public String getEncodedDeviceFingerprint() {
        try {
            JSONObject fingerprint = createDeviceFingerprint();
            return Base64.encodeToString(fingerprint.toString().getBytes(), Base64.NO_WRAP);
        } catch (JSONException e) {
            // Fallback to a minimal fingerprint if there's an error
            try {
                JSONObject minimal = new JSONObject();
                minimal.put("id", UUID.randomUUID().toString().replace("-", ""));
                minimal.put("data", new JSONObject());
                return Base64.encodeToString(minimal.toString().getBytes(), Base64.NO_WRAP);
            } catch (JSONException ex) {
                return "";
            }
        }
    }
}