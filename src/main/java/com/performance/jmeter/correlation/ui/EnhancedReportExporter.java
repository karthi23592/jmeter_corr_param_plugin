package com.performance.jmeter.correlation.ui;

import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.DetectedItem;
import com.performance.jmeter.correlation.model.ElementStatus;
import com.performance.jmeter.correlation.scanner.StatusAggregator;
import com.performance.jmeter.correlation.util.VariablePatterns;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Exports detailed report with variable tracking
public class EnhancedReportExporter {

    private static final Pattern VAR_USAGE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private Map<String, List<SamplerInfo>> varUsageMap = new HashMap<>();
    private Map<String, VariableSource> varSourceMap = new HashMap<>();
    private Map<String, String> threadGroupUserCounts = new HashMap<>();
    private Map<String, String> variableMap = new HashMap<>();
    private int totalExtractors = 0;
    private int totalParamSources = 0;
    private String totalUsers = "N/A";

    public void exportToDetailedReport(java.io.File file, ElementStatus root) throws Exception {
        // TODO: add support for HTML export format
        analyzeVariables();

        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("===============================================");
            writer.println("  VARIABLE TRACKING & FLOW REPORT");
            writer.println("  Smart Variable Tracker & Navigator");
            writer.println("===============================================");
            writer.println();

            writeSummary(writer, root);
            writer.println();

            writer.println("===============================================");
            writer.println("  TEST PLAN STRUCTURE");
            writer.println("===============================================");
            writer.println();
            writeTreeStructure(writer, root, 0);
            writer.println();

            writer.println("===============================================");
            writer.println("  VARIABLE DETAILS");
            writer.println("===============================================");
            writer.println();
            writeVariableDetails(writer);
        }
    }

    private void analyzeVariables() {
        varUsageMap.clear();
        varSourceMap.clear();
        threadGroupUserCounts.clear();
        variableMap.clear();
        totalExtractors = 0;
        totalParamSources = 0;

        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                JMeterTreeModel treeModel = guiPackage.getTreeModel();
                if (treeModel != null) {
                    JMeterTreeNode rootNode = (JMeterTreeNode) treeModel.getRoot();
                    if (rootNode.getChildCount() > 0) {
                        JMeterTreeNode testPlanNode = (JMeterTreeNode) rootNode.getChildAt(0);
                        // First pass: collect variables from UDV
                        collectVariablesFromTree(testPlanNode);
                        // Second pass: scan for variables and user counts
                        scanNodeForVariables(testPlanNode, "");
                        // Calculate total users
                        calculateTotalUsers();
                    }
                }
            }
        } catch (Exception e) {
            // Not in JMeter context
        }
    }

    private void scanNodeForVariables(JMeterTreeNode node, String threadGroupName) {
        if (node == null)
            return;

        TestElement te = node.getTestElement();
        if (te == null) return;

        String className = te.getClass().getName();
        String nodeName = node.getName();
        String nodeType = getNodeType(className);

        // Track thread groups (for context and user counts)
        if (className.contains("ThreadGroup")) {
            threadGroupName = nodeName;
            // Only count enabled thread groups
            if (te.isEnabled()) {
                // Get user count for this thread group
                String userCount = getThreadGroupUserCount(te);
                threadGroupUserCounts.put(nodeName, userCount);
            }
            // If disabled, don't add to user count map
        }

        // Track sampler variables
        if (className.contains("Sampler")) {
            // Find variables used by this sampler
            Set<String> usedVars = findVariablesUsedBy(te);
            for (String varName : usedVars) {
                SamplerInfo info = new SamplerInfo(threadGroupName, nodeName, getNodePath(node));
                varUsageMap.computeIfAbsent(varName, k -> new ArrayList<>()).add(info);
            }
        }

        // Track extractors (correlation sources)
        if (isExtractor(className)) {
            totalExtractors++;
            List<String> extractedVars = getExtractedVariables(te, className);
            for (String varName : extractedVars) {
                varSourceMap.put(varName,
                    new VariableSource(varName, "Extractor", nodeName, getNodeType(className), threadGroupName));
            }
        }

        // Track CSV Data Set Config (parameterization sources)
        if (className.contains("CSVDataSet")) {
            totalParamSources++;
            String varNames = te.getPropertyAsString("variableNames");
            if (varNames != null && !varNames.isEmpty()) {
                for (String varName : varNames.split(",")) {
                    String trimmed = varName.trim();
                    if (!trimmed.isEmpty()) {
                        varSourceMap.put(trimmed,
                            new VariableSource(trimmed, "CSV Parameter", nodeName, "CSV Data Set Config", threadGroupName));
                    }
                }
            }
        }

        // Track User Defined Variables
        if (className.contains("Arguments") && !className.contains("Sampler")) {
            try {
                org.apache.jmeter.config.Arguments args = (org.apache.jmeter.config.Arguments) te;
                Map<String, String> argMap = args.getArgumentsAsMap();
                for (String varName : argMap.keySet()) {
                    varSourceMap.put(varName,
                        new VariableSource(varName, "User Defined Variable", nodeName, "User Defined Variables", threadGroupName));
                }
            } catch (Exception e) {
                // Skip if not Arguments type
            }
        }

        // Recursively scan children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof JMeterTreeNode) {
                scanNodeForVariables((JMeterTreeNode) child, threadGroupName);
            }
        }
    }

    private Set<String> findVariablesUsedBy(TestElement te) {
        Set<String> variables = new HashSet<>();
        StringBuilder allProps = new StringBuilder();

        te.propertyIterator().forEachRemaining(prop -> {
            String value = prop.getStringValue();
            if (value != null) {
                allProps.append(value).append("\n");
            }
        });

        Matcher matcher = VariablePatterns.VARIABLE_USAGE.matcher(allProps.toString());
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!varName.startsWith("__") && !varName.contains("(")) {
                variables.add(varName);
            }
        }

        return variables;
    }

    private List<String> getExtractedVariables(TestElement te, String className) {
        List<String> vars = new ArrayList<>();

        if (className.contains("JSONPostProcessor") || className.contains("JSONExtractor")) {
            addVariableNames(te.getPropertyAsString("JSONPostProcessor.referenceNames"), vars);
        } else if (className.contains("RegexExtractor")) {
            addVariableNames(te.getPropertyAsString("RegexExtractor.refname"), vars);
        } else if (className.contains("BoundaryExtractor")) {
            addVariableNames(te.getPropertyAsString("BoundaryExtractor.refname"), vars);
        } else if (className.contains("XPathExtractor")) {
            addVariableNames(te.getPropertyAsString("XPathExtractor.refname"), vars);
        } else if (className.contains("HtmlExtractor") || className.contains("CSSSelector")) {
            addVariableNames(te.getPropertyAsString("HtmlExtractor.refname"), vars);
        } else if (className.contains("JSR223") || className.contains("BeanShell")) {
            String script = te.getPropertyAsString("script");
            if (script != null && script.contains("vars.put")) {
                Matcher matcher = VariablePatterns.VARS_PUT.matcher(script);
                while (matcher.find()) {
                    vars.add(matcher.group(1));
                }
            }
        }

        return vars;
    }

    private void addVariableNames(String refNames, List<String> vars) {
        if (refNames == null || refNames.trim().isEmpty()) return;
        for (String name : refNames.split(";")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty() && !vars.contains(trimmed)) {
                vars.add(trimmed);
            }
        }
    }

    private boolean isExtractor(String className) {
        return className.contains("Extractor") ||
               className.contains("JSONPostProcessor") ||
               (className.contains("JSR223PostProcessor")) ||
               (className.contains("BeanShellPostProcessor"));
    }

    private String getNodeType(String className) {
        if (className.contains("ThreadGroup")) return "ThreadGroup";
        if (className.contains("Sampler")) return "Sampler";
        if (className.contains("Controller")) return "Controller";
        if (isExtractor(className)) return "Extractor";
        if (className.contains("CSVDataSet")) return "CSV Config";
        if (className.contains("Arguments")) return "Config";
        return "Element";
    }

    private String getNodePath(JMeterTreeNode node) {
        StringBuilder path = new StringBuilder();
        javax.swing.tree.TreeNode[] nodes = node.getPath();
        for (int i = 2; i < nodes.length; i++) {
            path.append("/");
            if (nodes[i] instanceof JMeterTreeNode) {
                path.append(((JMeterTreeNode) nodes[i]).getName());
            }
        }
        return path.length() == 0 ? "/" : path.toString();
    }

    private void writeSummary(PrintWriter writer, ElementStatus root) {
        // Use the same counting logic as the UI
        StatusAggregator.ScanSummary summary = StatusAggregator.computeSummary(root);

        writer.println("SUMMARY STATISTICS:");
        writer.println("-------------------");
        writer.println("Thread Groups: " + summary.totalThreadGroups);
        writer.println("Total Users: " + totalUsers);
        writer.println("HTTP Samplers: " + summary.totalSamplers);
        writer.println("Extractors: " + totalExtractors);
        writer.println("Parameterization Sources: " + totalParamSources);
        writer.println("Total Variables Tracked: " + varSourceMap.size());
        writer.println();
        writer.println("Correlation Status:");
        writer.println("  Configured: " + summary.correlationConfigured);
    }

    private void writeTreeStructure(PrintWriter writer, ElementStatus element, int level) {
        String indent = getIndent(level);
        String icon = getIcon(element);

        writer.print(indent + icon + " " + element.getElementName());
        writer.print(" [" + element.getElementType() + "]");

        // Add C/P status indicators
        String corrStatus = getStatusIndicator(element.getEffectiveCorrelationStatus());
        String paramStatus = getStatusIndicator(element.getEffectiveParameterizationStatus());
        writer.print(" C:" + corrStatus + " P:" + paramStatus);

        // Add user count for ThreadGroups
        if ("ThreadGroup".equals(element.getElementType())) {
            String userCount = threadGroupUserCounts.get(element.getElementName());
            if (userCount != null && !userCount.equals("?")) {
                writer.print(" Users:" + userCount);
            }
        }

        writer.println();

        // If it's a sampler, show details about variables
        if (element.getElementType().equals("HTTPSampler") || element.getElementType().equals("Sampler")) {
            writeSamplerVariableDetails(writer, element, level + 1);
        }

        // Recurse to children
        for (ElementStatus child : element.getChildren()) {
            writeTreeStructure(writer, child, level + 1);
        }
    }

    private void writeSamplerVariableDetails(PrintWriter writer, ElementStatus element, int level) {
        String indent = getIndent(level);

        // Show extracted variables (if any)
        if (element.getCorrelationResult() != null &&
            element.getCorrelationResult().getDetectedItems() != null &&
            !element.getCorrelationResult().getDetectedItems().isEmpty()) {

            writer.println(indent + "├─ Variables Extracted:");
            for (DetectedItem item : element.getCorrelationResult().getDetectedItems()) {
                writer.println(indent + "│  └─ " + item.getVariableName() + " (" + item.getType() + ")");
            }
        }

        // Show variables used by this sampler
        Set<String> usedVars = findVariablesUsedInElement(element);
        if (!usedVars.isEmpty()) {
            writer.println(indent + "├─ Variables Used:");
            for (String varName : usedVars) {
                VariableSource source = varSourceMap.get(varName);
                if (source != null) {
                    writer.println(indent + "│  └─ ${" + varName + "} from " + source.sourceType + ": " + source.sourceName);
                } else {
                    writer.println(indent + "│  └─ ${" + varName + "} (source unknown)");
                }
            }
        }
    }

    private Set<String> findVariablesUsedInElement(ElementStatus element) {
        Set<String> vars = new HashSet<>();

        // Check parameterization result
        if (element.getParameterizationResult() != null &&
            element.getParameterizationResult().getDetectedItems() != null) {
            for (DetectedItem item : element.getParameterizationResult().getDetectedItems()) {
                vars.add(item.getVariableName());
            }
        }

        return vars;
    }

    private void writeVariableDetails(PrintWriter writer) {
        writer.println("VARIABLE USAGE MATRIX:");
        writer.println("----------------------");
        writer.println();

        // Sort variables alphabetically
        List<String> sortedVars = new ArrayList<>(varSourceMap.keySet());
        Collections.sort(sortedVars);

        for (String varName : sortedVars) {
            VariableSource source = varSourceMap.get(varName);
            List<SamplerInfo> usages = varUsageMap.get(varName);

            writer.println("Variable: ${" + varName + "}");
            writer.println("  Source Type: " + source.sourceType);
            writer.println("  Source Name: " + source.sourceName + " (" + source.sourceElementType + ")");
            writer.println("  Thread Group: " + source.threadGroup);

            if (usages == null || usages.isEmpty()) {
                writer.println("  ⚠ NOT USED (Unused variable)");
            } else {
                writer.println("  Used By (" + usages.size() + " sampler" + (usages.size() > 1 ? "s" : "") + "):");
                for (SamplerInfo info : usages) {
                    writer.println("    • " + info.samplerName + " (TG: " + info.threadGroup + ")");
                }
            }
            writer.println();
        }
    }

    private String getIndent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    private String getIcon(ElementStatus element) {
        switch (element.getElementType()) {
            case "TestPlan": return "📋";
            case "ThreadGroup": return "👥";
            case "HTTPSampler":
            case "Sampler": return "🌐";
            case "Controller": return "📂";
            case "Extractor": return "⚙";
            default: return "•";
        }
    }

    private String getStatusIndicator(ConfigurationStatus status) {
        switch (status) {
            case CONFIGURED: return "✓";
            case NOT_CONFIGURED: return "✗";
            case PARTIAL: return "◐";
            case NOT_APPLICABLE: return "—";
            default: return "?";
        }
    }

    /**
     * Collects variables from User Defined Variables across the test plan.
     */
    private void collectVariablesFromTree(JMeterTreeNode node) {
        if (node == null) return;

        TestElement te = node.getTestElement();
        if (te != null) {
            String className = te.getClass().getName();
            // Collect from User Defined Variables (Arguments)
            if (className.contains("Arguments") && !className.contains("Sampler")) {
                try {
                    Arguments args = (Arguments) te;
                    Map<String, String> argMap = args.getArgumentsAsMap();
                    variableMap.putAll(argMap);
                } catch (Exception e) {
                    // Not an Arguments type or cast failed
                }
            }
        }

        // Recurse to children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof JMeterTreeNode) {
                collectVariablesFromTree((JMeterTreeNode) child);
            }
        }
    }

    /**
     * Gets the user count from a ThreadGroup test element.
     * Supports standard ThreadGroup and jp@gc plugin thread groups.
     * Resolves variables from User Defined Variables if possible.
     */
    private String getThreadGroupUserCount(TestElement te) {
        if (te == null) return "?";

        String className = te.getClass().getName();
        if (!className.contains("ThreadGroup")) return "?";

        try {
            String numThreads = null;

            // Handle jp@gc - Ultimate Thread Group
            if (className.contains("UltimateThreadGroup")) {
                numThreads = getUltimateThreadGroupCount(te);
            }
            // Handle jp@gc - Stepping Thread Group
            else if (className.contains("SteppingThreadGroup")) {
                numThreads = te.getPropertyAsString("ThreadGroup.num_threads");
            }
            // Handle jp@gc - Concurrency Thread Group
            else if (className.contains("ConcurrencyThreadGroup")) {
                numThreads = te.getPropertyAsString("TargetLevel");
            }
            // Handle jp@gc - Arrivals Thread Group
            else if (className.contains("ArrivalsThreadGroup")) {
                numThreads = te.getPropertyAsString("ConcurrencyLimit");
            }
            // Handle standard Thread Group
            else {
                numThreads = te.getPropertyAsString("ThreadGroup.num_threads");
            }

            if (numThreads == null || numThreads.trim().isEmpty()) {
                return "?";
            }

            // Resolve variable if it's parameterized
            return resolveVariable(numThreads.trim());
        } catch (Exception e) {
            return "?";
        }
    }

    /**
     * Extracts the maximum thread count from Ultimate Thread Group schedule.
     */
    private String getUltimateThreadGroupCount(TestElement te) {
        try {
            // Try method 1: Get from collection property (most reliable)
            org.apache.jmeter.testelement.property.JMeterProperty prop = te.getProperty("ultimatethreadgroupdata");

            if (prop instanceof org.apache.jmeter.testelement.property.CollectionProperty) {
                org.apache.jmeter.testelement.property.CollectionProperty collectionProp =
                    (org.apache.jmeter.testelement.property.CollectionProperty) prop;

                int maxThreads = 0;

                // Iterate through each row in the schedule
                for (org.apache.jmeter.testelement.property.JMeterProperty rowProp : collectionProp) {
                    if (rowProp instanceof org.apache.jmeter.testelement.property.CollectionProperty) {
                        org.apache.jmeter.testelement.property.CollectionProperty row =
                            (org.apache.jmeter.testelement.property.CollectionProperty) rowProp;

                        // First element in row is thread count
                        if (row.size() > 0) {
                            try {
                                org.apache.jmeter.testelement.property.JMeterProperty firstCol = row.get(0);
                                String threadsStr = firstCol.getStringValue();

                                // Try to resolve if it's a variable
                                threadsStr = resolveVariable(threadsStr);

                                int threads = Integer.parseInt(threadsStr);
                                if (threads > maxThreads) {
                                    maxThreads = threads;
                                }
                            } catch (Exception e) {
                                // Skip invalid entries
                            }
                        }
                    }
                }

                if (maxThreads > 0) {
                    return String.valueOf(maxThreads);
                }
            }

            // Try method 2: Get as string and parse
            String scheduleData = te.getPropertyAsString("ultimatethreadgroupdata");
            if (scheduleData != null && !scheduleData.isEmpty() && !scheduleData.equals("null")) {
                int maxThreads = 0;
                String[] rows = scheduleData.split("\n");
                for (String row : rows) {
                    if (row.trim().isEmpty()) continue;
                    String[] parts = row.split(",");
                    if (parts.length > 0) {
                        try {
                            String threadsStr = parts[0].trim();
                            threadsStr = resolveVariable(threadsStr);
                            int threads = Integer.parseInt(threadsStr);
                            if (threads > maxThreads) {
                                maxThreads = threads;
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid rows
                        }
                    }
                }
                if (maxThreads > 0) {
                    return String.valueOf(maxThreads);
                }
            }

            // Try method 3: Look for "load" property (alternative property name)
            String load = te.getPropertyAsString("load");
            if (load != null && !load.isEmpty() && !load.equals("null")) {
                return resolveVariable(load);
            }

        } catch (Exception e) {
            // Fallback to standard property
        }

        // Last resort: try standard property
        String standard = te.getPropertyAsString("ThreadGroup.num_threads");
        if (standard != null && !standard.isEmpty() && !standard.equals("null")) {
            return resolveVariable(standard);
        }

        return "?";
    }

    /**
     * Resolves a variable reference to its actual value.
     */
    private String resolveVariable(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        // Extract variable name from ${varName}
        Matcher matcher = VAR_USAGE_PATTERN.matcher(value);
        if (matcher.find()) {
            String varName = matcher.group(1);
            String resolvedValue = variableMap.get(varName);
            if (resolvedValue != null) {
                return resolvedValue;
            }
        }

        // Return original if not resolved
        return value;
    }

    /**
     * Calculates total user count across all thread groups.
     */
    private void calculateTotalUsers() {
        int numericTotal = 0;
        List<String> unresolvedVars = new ArrayList<>();

        for (String userCount : threadGroupUserCounts.values()) {
            if (userCount != null && !userCount.equals("?")) {
                try {
                    numericTotal += Integer.parseInt(userCount);
                } catch (NumberFormatException e) {
                    // Still contains unresolved variable
                    if (!unresolvedVars.contains(userCount)) {
                        unresolvedVars.add(userCount);
                    }
                }
            }
        }

        // Build result string
        StringBuilder result = new StringBuilder();
        if (numericTotal > 0 || unresolvedVars.isEmpty()) {
            result.append(numericTotal);
        }

        if (!unresolvedVars.isEmpty()) {
            if (result.length() > 0) {
                result.append(" + ");
            }
            result.append(String.join(" + ", unresolvedVars));
        }

        totalUsers = result.length() > 0 ? result.toString() : "0";
    }

    // Inner classes for tracking variable information
    private static class VariableSource {
        String varName;
        String sourceType; // "Extractor", "CSV Parameter", "User Defined Variable"
        String sourceName; // Name of the element that creates this variable
        String sourceElementType; // Type of element
        String threadGroup;

        VariableSource(String varName, String sourceType, String sourceName, String sourceElementType, String threadGroup) {
            this.varName = varName;
            this.sourceType = sourceType;
            this.sourceName = sourceName;
            this.sourceElementType = sourceElementType;
            this.threadGroup = threadGroup;
        }
    }

    private static class SamplerInfo {
        String threadGroup;
        String samplerName;
        String path;

        SamplerInfo(String threadGroup, String samplerName, String path) {
            this.threadGroup = threadGroup;
            this.samplerName = samplerName;
            this.path = path;
        }
    }
}
