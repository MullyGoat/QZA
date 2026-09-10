package com.qza.shitter;

import com.google.gson.annotations.SerializedName;

public class ShitterEntry {

    /**
     * Accepts "ign" as well as "name" so a shitterlist.json from the old 1.8.9
     * ChatTriggers version of QZA can be dropped straight in. That module wrote
     * entries as {"ign": "...", "reason": "..."}. Always re-saved as "name".
     */
    @SerializedName(value = "name", alternate = {"ign"})
    public String name;

    public String reason;
    public long addedAt;

    public ShitterEntry() {
    }

    public ShitterEntry(String name, String reason) {
        this.name = name;
        this.reason = reason;
        this.addedAt = System.currentTimeMillis();
    }

    public String reasonOrDefault() {
        return (reason == null || reason.isBlank()) ? "No reason given" : reason;
    }
}
