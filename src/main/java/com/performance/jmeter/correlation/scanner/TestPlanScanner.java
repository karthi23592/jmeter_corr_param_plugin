package com.performance.jmeter.correlation.scanner;

import com.performance.jmeter.correlation.analyzer.CorrelationAnalyzer;
import com.performance.jmeter.correlation.analyzer.ParameterizationAnalyzer;
import com.performance.jmeter.correlation.model.AnalysisResult;
import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.DetectedItem;
import com.performance.jmeter.correlation.model.ElementStatus;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jorphan.collections.HashTree;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestPlanScanner {

    private static final Pattern VARIABLE_USAGE_PATTERN = Pattern.compile("\\$\\{([^}_][^}]*)}");

    private final CorrelationAnalyzer correlationAnalyzer;
    private final ParameterizationAnalyzer parameterizationAnalyzer;

    private int totalThreadGroups;
    private int totalSamplers;
    private int totalExtractors;
    private int totalParameterizationSources;

    public TestPlanScanner() {
        this.correlationAnalyzer = new CorrelationAnalyzer();
        this.parameterizationAnalyzer = new ParameterizationAnalyzer();
    }

    public ElementStatus scanFromTreeModel(JMeterTreeModel treeModel) {
        resetCounters();

        JMeterTreeNode rootNode = (JMeterTreeNode) treeModel.getRoot();
        if (rootNode == null || rootNode.getChildCount() == 0) {
            return new ElementStatus("Test Plan", "TestPlan", "/");
        }

        // Collect CSV variables from the entire tree
        List<String> csvVariables = collectCsvVariablesFromNodes(rootNode);
        parameterizationAnalyzer.setCsvVariables(csvVariables);

        // Collect all extracted variable names (from extractors) across the entire test plan
        Set<String> allExtractedVarNames = collectAllExtractedVariableNames(rootNode);
        parameterizationAnalyzer.setExtractedVariables(new ArrayList<>(allExtractedVarNames));

        // Collect all variable usages across the entire test plan (for unused extractor detection)
        Set<String> allVariableUsages = collectAllVariableUsages(rootNode);

        // The first child of root is the TestPlan node
        JMeterTreeNode testPlanNode = (JMeterTreeNode) rootNode.getChildAt(0);
        TestElement testPlanElement = testPlanNode.getTestElement();

        ElementStatus rootStatus = new ElementStatus(
                testPlanElement.getName(), "TestPlan", "/");

        // Scan all children of the TestPlan node (empty prefix so paths start with /Name)
        scanNodeChildren(testPlanNode, rootStatus, "", allVariableUsages, allExtractedVarNames);

        return rootStatus;
    }

    public ElementStatus scan(HashTree testPlanTree) {
        resetCounters();
        if (testPlanTree == null) {
            return new ElementStatus("Test Plan", "TestPlan", "/");
        }

        List<String> csvVariables = parameterizationAnalyzer.extractCsvVariablesFromTree(testPlanTree);
        parameterizationAnalyzer.setCsvVariables(csvVariables);

        ElementStatus rootStatus = new ElementStatus("Test Plan", "TestPlan", "/");
        scanHashTree(testPlanTree, rootStatus, "/", Collections.emptySet(), Collections.emptySet());
        return rootStatus;
    }

    private void scanHashTree(HashTree tree, ElementStatus parentStatus, String parentPath, Set<String> allUsages, Set<String> extractedVarNames) {
        if (tree == null) return;
        for (Object key : tree.list()) {
            TestElement element = resolveTestElement(key);
            if (element == null) continue;
            HashTree subTree = tree.getTree(key);
            if (element instanceof TestPlan) {
                parentStatus = new ElementStatus(element.getName(), "TestPlan", "/");
                scanHashTree(subTree, parentStatus, "/" + element.getName(), allUsages, extractedVarNames);
            } else {
                processElement(element, subTree, parentStatus, parentPath, allUsages, extractedVarNames);
            }
        }
    }

    private TestElement resolveTestElement(Object key) {
        if (key instanceof TestElement) return (TestElement) key;
        if (key instanceof JMeterTreeNode) return ((JMeterTreeNode) key).getTestElement();
        return null;
    }

    private void scanNodeChildren(JMeterTreeNode parentNode, ElementStatus parentStatus, String parentPath, Set<String> allUsages, Set<String> extractedVarNames) {
        Enumeration<?> children = parentNode.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode childNode = (JMeterTreeNode) children.nextElement();
            TestElement element = childNode.getTestElement();
            if (element == null) continue;

            // Skip noise elements entirely
            if (isNoiseElement(element)) {
                // But still recurse into its children (Include Controllers may contain HTTP samplers)
                scanNodeChildren(childNode, parentStatus, parentPath, allUsages, extractedVarNames);
                continue;
            }

            HashTree childSubTree = buildChildElementTree(childNode);
            processElement(element, childSubTree, parentStatus, parentPath, allUsages, extractedVarNames);

            // Recurse into any ThreadGroup or Controller (all types, not just TransactionController)
            if (isThreadGroup(element) || isAnyController(element)) {
                if (!parentStatus.getChildren().isEmpty()) {
                    ElementStatus lastAdded = parentStatus.getChildren().get(parentStatus.getChildren().size() - 1);
                    scanNodeChildren(childNode, lastAdded, parentPath + "/" + element.getName(), allUsages, extractedVarNames);
                    StatusAggregator.aggregate(lastAdded);
                }
            }
        }
    }

    private HashTree buildChildElementTree(JMeterTreeNode node) {
        HashTree subTree = new org.apache.jorphan.collections.ListedHashTree();
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            TestElement childElement = child.getTestElement();
            if (childElement != null) {
                subTree.add(childElement);
            }
        }
        return subTree;
    }

    private void processElement(TestElement element, HashTree elementSubTree, ElementStatus parentStatus, String parentPath, Set<String> allUsages, Set<String> extractedVarNames) {
        String elementPath = parentPath + "/" + element.getName();

        if (isThreadGroup(element)) {
            totalThreadGroups++;
            ElementStatus threadGroupStatus = new ElementStatus(
                    element.getName(), "ThreadGroup", elementPath);
            parentStatus.addChild(threadGroupStatus);

        } else if (isHttpSampler(element)) {
            totalSamplers++;
            ElementStatus samplerStatus = new ElementStatus(
                    element.getName(), "HTTPSampler", elementPath);

            AnalysisResult correlationResult = correlationAnalyzer.analyze(null, element, elementSubTree);
            AnalysisResult paramResult = parameterizationAnalyzer.analyze(null, element, elementSubTree);

            // Check for unused extractors
            if (correlationResult.hasItems() && !allUsages.isEmpty()) {
                validateExtractorUsage(correlationResult, allUsages);
            }

            // If sampler has no extractors but USES extracted variables → mark C:✓
            if (!correlationResult.hasItems() && !extractedVarNames.isEmpty()) {
                if (usesExtractedVariables(element, extractedVarNames)) {
                    correlationResult.setStatus(ConfigurationStatus.CONFIGURED);
                    correlationResult.addDetectedItem(new DetectedItem(
                            "Correlation Variable Usage", "uses extracted variables", element.getName(), true));
                }
            }

            if (correlationResult.hasItems()) {
                totalExtractors += correlationResult.getDetectedItems().size();
            }
            if (paramResult.hasItems()) {
                totalParameterizationSources += paramResult.getDetectedItems().size();
            }

            samplerStatus.setCorrelationResult(correlationResult);
            samplerStatus.setParameterizationResult(paramResult);
            parentStatus.addChild(samplerStatus);

        } else if (isAnyController(element)) {
            ElementStatus controllerStatus = new ElementStatus(
                    element.getName(), "Controller", elementPath);
            parentStatus.addChild(controllerStatus);
        }
    }

    private void validateExtractorUsage(AnalysisResult correlationResult, Set<String> allUsages) {
        boolean anyUnused = false;
        for (DetectedItem item : correlationResult.getDetectedItems()) {
            if (!allUsages.contains(item.getVariableName())) {
                anyUnused = true;
                correlationResult.addWarning("Variable '" + item.getVariableName() + "' is extracted but never used in subsequent requests");
            }
        }
        if (anyUnused && correlationResult.getStatus() == ConfigurationStatus.CONFIGURED) {
            correlationResult.setStatus(ConfigurationStatus.PARTIAL);
        }
    }

    /**
     * Collects all variable names that are EXTRACTED by post-processors (JSON, Regex, Boundary, JSR223, etc.)
     * across the entire test plan tree. Used to distinguish correlation vs parameterization variables.
     */
    private Set<String> collectAllExtractedVariableNames(JMeterTreeNode node) {
        Set<String> extracted = new HashSet<>();
        TestElement element = node.getTestElement();
        if (element != null) {
            String className = element.getClass().getName();
            if (className.contains("JSONPostProcessor") || className.contains("JSONExtractor")) {
                addExtractedNames(element.getPropertyAsString("JSONPostProcessor.referenceNames"), extracted);
            } else if (className.contains("RegexExtractor")) {
                addExtractedNames(element.getPropertyAsString("RegexExtractor.refname"), extracted);
            } else if (className.contains("BoundaryExtractor")) {
                addExtractedNames(element.getPropertyAsString("BoundaryExtractor.refname"), extracted);
            } else if (className.contains("XPath2Extractor")) {
                addExtractedNames(element.getPropertyAsString("XPath2Extractor.refname"), extracted);
            } else if (className.contains("XPathExtractor")) {
                addExtractedNames(element.getPropertyAsString("XPathExtractor.refname"), extracted);
            } else if (className.contains("HtmlExtractor") || className.contains("CSSSelector")) {
                addExtractedNames(element.getPropertyAsString("HtmlExtractor.refname"), extracted);
            } else if (className.contains("Extractor")) {
                // Generic fallback: scan all properties for refname/referenceNames patterns
                extractRefNamesFromProperties(element, extracted);
            } else if (className.contains("JSR223PostProcessor") || className.contains("BeanShellPostProcessor")) {
                String script = element.getPropertyAsString("script");
                if (script != null && script.contains("vars.put")) {
                    Pattern putPattern = Pattern.compile("vars\\.put\\s*\\(\\s*[\"']([^\"']+)[\"']");
                    Matcher matcher = putPattern.matcher(script);
                    while (matcher.find()) {
                        extracted.add(matcher.group(1).trim());
                    }
                }
            }
        }
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            extracted.addAll(collectAllExtractedVariableNames(child));
        }
        return extracted;
    }

    private void extractRefNamesFromProperties(TestElement element, Set<String> extracted) {
        element.propertyIterator().forEachRemaining(prop -> {
            String propName = prop.getName();
            if (propName.endsWith(".refname") || propName.endsWith(".referenceNames")) {
                addExtractedNames(prop.getStringValue(), extracted);
            }
        });
    }

    private void addExtractedNames(String refNames, Set<String> extracted) {
        if (refNames == null || refNames.trim().isEmpty()) return;
        for (String name : refNames.split(";")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                extracted.add(trimmed);
            }
        }
    }

    /**
     * Checks if a sampler's properties reference any of the known extracted variables.
     */
    private boolean usesExtractedVariables(TestElement element, Set<String> extractedVarNames) {
        StringBuilder sb = new StringBuilder();
        element.propertyIterator().forEachRemaining(prop -> {
            String value = prop.getStringValue();
            if (value != null) {
                sb.append(value).append("\n");
            }
        });
        String text = sb.toString();
        Matcher matcher = VARIABLE_USAGE_PATTERN.matcher(text);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (extractedVarNames.contains(varName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects all ${variableName} usages across the entire test plan.
     * Used to detect unused extractors.
     */
    private Set<String> collectAllVariableUsages(JMeterTreeNode node) {
        Set<String> usages = new HashSet<>();
        TestElement element = node.getTestElement();
        if (element != null && isHttpSampler(element)) {
            element.propertyIterator().forEachRemaining(prop -> {
                String value = prop.getStringValue();
                if (value != null) {
                    Matcher matcher = VARIABLE_USAGE_PATTERN.matcher(value);
                    while (matcher.find()) {
                        usages.add(matcher.group(1));
                    }
                }
            });
        }

        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            usages.addAll(collectAllVariableUsages(child));
        }
        return usages;
    }

    private boolean isNoiseElement(TestElement element) {
        String className = element.getClass().getName();
        String name = element.getName().toLowerCase();

        // Include Controller — skip it as a tree node but still recurse into children
        if (className.contains("IncludeController")) return true;

        // Non-HTTP samplers: ThinkTime, Pacing, TestAction, DebugSampler, DummySampler
        if (className.contains("TestAction") || className.contains("DebugSampler")) return true;
        if (name.contains("thinktime") || name.contains("think time") || name.contains("pacing")) {
            if (!isHttpSampler(element)) return true;
        }

        // Flow control actions
        if (className.contains("FlowControlAction")) return true;

        // Config elements, timers, assertions, pre/post processors at tree level
        if (className.contains("Timer") || className.contains("Assertion")) return true;
        if (className.contains("ConfigTestElement") || className.contains("CSVDataSet")) return true;
        if (className.contains("HeaderManager") || className.contains("CookieManager")) return true;
        if (className.contains("CacheManager") || className.contains("AuthManager")) return true;

        // Post/Pre processors (these are analyzed as children of samplers)
        if (className.contains("PostProcessor") || className.contains("PreProcessor")) return true;
        if (className.contains("Extractor") || className.contains("JSR223")) return true;

        // Listeners
        if (className.contains("Visualizer") || className.contains("ResultCollector")) return true;
        if (className.contains("ViewResultsFullVisualizer") || className.contains("Summariser")) return true;

        return false;
    }

    private boolean isThreadGroup(TestElement element) {
        return element instanceof ThreadGroup ||
               element.getClass().getName().contains("ThreadGroup") ||
               (element.getClass().getSuperclass() != null &&
                element.getClass().getSuperclass().getName().contains("ThreadGroup"));
    }

    private boolean isTransactionController(TestElement element) {
        String className = element.getClass().getName();
        return className.contains("TransactionController") ||
               (className.contains("Controller") &&
                !className.contains("IncludeController") &&
                !className.contains("ThreadGroup") &&
                !className.contains("LoopController") &&
                !className.contains("ModuleController"));
    }

    private boolean isAnyController(TestElement element) {
        String className = element.getClass().getName();
        return className.contains("Controller") &&
               !className.contains("IncludeController") &&
               !className.contains("ThreadGroup") &&
               !className.contains("LoopController");
    }

    private boolean isHttpSampler(TestElement element) {
        if (element instanceof HTTPSamplerProxy) return true;
        String className = element.getClass().getName();
        return className.contains("HTTPSampler") || className.contains("HttpSampler");
    }

    private List<String> collectCsvVariablesFromNodes(JMeterTreeNode node) {
        List<String> vars = new ArrayList<>();
        TestElement element = node.getTestElement();
        if (element instanceof CSVDataSet) {
            String variableNames = element.getPropertyAsString("variableNames");
            if (variableNames != null && !variableNames.trim().isEmpty()) {
                for (String name : variableNames.split(",")) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        vars.add(trimmed);
                    }
                }
            }
        }
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            vars.addAll(collectCsvVariablesFromNodes(child));
        }
        return vars;
    }

    public void resetCounters() {
        totalThreadGroups = 0;
        totalSamplers = 0;
        totalExtractors = 0;
        totalParameterizationSources = 0;
    }

    public int getTotalThreadGroups() { return totalThreadGroups; }
    public int getTotalSamplers() { return totalSamplers; }
    public int getTotalExtractors() { return totalExtractors; }
    public int getTotalParameterizationSources() { return totalParameterizationSources; }
}
