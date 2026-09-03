package com.performance.jmeter.correlation.analyzer;

import com.performance.jmeter.correlation.model.AnalysisResult;
import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.DetectedItem;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterizationAnalyzer implements ConfigurationAnalyzer {

    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "\\$\\{__(?:Random|RandomString|UUID|time|timeShift|threadNum|machineIP|" +
            "property|P|RandomDate|counter|intSum|longSum|V|eval|char|unescape)\\s*\\("
    );

    private static final Pattern VARIABLE_REF_PATTERN = Pattern.compile("\\$\\{([^_][^}]*)}");

    private List<String> csvVariables = new ArrayList<>();
    private List<String> extractedVariables = new ArrayList<>();

    public void setCsvVariables(List<String> csvVariables) {
        this.csvVariables = csvVariables;
    }

    public void setExtractedVariables(List<String> extractedVariables) {
        this.extractedVariables = extractedVariables;
    }

    @Override
    public AnalysisResult analyze(HashTree testPlanTree, TestElement element, HashTree elementSubTree) {
        AnalysisResult result = new AnalysisResult();
        List<DetectedItem> paramItems = new ArrayList<>();

        String elementText = collectElementText(element);

        detectFunctionUsage(elementText, paramItems, element.getName());
        detectVariableUsage(elementText, paramItems, element.getName());
        detectCsvUsage(elementText, paramItems, element.getName());

        if (elementSubTree != null) {
            detectCsvDataSetInScope(elementSubTree, paramItems);
            detectJSR223Parameterization(elementSubTree, paramItems);
        }

        if (paramItems.isEmpty()) {
            result.setStatus(ConfigurationStatus.NOT_CONFIGURED);
        } else {
            result.setStatus(ConfigurationStatus.CONFIGURED);
            paramItems.forEach(result::addDetectedItem);
        }

        return result;
    }

    private String collectElementText(TestElement element) {
        StringBuilder sb = new StringBuilder();
        element.propertyIterator().forEachRemaining(prop -> {
            String value = prop.getStringValue();
            if (value != null) {
                sb.append(value).append("\n");
            }
        });
        return sb.toString();
    }

    private void detectFunctionUsage(String text, List<DetectedItem> items, String source) {
        if (text == null) return;
        Matcher matcher = FUNCTION_PATTERN.matcher(text);
        while (matcher.find()) {
            String functionMatch = matcher.group();
            String functionName = functionMatch.substring(2, functionMatch.length() - 1);
            functionName = functionName.replace("{", "");
            items.add(new DetectedItem(
                    "JMeter Function",
                    functionName,
                    source,
                    true
            ));
        }
    }

    private void detectVariableUsage(String text, List<DetectedItem> items, String source) {
        if (text == null) return;
        Matcher matcher = VARIABLE_REF_PATTERN.matcher(text);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!varName.startsWith("__") && !varName.contains("(")) {
                // Skip extracted correlation variables — those contribute to C:✓ not P:✓
                if (extractedVariables.contains(varName)) {
                    continue;
                }
                String type = csvVariables.contains(varName) ? "CSV Variable" : "Variable Reference";
                items.add(new DetectedItem(type, varName, source, true));
            }
        }
    }

    private void detectCsvUsage(String text, List<DetectedItem> items, String source) {
        // Already covered in detectVariableUsage for CSV vars
    }

    private void detectCsvDataSetInScope(HashTree subTree, List<DetectedItem> items) {
        Collection<?> children = subTree.list();
        for (Object child : children) {
            if (child instanceof CSVDataSet) {
                CSVDataSet csv = (CSVDataSet) child;
                String variableNames = csv.getPropertyAsString("variableNames");
                if (variableNames != null && !variableNames.trim().isEmpty()) {
                    String[] vars = variableNames.split(",");
                    for (String var : vars) {
                        String trimmed = var.trim();
                        if (!trimmed.isEmpty()) {
                            items.add(new DetectedItem(
                                    "CSV Data Set Config",
                                    trimmed,
                                    csv.getName(),
                                    true
                            ));
                        }
                    }
                }
            }
        }
    }

    private void detectJSR223Parameterization(HashTree subTree, List<DetectedItem> items) {
        Collection<?> children = subTree.list();
        for (Object child : children) {
            if (child instanceof TestElement) {
                TestElement te = (TestElement) child;
                String className = te.getClass().getName();
                if (className.contains("JSR223PreProcessor") || className.contains("JSR223Sampler")) {
                    String script = te.getPropertyAsString("script");
                    if (script != null) {
                        Pattern putPattern = Pattern.compile("vars\\.put\\s*\\(\\s*[\"']([^\"']+)[\"']");
                        Matcher matcher = putPattern.matcher(script);
                        while (matcher.find()) {
                            items.add(new DetectedItem(
                                    "JSR223 Parameterization",
                                    matcher.group(1),
                                    te.getName(),
                                    true
                            ));
                        }
                    }
                }
            }
        }
    }

    public List<String> extractCsvVariablesFromTree(HashTree tree) {
        List<String> vars = new ArrayList<>();
        if (tree == null) return vars;

        for (Object key : tree.list()) {
            if (key instanceof CSVDataSet) {
                CSVDataSet csv = (CSVDataSet) key;
                String variableNames = csv.getPropertyAsString("variableNames");
                if (variableNames != null && !variableNames.trim().isEmpty()) {
                    String[] names = variableNames.split(",");
                    for (String name : names) {
                        String trimmed = name.trim();
                        if (!trimmed.isEmpty()) {
                            vars.add(trimmed);
                        }
                    }
                }
            }
            HashTree subTree = tree.getTree(key);
            if (subTree != null) {
                vars.addAll(extractCsvVariablesFromTree(subTree));
            }
        }
        return vars;
    }
}
