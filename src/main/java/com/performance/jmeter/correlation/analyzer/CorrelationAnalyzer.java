package com.performance.jmeter.correlation.analyzer;

import com.performance.jmeter.correlation.model.AnalysisResult;
import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.DetectedItem;
import org.apache.jmeter.extractor.BoundaryExtractor;
import org.apache.jmeter.extractor.RegexExtractor;
import org.apache.jmeter.extractor.json.jsonpath.JSONPostProcessor;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CorrelationAnalyzer implements ConfigurationAnalyzer {

    private static final Pattern VARIABLE_USAGE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public AnalysisResult analyze(HashTree testPlanTree, TestElement element, HashTree elementSubTree) {
        AnalysisResult result = new AnalysisResult();
        List<DetectedItem> extractors = new ArrayList<>();

        if (elementSubTree == null) {
            return result;
        }

        Collection<?> children = elementSubTree.list();
        for (Object child : children) {
            if (child instanceof TestElement) {
                detectExtractors((TestElement) child, extractors);
            }
        }

        if (extractors.isEmpty()) {
            result.setStatus(ConfigurationStatus.NOT_CONFIGURED);
        } else {
            boolean allUsed = extractors.stream().allMatch(DetectedItem::isUsageConfirmed);
            if (allUsed) {
                result.setStatus(ConfigurationStatus.CONFIGURED);
            } else {
                result.setStatus(ConfigurationStatus.PARTIAL);
                result.addWarning("Some extracted variables may not be used in subsequent requests");
            }
            extractors.forEach(result::addDetectedItem);
        }

        return result;
    }

    public List<DetectedItem> detectExtractorsFromSubTree(HashTree elementSubTree) {
        List<DetectedItem> extractors = new ArrayList<>();
        if (elementSubTree == null) return extractors;

        Collection<?> children = elementSubTree.list();
        for (Object child : children) {
            if (child instanceof TestElement) {
                detectExtractors((TestElement) child, extractors);
            }
        }
        return extractors;
    }

    private void detectExtractors(TestElement element, List<DetectedItem> extractors) {
        if (element instanceof JSONPostProcessor) {
            JSONPostProcessor jsonExtractor = (JSONPostProcessor) element;
            String varName = jsonExtractor.getRefNames();
            if (varName != null && !varName.trim().isEmpty()) {
                String[] varNames = varName.split(";");
                for (String name : varNames) {
                    extractors.add(new DetectedItem(
                            "JSON Extractor",
                            name.trim(),
                            element.getName(),
                            true
                    ));
                }
            }
        } else if (element instanceof RegexExtractor) {
            RegexExtractor regexExtractor = (RegexExtractor) element;
            String varName = regexExtractor.getRefName();
            if (varName != null && !varName.trim().isEmpty()) {
                extractors.add(new DetectedItem(
                        "Regular Expression Extractor",
                        varName.trim(),
                        element.getName(),
                        true
                ));
            }
        } else if (element instanceof BoundaryExtractor) {
            BoundaryExtractor boundaryExtractor = (BoundaryExtractor) element;
            String varName = boundaryExtractor.getRefName();
            if (varName != null && !varName.trim().isEmpty()) {
                extractors.add(new DetectedItem(
                        "Boundary Extractor",
                        varName.trim(),
                        element.getName(),
                        true
                ));
            }
        } else if (isJSR223PostProcessor(element)) {
            String script = getScriptContent(element);
            if (script != null && !script.isEmpty()) {
                List<String> varsFromScript = extractVarsFromScript(script);
                for (String varName : varsFromScript) {
                    extractors.add(new DetectedItem(
                            "JSR223 PostProcessor",
                            varName,
                            element.getName(),
                            true
                    ));
                }
            }
        }
    }

    private boolean isJSR223PostProcessor(TestElement element) {
        String className = element.getClass().getName();
        return className.contains("JSR223PostProcessor") ||
               className.contains("BeanShellPostProcessor");
    }

    private String getScriptContent(TestElement element) {
        try {
            return element.getPropertyAsString("script");
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> extractVarsFromScript(String script) {
        List<String> vars = new ArrayList<>();
        Pattern putPattern = Pattern.compile("vars\\.put\\s*\\(\\s*[\"']([^\"']+)[\"']");
        Matcher matcher = putPattern.matcher(script);
        while (matcher.find()) {
            vars.add(matcher.group(1));
        }
        return vars;
    }

    public List<String> findVariableUsagesInText(String text) {
        List<String> variables = new ArrayList<>();
        if (text == null) return variables;
        Matcher matcher = VARIABLE_USAGE_PATTERN.matcher(text);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }
}
