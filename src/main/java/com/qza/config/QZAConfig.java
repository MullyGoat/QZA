package com.qza.config;

public class QZAConfig {
    public boolean shitterListEnabled = true;

    public boolean restrictToDungeonGroups = false;

    public boolean terminalMusicEnabled = true;

    public String musicStartTrigger = "Who dares trespass into my domain";

    public String musicStopTrigger = "The Core entrance is opening";

    public boolean shuffleMode = true;

    public String selectedTrack = "";

    public double musicVolume = 60.0;

    public double fadeMillis = 1500.0;

    public boolean necronTimerEnabled = true;

    public String necronStartTrigger = "You went further than any human before";

    public String necronDeathTrigger = "Necron: ARGH";

    public int necronDeathTriggerCount = 2;

    public String necronAnnounceMode = "party";

    public boolean partyInviteNotifyEnabled = true;

    public double partyNotifyDuration = 5.0;

    public double partyNotifyX = 0.5;

    public double partyNotifyY = 0.28;

    public double partyNotifyScale = 1.0;

    public boolean qzaChatEnabled = true;

    public String chatHistoryMode = "forever";

    public String chatDefaultTab = "everything";

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

    public double guiScale = 100.0;
}
