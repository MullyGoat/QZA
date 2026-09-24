package com.qza.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class QZAConfig {
    public boolean shitterListEnabled = true;

    public boolean restrictToDungeonGroups = false;

    public boolean terminalMusicEnabled = true;

    public String musicStartTrigger = "Who dares trespass into my domain";

    public String musicStopTrigger = "The Core entrance is opening";

    public boolean shuffleMode = true;

    public String selectedTrack = "";

    public Map<String, String> trackNames = new LinkedHashMap<>();

    public Map<String, com.qza.music.TrackTrim> trackTrims = new LinkedHashMap<>();

    public double musicVolume = 60.0;

    public double fadeMillis = 1500.0;

    public boolean necronTimerEnabled = true;

    public String necronStartTrigger = "You went further than any human before";

    public String necronDeathTrigger = "Necron: ARGH";

    public int necronDeathTriggerCount = 2;

    public String necronAnnounceMode = "party";

    public boolean waypointsEnabled = false;

    public boolean waypointsDungeonOnly = true;

    public boolean waypointShowNames = false;

    public boolean terminalGuiEnabled = false;

    public Map<String, String> terminalTemplates = new LinkedHashMap<>();

    public com.qza.terminal.TerminalTemplate terminalCustom =
            new com.qza.terminal.TerminalTemplate();

    public boolean terminalNumbersLimit = false;

    public int terminalNumbersShown = 3;

    public boolean terminalHideDone = true;

    public boolean terminalMelodyHold = true;

    public boolean terminalMelodyLineUp = true;

    public boolean terminalMelodyAim = false;

    public boolean terminalRubixHints = true;

    public boolean terminalFirstClickProt = false;

    public int terminalFirstClickMs = 500;

    public boolean witherKeyEnabled = false;

    public double witherKeyX = 0.5;

    public double witherKeyY = 0.42;

    public double witherKeyScale = 1.0;

    public boolean partyInviteNotifyEnabled = true;

    public double partyNotifyDuration = 5.0;

    public double partyNotifyX = 0.5;

    public double partyNotifyY = 0.28;

    public double partyNotifyScale = 1.0;

    public boolean qzaChatEnabled = true;

    public String chatHistoryMode = "forever";

    public String chatDefaultTab = "everything";

    public boolean chatUnlimitedHistory = false;

    public boolean hideVanillaChat = false;

    public boolean openChatWithT = false;

    public int chatKeyCode = -1;

    public boolean chatNotifyEnabled = true;

    public String chatNotifyMode = "ringer";

    public double chatNotifyDuration = 5.0;

    public double chatNotifyX = 0.5;

    public double chatNotifyY = 0.16;

    public double chatNotifyScale = 1.0;

    public boolean dungeonOnlyNotifications = false;

    public String dungeonOnlyScope = "both";

    public boolean autoInviteEnabled = false;

    public boolean autoInviteRespond = false;

    public double autoInviteCataReq = 40.0;

    public String autoInviteFloor = "m7";

    public double autoInvitePbSeconds = 0.0;

    public String statsProxyUrl = "";

    public boolean discordAlertEnabled = false;

    public boolean discordAlertAlways = false;

    public boolean discordAlertDm = true;

    public boolean discordAlertChannel = true;

    public String discordAlertUrl = "";

    public String discordAlertToken = "";

    public double guiScale = 100.0;
}
