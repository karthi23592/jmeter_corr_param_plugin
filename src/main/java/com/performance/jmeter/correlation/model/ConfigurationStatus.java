package com.performance.jmeter.correlation.model;

public enum ConfigurationStatus {
    CONFIGURED("✓", "Configured"),
    PARTIAL("⚠", "Partial"),
    NOT_CONFIGURED("□", "Not Configured"),
    NOT_APPLICABLE("—", "N/A"),
    UNKNOWN("?", "Unknown");

    private final String symbol;
    private final String label;

    ConfigurationStatus(String symbol, String label) {
        this.symbol = symbol;
        this.label = label;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return symbol + " " + label;
    }
}
