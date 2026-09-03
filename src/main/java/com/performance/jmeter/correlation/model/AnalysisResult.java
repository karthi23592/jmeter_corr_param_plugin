package com.performance.jmeter.correlation.model;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {
    private ConfigurationStatus status;
    private final List<DetectedItem> detectedItems;
    private final List<String> warnings;

    public AnalysisResult() {
        this.status = ConfigurationStatus.NOT_CONFIGURED;
        this.detectedItems = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public AnalysisResult(ConfigurationStatus status) {
        this.status = status;
        this.detectedItems = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public ConfigurationStatus getStatus() {
        return status;
    }

    public void setStatus(ConfigurationStatus status) {
        this.status = status;
    }

    public List<DetectedItem> getDetectedItems() {
        return detectedItems;
    }

    public void addDetectedItem(DetectedItem item) {
        this.detectedItems.add(item);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public boolean hasItems() {
        return !detectedItems.isEmpty();
    }
}
