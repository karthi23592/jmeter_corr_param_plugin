package com.performance.jmeter.correlation.scanner;

import com.performance.jmeter.correlation.model.AnalysisResult;
import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.ElementStatus;

import java.util.List;

public class StatusAggregator {

    public static void aggregate(ElementStatus parent) {
        List<ElementStatus> children = parent.getChildren();
        if (children.isEmpty()) {
            return;
        }

        ConfigurationStatus correlationAgg = aggregateStatus(children, true);
        ConfigurationStatus paramAgg = aggregateStatus(children, false);

        AnalysisResult correlationResult = new AnalysisResult(correlationAgg);
        AnalysisResult paramResult = new AnalysisResult(paramAgg);

        parent.setCorrelationResult(correlationResult);
        parent.setParameterizationResult(paramResult);
    }

    private static ConfigurationStatus aggregateStatus(List<ElementStatus> children, boolean isCorrelation) {
        int configured = 0;
        int applicable = 0;

        for (ElementStatus child : children) {
            ConfigurationStatus status = isCorrelation
                    ? child.getEffectiveCorrelationStatus()
                    : child.getEffectiveParameterizationStatus();

            if (status == ConfigurationStatus.NOT_APPLICABLE) continue;

            applicable++;
            if (status == ConfigurationStatus.CONFIGURED || status == ConfigurationStatus.PARTIAL) {
                configured++;
            }
        }

        // If even one child is configured, mark parent as configured (green)
        if (configured > 0) {
            return ConfigurationStatus.CONFIGURED;
        }
        // If no applicable children at all, mark as N/A
        if (applicable == 0) {
            return ConfigurationStatus.NOT_APPLICABLE;
        }
        // None configured
        return ConfigurationStatus.NOT_CONFIGURED;
    }

    public static ScanSummary computeSummary(ElementStatus root) {
        ScanSummary summary = new ScanSummary();
        computeSummaryRecursive(root, summary);
        return summary;
    }

    private static void computeSummaryRecursive(ElementStatus element, ScanSummary summary) {
        if ("ThreadGroup".equals(element.getElementType())) {
            summary.totalThreadGroups++;
        }

        if (isSamplerType(element.getElementType())) {
            ConfigurationStatus corrStatus = element.getEffectiveCorrelationStatus();
            ConfigurationStatus paramStatus = element.getEffectiveParameterizationStatus();

            // Skip N/A samplers from counts
            if (corrStatus == ConfigurationStatus.NOT_APPLICABLE &&
                paramStatus == ConfigurationStatus.NOT_APPLICABLE) {
                summary.notApplicable++;
            } else {
                summary.totalSamplers++;

                switch (corrStatus) {
                    case CONFIGURED:
                        summary.correlationConfigured++;
                        break;
                    case PARTIAL:
                        summary.correlationPartial++;
                        break;
                    case NOT_CONFIGURED:
                        summary.correlationNotConfigured++;
                        break;
                    case NOT_APPLICABLE:
                        summary.correlationNA++;
                        break;
                    default:
                        break;
                }

                switch (paramStatus) {
                    case CONFIGURED:
                        summary.parameterizationConfigured++;
                        break;
                    case PARTIAL:
                        summary.parameterizationPartial++;
                        break;
                    case NOT_CONFIGURED:
                        summary.parameterizationNotConfigured++;
                        break;
                    case NOT_APPLICABLE:
                        summary.parameterizationNA++;
                        break;
                    default:
                        break;
                }
            }
        }

        for (ElementStatus child : element.getChildren()) {
            computeSummaryRecursive(child, summary);
        }
    }

    private static boolean isSamplerType(String elementType) {
        return "HTTPSampler".equals(elementType) || "Sampler".equals(elementType);
    }

    public static class ScanSummary {
        public int totalThreadGroups;
        public int totalSamplers;
        public int notApplicable;
        public int correlationConfigured;
        public int correlationPartial;
        public int correlationNotConfigured;
        public int correlationNA;
        public int parameterizationConfigured;
        public int parameterizationPartial;
        public int parameterizationNotConfigured;
        public int parameterizationNA;

        public double getCorrelationCoverage() {
            int applicable = totalSamplers - correlationNA;
            if (applicable <= 0) return 100;
            return (double) correlationConfigured / applicable * 100;
        }

        public double getParameterizationCoverage() {
            int applicable = totalSamplers - parameterizationNA;
            if (applicable <= 0) return 100;
            return (double) parameterizationConfigured / applicable * 100;
        }
    }
}
