package com.qza.shitter;

import com.google.gson.annotations.SerializedName;

public class ShitterEntry {
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
