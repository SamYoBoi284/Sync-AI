package com.sam.syncai;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Set;
import java.util.Locale;

/** Applies saved speech preferences to each fresh TextToSpeech session. */
public final class SpeechPreferences {
    private SpeechPreferences() {}

    public static Locale recognitionLocale(Context context) {
        String tag = new AppPreferences(context).getRecognitionLanguageTag();
        return tag == null || tag.trim().isEmpty() ? Locale.getDefault() : Locale.forLanguageTag(tag);
    }

    public static void apply(TextToSpeech engine, Context context) {
        if (engine == null || context == null) return;
        AppPreferences prefs = new AppPreferences(context);
        engine.setSpeechRate(prefs.getSpeechRate());
        engine.setPitch(prefs.getSpeechPitch());
        String languageTag = prefs.getTtsLanguageTag();
        Locale requestedLocale = languageTag.isEmpty()
                ? Locale.getDefault() : Locale.forLanguageTag(languageTag);
        int languageResult = engine.setLanguage(requestedLocale);
        if (languageResult == TextToSpeech.LANG_MISSING_DATA
                || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            SyncEventLogger.record(context, "SpeechPreferences", "TTS_LANGUAGE_UNAVAILABLE",
                    "WARN", "requested=" + requestedLocale.toLanguageTag()
                            + "; falling back to device default");
            engine.setLanguage(Locale.getDefault());
        } else {
            SyncEventLogger.record(context, "SpeechPreferences", "TTS_LANGUAGE_APPLIED",
                    "INFO", "requested=" + requestedLocale.toLanguageTag());
        }
        String preferredName = prefs.getSpeechVoiceName();
        if (preferredName.isEmpty()) return;

        try {
            Set<Voice> voices = engine.getVoices();
            if (voices != null) {
                for (Voice voice : voices) {
                    if (voice != null && preferredName.equals(voice.getName())) {
                        engine.setVoice(voice);
                        SyncEventLogger.record(context, "SpeechPreferences", "VOICE_APPLIED",
                                "INFO", "voice=" + preferredName);
                        return;
                    }
                }
            }
            SyncEventLogger.record(context, "SpeechPreferences", "VOICE_NOT_AVAILABLE",
                    "WARN", "Selected TTS voice is not installed in the active engine; using engine default.");
        } catch (Exception error) {
            SyncEventLogger.recordException(context, "SpeechPreferences",
                    "VOICE_APPLY_FAILED", error, "using engine default");
        }
    }
}
