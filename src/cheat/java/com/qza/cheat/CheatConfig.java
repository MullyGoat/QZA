package com.qza.cheat;

import java.util.LinkedHashMap;
import java.util.Map;

public class CheatConfig {
    public boolean deathBowEnabled = false;

    public boolean deathBowHotbarSwap = false;

    public double deathBowHotbarSlot = 1.0;

    public boolean deathBowRaiderSwap = false;

    public double deathBowRaiderSlot = 1.0;

    public double deathBowCloseDelay = 5.0;

    public boolean melodyAimEnabled = false;

    public boolean bloodClickEnabled = false;

    public double bloodClickRange = 20.0;

    public boolean bloodClickPreFire = false;

    public double bloodClickPreFireSeconds = 0.05;

    public boolean bloodClickWhitelist = false;

    public boolean bloodClickDebug = false;

    public Map<String, String> bloodClickItems = new LinkedHashMap<>();

    public boolean starredMobEsp = false;

    public boolean movementCopierEnabled = false;

    public String movementCopierKey = "";

    public double movementCountdown = 3.0;

    public boolean movementPlaceStart = true;

    public double movementLagTolerance = 5.0;

    public boolean movementHighlight = true;

    public boolean movementThroughWalls = true;

    public double movementWalkSpeed = 5.0;

    public double movementTurnSpeed = 1.0;

    public boolean movementTurnOvershoot = true;

    public double movementOvershootDegrees = 0.0;

    public boolean movementMatchSpeed = false;

    public double movementPlaySpeed = 0.0;

    public boolean movementDebug = false;

    public boolean autoClickerEnabled = false;

    public double autoClickerCps = 10.0;

    public Map<String, com.qza.cheat.click.ClickItem> autoClickerItems = new LinkedHashMap<>();
}
