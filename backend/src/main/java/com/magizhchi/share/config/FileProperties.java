package com.magizhchi.share.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the 'file:' block from application.yml.
 * Using @ConfigurationProperties (not @Value) so YAML lists work correctly.
 */
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    private long maxSizeBytes = 524288000L;   // 500 MB default
    private List<String> allowedTypes = new ArrayList<>();

    public long getMaxSizeBytes()            { return maxSizeBytes; }
    public void setMaxSizeBytes(long v)      { this.maxSizeBytes = v; }

    public List<String> getAllowedTypes()            { return allowedTypes; }
    public void setAllowedTypes(List<String> types)  { this.allowedTypes = types; }
}
