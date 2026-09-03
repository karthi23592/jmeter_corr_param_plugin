package com.performance.jmeter.correlation.analyzer;

import com.performance.jmeter.correlation.model.AnalysisResult;
import com.performance.jmeter.correlation.model.ConfigurationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationAnalyzerTest {

    private CorrelationAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new CorrelationAnalyzer();
    }

    @Test
    void findVariableUsagesInText_detectsSimpleVariables() {
        String text = "Authorization: Bearer ${authToken}";
        List<String> vars = analyzer.findVariableUsagesInText(text);
        assertEquals(1, vars.size());
        assertEquals("authToken", vars.get(0));
    }

    @Test
    void findVariableUsagesInText_detectsMultipleVariables() {
        String text = "${baseUrl}/api/users/${userId}/accounts/${accountId}";
        List<String> vars = analyzer.findVariableUsagesInText(text);
        assertEquals(3, vars.size());
        assertTrue(vars.contains("baseUrl"));
        assertTrue(vars.contains("userId"));
        assertTrue(vars.contains("accountId"));
    }

    @Test
    void findVariableUsagesInText_returnsEmptyForNoVariables() {
        String text = "No variables here";
        List<String> vars = analyzer.findVariableUsagesInText(text);
        assertTrue(vars.isEmpty());
    }

    @Test
    void findVariableUsagesInText_handlesNullInput() {
        List<String> vars = analyzer.findVariableUsagesInText(null);
        assertTrue(vars.isEmpty());
    }

    @Test
    void analyze_returnsNotConfiguredWhenNoSubTree() {
        AnalysisResult result = analyzer.analyze(null, new org.apache.jmeter.testelement.AbstractTestElement() {
            @Override
            public String getName() {
                return "test";
            }
        }, null);
        assertEquals(ConfigurationStatus.NOT_CONFIGURED, result.getStatus());
    }
}
