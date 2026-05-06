package com.example.towncrierbd.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

public class NetworkMonitor {

    public interface NetworkCallback {
        void onAvailable();
        void onLost();
    }

    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NetworkCallback listener;

    // ✅ FIX: debounce state so rapid onAvailable/onLost calls don't cause banner flicker
    private boolean lastKnownConnected = true;
    private Runnable pendingCallback = null;
    private static final long DEBOUNCE_MS = 500;

    public NetworkMonitor(Context context) {
        connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        lastKnownConnected = isConnected();
    }

    public boolean isConnected() {
        if (connectivityManager == null) return false;
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public void startMonitoring(NetworkCallback callback) {
        this.listener = callback;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // ✅ FIX: debounce and only fire if state actually changed
                postDebounced(() -> {
                    if (!lastKnownConnected) {
                        lastKnownConnected = true;
                        if (listener != null) listener.onAvailable();
                    }
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                // ✅ FIX: debounce — device may switch networks briefly
                postDebounced(() -> {
                    // Re-check actual connectivity before declaring lost
                    if (!isConnected() && lastKnownConnected) {
                        lastKnownConnected = false;
                        if (listener != null) listener.onLost();
                    }
                });
            }
        };

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (Exception ignored) {}
    }

    private void postDebounced(Runnable action) {
        if (pendingCallback != null) mainHandler.removeCallbacks(pendingCallback);
        pendingCallback = action;
        mainHandler.postDelayed(pendingCallback, DEBOUNCE_MS);
    }

    public void stopMonitoring() {
        if (pendingCallback != null) {
            mainHandler.removeCallbacks(pendingCallback);
            pendingCallback = null;
        }
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
            networkCallback = null;
        }
        listener = null;
    }
}