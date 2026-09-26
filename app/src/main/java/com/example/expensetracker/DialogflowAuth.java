package com.example.expensetracker;

import android.content.Context;
import android.util.Log;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.InputStream;
import java.util.Collections;

public class DialogflowAuth {
    public static String getAccessToken(Context context) {
        try {
            InputStream stream = context.getResources().openRawResource(R.raw.credentials); // make sure this matches your filename

            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));

            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            Log.e("DialogflowAuth", "Error reading credentials: " + e.getMessage());
            return null;
        }
    }
}
