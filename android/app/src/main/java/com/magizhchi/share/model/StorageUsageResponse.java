package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

public class StorageUsageResponse {

    @SerializedName("usedBytes")
    private long usedBytes;

    @SerializedName("limitBytes")
    private long limitBytes;

    @SerializedName("usedPercent")
    private double usedPercent;

    public StorageUsageResponse() {}

    public long getUsedBytes() { return usedBytes; }
    public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }

    public long getLimitBytes() { return limitBytes; }
    public void setLimitBytes(long limitBytes) { this.limitBytes = limitBytes; }

    public double getUsedPercent() { return usedPercent; }
    public void setUsedPercent(double usedPercent) { this.usedPercent = usedPercent; }
}
