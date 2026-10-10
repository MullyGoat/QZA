package com.qza.config;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    public String necronAnnounceMode = "party";

    public boolean necronDebugTimer = false;

    public boolean waypointsEnabled = false;

    public boolean waypointsDungeonOnly = true;

    public boolean waypointShowNames = false;

    public boolean witherKeyEnabled = false;

    public double witherKeyX = 0.5;

    public double witherKeyY = 0.42;

    public double witherKeyScale = 1.0;

    public boolean splitTimersEnabled = false;

    public boolean splitTimersTickTime = true;

    public boolean splitTimersBossEntry = false;

    public double splitTimersX = 0.1;

    public double splitTimersY = 0.32;

    public double splitTimersScale = 1.0;

    public boolean lagTimerEnabled = false;

    public String lagAnnounceMode = "party";

    public boolean leapNotifyEnabled = false;

    public boolean leapNotifyClass = false;

    public boolean leapNotifyBossOnly = false;

    public double leapNotifyDuration = 2.0;

    public double leapNotifyX = 0.5;

    public double leapNotifyY = 0.36;

    public double leapNotifyScale = 1.5;

    public boolean pyTimerEnabled = false;

    public double pyTimerX = 0.5;

    public double pyTimerY = 0.56;

    public double pyTimerScale = 1.5;

    public boolean necronLeapEnabled = false;

    public double necronLeapX = 0.5;

    public double necronLeapY = 0.66;

    public double necronLeapScale = 2.0;

    public boolean crystalTimerEnabled = false;

    public double crystalTimerX = 0.5;

    public double crystalTimerY = 0.48;

    public double crystalTimerScale = 1.0;

    public boolean starredMobsEnabled = false;

    public String starredMobColour = "yellow";

    public boolean starredMobsBats = true;

    public String starredMobsBatColour = "lime";

    public boolean starredMobsFels = false;

    public String starredMobsFelColour = "pink";

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

    public boolean autoInviteWhileQueued = false;

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

    public boolean clockEnabled = false;

    public String clockFormat = "12";

    public double clockX = 0.5;

    public double clockY = 0.08;

    public double clockScale = 1.0;

    public double guiScale = 100.0;

    public boolean itemListEnabled = true;

    public boolean itemListShown = true;

    public boolean itemListInventory = true;

    public boolean newGui = true;

    public boolean hotbarScrollLock = false;

    public boolean tooltipScaleEnabled = false;

    public String tooltipScaleMode = "dynamic";

    public double tooltipScale = 1.0;

    public boolean commandKeybindsEnabled = false;

    public boolean commandKeybindsInMenus = false;

    public List<com.qza.keybind.CommandBind> commandBinds = new ArrayList<>();

    public boolean playerSizeEnabled = false;

    public double playerSizeX = 1.0;

    public double playerSizeY = 1.0;

    public double playerSizeZ = 1.0;

    public boolean equipmentInInventory = false;

    public boolean equipmentRarity = true;

    public boolean hideShieldSlot = false;

    public boolean hideCrafting = false;

    public boolean itemStarCount = false;

    public String itemStarColour = "magenta";

    public double itemStarScale = 100.0;

    @SerializedName(value = "commandShortcutsEnabled", alternate = "dungeonWarpShortcut")
    public boolean commandShortcutsEnabled = false;

    public List<com.qza.tweaks.CommandShortcut> commandShortcuts =
            new ArrayList<>(List.of(new com.qza.tweaks.CommandShortcut("d", "warp dungeons")));

    public boolean marketSearchCommands = false;

    public List<String> auctionSearchHistory = new ArrayList<>();

    public List<String> bazaarSearchHistory = new ArrayList<>();
}
