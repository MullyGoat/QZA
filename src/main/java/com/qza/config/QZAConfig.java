package com.qza.config;

/**
 * Plain data holder, serialised straight to config/qza/config.json by Gson.
 * Field names are the JSON keys, so renaming one resets that option.
 */
public class QZAConfig {

    // ---------------------------------------------------------------- Shitter List
    /** Master switch. When false, listed players are never kicked. */
    public boolean shitterListEnabled = true;
    /** Only react to "joined the dungeon group!" lines, ignore normal party joins. */
    public boolean restrictToDungeonGroups = false;

    // ---------------------------------------------------------------- F7 / M7
    /** Master switch for the terminal-phase music. */
    public boolean terminalMusicEnabled = true;
    /**
     * Substring that starts the music: Goldor's phase-start line, i.e. the
     * beginning of the terminal phase.
     * "[BOSS] Goldor: Who dares trespass into my domain?"
     */
    public String musicStartTrigger = "Who dares trespass into my domain";
    /**
     * Substring that stops the music: terminals done and the core opening.
     * "The Core entrance is opening!"
     */
    public String musicStopTrigger = "The Core entrance is opening";

    // ---------------------------------------------------------------- Music
    /** true = pick a random track each run. false = always play {@link #selectedTrack}. */
    public boolean shuffleMode = true;
    /** File name (not full path) of the track used when shuffle is off. */
    public String selectedTrack = "";
    /** 0-100, applied in software before the samples hit the audio device. */
    public double musicVolume = 60.0;
    /** Fade in / fade out length in milliseconds. */
    public double fadeMillis = 1500.0;

    // ---------------------------------------------------------------- Miscellaneous
    /** GUI size as a percentage. 100 = the original layout. */
    public double guiScale = 100.0;
}
