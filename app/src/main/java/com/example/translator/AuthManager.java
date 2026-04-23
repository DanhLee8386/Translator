package com.example.translator;

import android.content.Context;
import android.content.SharedPreferences;


public class AuthManager {

    private static final String PREFS_NAME = "auth_prefs";
    private static final String KEY_TOKEN    = "jwt_token";
    private static final String KEY_USERNAME = "username";

    private static AuthManager instance;
    private final SharedPreferences prefs;

    private AuthManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }


    public void saveAuth(String token, String username) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USERNAME, username)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public boolean isLoggedIn() {
        String t = getToken();
        return t != null && !t.isEmpty();
    }

    /** Returns "Bearer <token>" ready to put in the Authorization header. */
    public String getBearerToken() {
        return isLoggedIn() ? "Bearer " + getToken() : null;
    }

    public void logout() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USERNAME).apply();
    }
}