package com.qza.config;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QZAConfig {
    public boolean shitterListEnabled = false;

    public boolean restrictToDungeonGroups = false;

    public boolean terminalMusicEnabled = false;

    public String musicStartTrigger = "Who dares trespass into my domain";

    public String musicStopTrigger = "The Core entrance is opening";

    public boolean shuffleMode = true;

    public String selectedTrack = "";

    public Map<String, String> trackNames = new LinkedHashMap<>();

    public Map<String, com.qza.music.TrackTrim> trackTrims = new LinkedHashMap<>();

    public double musicVolume = 60.0;

    public double fadeMillis = 1500.0;

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

    public boolean terminalTrackerEnabled = false;

    public boolean terminalDoneAlert = true;

    public boolean terminalDoneParty = true;

    public double terminalTrackerX = 0.9;

    public double terminalTrackerY = 0.82;

    public double terminalTrackerScale = 1.0;

    public boolean lagTimerEnabled = false;

    public String lagAnnounceMode = "party";

    public boolean score300Alert = false;

    public boolean score300Party = true;

    public boolean scoreBonusAnnounce = false;

    public boolean scoreDebugLog = false;

    public boolean scoreHudEnabled = false;

    public boolean scoreHudBreakdown = false;

    public double scoreHudX = 0.1;

    public double scoreHudY = 0.12;

    public double scoreHudScale = 1.0;

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

    public boolean stormTimerEnabled = false;

    public boolean stormTimerCountDown = false;

    public String stormTimerColour = "white";

    public double stormTimerX = 0.5;

    public double stormTimerY = 0.61;

    public double stormTimerScale = 1.5;

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

    public boolean partyInviteNotifyEnabled = false;

    public double partyNotifyDuration = 5.0;

    public double partyNotifyX = 0.5;

    public double partyNotifyY = 0.28;

    public double partyNotifyScale = 1.0;

    public boolean qzaChatEnabled = false;

    public String chatHistoryMode = "forever";

    public String chatDefaultTab = "everything";

    public boolean chatUnlimitedHistory = false;

    public boolean hideVanillaChat = false;

    public boolean chatBottomLeft = false;

    public boolean chatQzaStyle = false;

    public double chatDockTextScale = 85.0;

    public boolean chatHideUseless = false;

    public boolean chatHideSeparators = false;

    public boolean openChatWithT = false;

    public int chatKeyCode = -1;

    public boolean chatNotifyEnabled = false;

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

    public boolean itemListEnabled = false;

    public boolean itemListShown = true;

    public boolean itemListInventory = true;

    public boolean newGui = true;

    public boolean hotbarScrollLock = false;

    public boolean tooltipScaleEnabled = false;

    public String tooltipScaleMode = "dynamic";

    public double tooltipScale = 1.0;

    public boolean itemPricesEnabled = false;

    public boolean itemPriceBazaar = true;

    public boolean itemPriceLowestBin = true;

    public String itemPriceAverage = "three_day";

    public boolean itemPriceValue = true;

    public boolean commandKeybindsEnabled = false;

    public boolean commandKeybindsInMenus = false;

    public List<com.qza.keybind.CommandBind> commandBinds = new ArrayList<>();

    public boolean playerSizeEnabled = false;

    public double playerSizeX = 1.0;

    public double playerSizeY = 1.0;

    public double playerSizeZ = 1.0;

    public boolean equipmentInInventory = false;

    public boolean equipmentRarity = true;

    public boolean storagePreview = false;

    public boolean musicOverlayEnabled = false;

    public double musicOverlayX = 0.86;

    public double musicOverlayY = 0.08;

    public double musicOverlayScale = 1.0;

    public boolean musicOverlayCover = true;

    public boolean hideShieldSlot = false;

    public boolean hideCrafting = false;

    public boolean noLoadingScreen = false;

    public boolean noLoadingScreenServersOnly = true;

    public boolean fullBright = false;

    public String fullBrightMode = "ambient";

    public boolean fullBrightHideNightVision = false;

    public boolean itemProtectionEnabled = false;

    public String itemProtectionKey = "";

    public boolean itemProtectionMessages = true;

    public boolean itemProtectionStar = true;

    public String itemProtectionCorner = "top_right";

    public List<String> protectedItems = new ArrayList<>();

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
