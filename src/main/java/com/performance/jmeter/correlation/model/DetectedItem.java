package com.performance.jmeter.correlation.model;

public class DetectedItem {
    private final String type;
    private final String variableName;
    private final String source;
    private final boolean usageConfirmed;

    public DetectedItem(String type, String variableName, String source, boolean usageConfirmed) {
        this.type = type;
        this.variableName = variableName;
        this.source = source;
        this.usageConfirmed = usageConfirmed;
    }

    public String getType() {
        return type;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getSource() {
        return source;
    }

    public boolean isUsageConfirmed() {
        return usageConfirmed;
    }

    @Override
    public String toString() {
        String status = usageConfirmed ? "✓" : "⚠";
        return status + " " + type + " - " + variableName + " (" + source + ")";
    }
}
