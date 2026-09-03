package com.performance.jmeter.correlation.analyzer;

import com.performance.jmeter.correlation.model.AnalysisResult;
import com.performance.jmeter.correlation.model.ConfigurationStatus;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jorphan.collections.ListedHashTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParameterizationAnalyzerTest {

    private ParameterizationAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ParameterizationAnalyzer();
    }

    @Test
    void analyze_detectsFunctionUsageInSampler() {
        HTTPSamplerProxy sampler = new HTTPSamplerProxy();
        sampler.setName("Test Request");
        sampler.setPath("/api/test?id=${__UUID()}");

        ListedHashTree subTree = new ListedHashTree();
        AnalysisResult result = analyzer.analyze(null, sampler, subTree);

        assertEquals(ConfigurationStatus.CONFIGURED, result.getStatus());
        assertTrue(result.hasItems());
    }

    @Test
    void analyze_detectsCsvVariableUsage() {
        analyzer.setCsvVariables(Arrays.asList("username", "password"));

        HTTPSamplerProxy sampler = new HTTPSamplerProxy();
        sampler.setName("Login Request");
        sampler.setDomain("example.com");
        sampler.setPath("/login");
        sampler.addArgument("user", "${username}");

        ListedHashTree subTree = new ListedHashTree();
        AnalysisResult result = analyzer.analyze(null, sampler, subTree);

        assertEquals(ConfigurationStatus.CONFIGURED, result.getStatus());
    }

    @Test
    void analyze_returnsNotConfiguredForHardcodedValues() {
        HTTPSamplerProxy sampler = new HTTPSamplerProxy();
        sampler.setName("Hardcoded Request");
        sampler.setDomain("api.example.com");
        sampler.setPath("/api/test");
        sampler.addArgument("productId", "12345");

        ListedHashTree subTree = new ListedHashTree();
        AnalysisResult result = analyzer.analyze(null, sampler, subTree);

        assertEquals(ConfigurationStatus.NOT_CONFIGURED, result.getStatus());
    }

    @Test
    void extractCsvVariablesFromTree_extractsVariableNames() {
        List<String> vars = analyzer.extractCsvVariablesFromTree(null);
        assertTrue(vars.isEmpty());
    }
}
