package com.magizhchi.share.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the 'file:' block from application.yml.
 * Uses a BLOCK-list approach: all types are allowed except those in blockedTypes.
 */
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    private long maxSizeBytes = 524288000L;   // 500 MB default

    /** MIME types that are explicitly blocked (executables, installers, scripts). */
    private List<String> blockedTypes = new ArrayList<>();

    public long getMaxSizeBytes()               { return maxSizeBytes; }
    public void setMaxSizeBytes(long v)         { this.maxSizeBytes = v; }

    public List<String> getBlockedTypes()               { return blockedTypes; }
    public void setBlockedTypes(List<String> types)     { this.blockedTypes = types; }
}
