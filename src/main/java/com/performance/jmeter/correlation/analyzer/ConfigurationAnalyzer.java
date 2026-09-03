package com.performance.jmeter.correlation.analyzer;

import com.performance.jmeter.correlation.model.AnalysisResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;

public interface ConfigurationAnalyzer {
    AnalysisResult analyze(HashTree testPlanTree, TestElement element, HashTree elementSubTree);
}
