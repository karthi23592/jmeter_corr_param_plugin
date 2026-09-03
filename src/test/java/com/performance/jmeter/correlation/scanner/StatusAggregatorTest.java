package com.performance.jmeter.correlation.scanner;

import com.performance.jmeter.correlation.model.AnalysisResult;
import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.ElementStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusAggregatorTest {

    @Test
    void aggregate_allConfigured_returnsConfigured() {
        ElementStatus parent = new ElementStatus("Thread Group", "ThreadGroup", "/tg");
        parent.addChild(createSampler("S1", ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED));
        parent.addChild(createSampler("S2", ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED));
        parent.addChild(createSampler("S3", ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED));

        StatusAggregator.aggregate(parent);

        assertEquals(ConfigurationStatus.CONFIGURED, parent.getEffectiveCorrelationStatus());
        assertEquals(ConfigurationStatus.CONFIGURED, parent.getEffectiveParameterizationStatus());
    }

    @Test
    void aggregate_noneConfigured_returnsNotConfigured() {
        ElementStatus parent = new ElementStatus("Thread Group", "ThreadGroup", "/tg");
        parent.addChild(createSampler("S1", ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.NOT_CONFIGURED));
        parent.addChild(createSampler("S2", ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.NOT_CONFIGURED));

        StatusAggregator.aggregate(parent);

        assertEquals(ConfigurationStatus.NOT_CONFIGURED, parent.getEffectiveCorrelationStatus());
        assertEquals(ConfigurationStatus.NOT_CONFIGURED, parent.getEffectiveParameterizationStatus());
    }

    @Test
    void aggregate_mixed_returnsConfigured() {
        // Aggregation rule: if at least one child is configured, parent shows CONFIGURED (green)
        ElementStatus parent = new ElementStatus("Thread Group", "ThreadGroup", "/tg");
        parent.addChild(createSampler("S1", ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED));
        parent.addChild(createSampler("S2", ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.CONFIGURED));
        parent.addChild(createSampler("S3", ConfigurationStatus.CONFIGURED, ConfigurationStatus.NOT_CONFIGURED));

        StatusAggregator.aggregate(parent);

        assertEquals(ConfigurationStatus.CONFIGURED, parent.getEffectiveCorrelationStatus());
        assertEquals(ConfigurationStatus.CONFIGURED, parent.getEffectiveParameterizationStatus());
    }

    @Test
    void aggregate_noChildren_keepsDefaults() {
        ElementStatus parent = new ElementStatus("Empty", "ThreadGroup", "/empty");
        StatusAggregator.aggregate(parent);
        // No children means no change
        assertEquals(ConfigurationStatus.NOT_CONFIGURED, parent.getEffectiveCorrelationStatus());
    }

    @Test
    void computeSummary_countsCorrectly() {
        ElementStatus root = new ElementStatus("Test Plan", "TestPlan", "/");

        ElementStatus tg = new ElementStatus("TG1", "ThreadGroup", "/tg1");
        tg.addChild(createSampler("S1", ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED));
        tg.addChild(createSampler("S2", ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.PARTIAL));
        tg.addChild(createSampler("S3", ConfigurationStatus.PARTIAL, ConfigurationStatus.NOT_CONFIGURED));

        root.addChild(tg);

        StatusAggregator.ScanSummary summary = StatusAggregator.computeSummary(root);

        assertEquals(1, summary.totalThreadGroups);
        assertEquals(3, summary.totalSamplers);
        assertEquals(1, summary.correlationConfigured);
        assertEquals(1, summary.correlationPartial);
        assertEquals(1, summary.correlationNotConfigured);
        assertEquals(1, summary.parameterizationConfigured);
        assertEquals(1, summary.parameterizationPartial);
        assertEquals(1, summary.parameterizationNotConfigured);
    }

    @Test
    void computeSummary_coverageCalculation() {
        ElementStatus root = new ElementStatus("Test Plan", "TestPlan", "/");
        ElementStatus tg = new ElementStatus("TG1", "ThreadGroup", "/tg1");

        tg.addChild(createSampler("S1", ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED));
        tg.addChild(createSampler("S2", ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED));
        tg.addChild(createSampler("S3", ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.NOT_CONFIGURED));
        tg.addChild(createSampler("S4", ConfigurationStatus.CONFIGURED, ConfigurationStatus.NOT_CONFIGURED));

        root.addChild(tg);

        StatusAggregator.ScanSummary summary = StatusAggregator.computeSummary(root);

        assertEquals(75.0, summary.getCorrelationCoverage(), 0.01);
        assertEquals(50.0, summary.getParameterizationCoverage(), 0.01);
    }

    private ElementStatus createSampler(String name, ConfigurationStatus corrStatus, ConfigurationStatus paramStatus) {
        ElementStatus sampler = new ElementStatus(name, "HTTPSampler", "/" + name);
        sampler.setCorrelationResult(new AnalysisResult(corrStatus));
        sampler.setParameterizationResult(new AnalysisResult(paramStatus));
        return sampler;
    }
}
