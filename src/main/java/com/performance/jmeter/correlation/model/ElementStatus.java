package com.performance.jmeter.correlation.model;

import java.util.ArrayList;
import java.util.List;

public class ElementStatus {
    private final String elementName;
    private final String elementType;
    private final String path;
    private AnalysisResult correlationResult;
    private AnalysisResult parameterizationResult;
    private ConfigurationStatus manualCorrelationOverride;
    private ConfigurationStatus manualParameterizationOverride;
    private final List<ElementStatus> children;

    public ElementStatus(String elementName, String elementType, String path) {
        this.elementName = elementName;
        this.elementType = elementType;
        this.path = path;
        this.correlationResult = new AnalysisResult();
        this.parameterizationResult = new AnalysisResult();
        this.children = new ArrayList<>();
    }

    public String getElementName() {
        return elementName;
    }

    public String getElementType() {
        return elementType;
    }

    public String getPath() {
        return path;
    }

    public AnalysisResult getCorrelationResult() {
        return correlationResult;
    }

    public void setCorrelationResult(AnalysisResult correlationResult) {
        this.correlationResult = correlationResult;
    }

    public AnalysisResult getParameterizationResult() {
        return parameterizationResult;
    }

    public void setParameterizationResult(AnalysisResult parameterizationResult) {
        this.parameterizationResult = parameterizationResult;
    }

    public ConfigurationStatus getEffectiveCorrelationStatus() {
        if (manualCorrelationOverride != null) {
            return manualCorrelationOverride;
        }
        return correlationResult.getStatus();
    }

    public ConfigurationStatus getEffectiveParameterizationStatus() {
        if (manualParameterizationOverride != null) {
            return manualParameterizationOverride;
        }
        return parameterizationResult.getStatus();
    }

    public void setManualCorrelationOverride(ConfigurationStatus override) {
        this.manualCorrelationOverride = override;
    }

    public void setManualParameterizationOverride(ConfigurationStatus override) {
        this.manualParameterizationOverride = override;
    }

    public ConfigurationStatus getManualCorrelationOverride() {
        return manualCorrelationOverride;
    }

    public ConfigurationStatus getManualParameterizationOverride() {
        return manualParameterizationOverride;
    }

    public List<ElementStatus> getChildren() {
        return children;
    }

    public void addChild(ElementStatus child) {
        this.children.add(child);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
