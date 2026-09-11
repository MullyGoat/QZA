package com.qza.shitter;

import com.google.gson.annotations.SerializedName;

public class ShitterEntry {

    @SerializedName(value = "name", alternate = {"ign"})
    public String name;

    public String reason;
    public String uuid;
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

    public boolean hasUuid() {
        return uuid != null && !uuid.isBlank();
    }
}
