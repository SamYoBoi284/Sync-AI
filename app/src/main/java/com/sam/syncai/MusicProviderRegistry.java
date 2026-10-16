package com.sam.syncai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Conservative capability registry for music controls.
 *
 * Android media-key transport is available independently of a provider-specific API.
 * Advanced controls remain unavailable unless an explicit provider adapter is registered;
 * a package name alone is not evidence that shuffle, repeat, playlists, or app-specific
 * actions can be controlled externally.
 */
public final class MusicProviderRegistry {
    public enum Capability { PLAY_PAUSE, NEXT_PREVIOUS, SHUFFLE, REPEAT, PLAYLISTS, APP_SPECIFIC }

    public static final class Provider {
        public final String packageName;
        public final String label;
        public final boolean launchable;

        private Provider(String packageName, String label, boolean launchable) {
            this.packageName = packageName;
            this.label = label;
            this.launchable = launchable;
        }

        public boolean supports(Capability capability) {
            if (capability == Capability.PLAY_PAUSE || capability == Capability.NEXT_PREVIOUS) {
                return true; // best-effort Android media-key dispatch, not provider acknowledgement
            }
            return false; // no provider-specific adapters have been verified yet
        }
    }

    private final Context context;

    public MusicProviderRegistry(Context context) {
        this.context = context.getApplicationContext();
    }

    public Provider selectedProvider() {
        SharedPreferences prefs = context.getSharedPreferences("sync_media_preferences", Context.MODE_PRIVATE);
        String packageName = prefs.getString("preferred_music_package", null);
        String label = prefs.getString("preferred_music_label", "selected music app");
        if (packageName == null || packageName.trim().isEmpty()) {
            return new Provider(null, "active music player", false);
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            prefs.edit().remove("preferred_music_package").remove("preferred_music_label").apply();
            return new Provider(null, "active music player", false);
        }
        return new Provider(packageName, label, true);
    }

    public String unsupportedMessage(Capability capability) {
        Provider provider = selectedProvider();
        String feature;
        switch (capability) {
            case SHUFFLE: feature = "shuffle"; break;
            case REPEAT: feature = "repeat"; break;
            case PLAYLISTS: feature = "playlist management"; break;
            default: feature = "that app-specific control"; break;
        }
        String target = provider.launchable ? provider.label : "the active music player";
        return "I can't verify that " + target + " exposes " + feature + " control to Sync AI, so I haven't changed it. "
                + (provider.launchable ? "You can open your selected music app and use its own control." :
                "Choose a music app in Settings > Default apps to enable provider-specific routing when supported.");
    }
}
