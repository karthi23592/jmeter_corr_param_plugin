package com.performance.jmeter.correlation.ui;

import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.ElementStatus;
import com.performance.jmeter.correlation.scanner.TestPlanScanner;
import com.performance.jmeter.correlation.util.OverridePersistence;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InlineStatusDecorator {

    private static InlineStatusDecorator instance;

    private static final Pattern VAR_USAGE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private JTree jmeterTree;
    private TreeCellRenderer originalRenderer;
    private Map<String, ElementStatus> statusMap = new HashMap<>();
    private Map<String, List<JMeterTreeNode>> extractorSourceMap = new HashMap<>();
    private Map<String, List<JMeterTreeNode>> paramSourceMap = new HashMap<>();
    private ElementStatus rootStatus;
    private boolean active = false;
    private Set<JTextComponent> enhancedTextComponents = new HashSet<>();
    private Set<JTable> enhancedTables = new HashSet<>();
    private Set<JTree> enhancedResultsTrees = new HashSet<>(); // Track enhanced View Results Tree components
    private javax.swing.Timer enhancementTimer; // Timer for continuous listener detection
    private JDialog currentCorrelationDialog = null; // Track currently open correlation dialog
    private Set<String> highlightedNodePaths = new HashSet<>(); // Nodes to highlight in purple
    private String currentHighlightedVariable = null; // Currently highlighted variable name

    public static InlineStatusDecorator getInstance() {
        if (instance == null) {
            instance = new InlineStatusDecorator();
        }
        return instance;
    }

    public void activate() {
        if (active) return;

        jmeterTree = findJMeterTree();
        if (jmeterTree == null) {
            JOptionPane.showMessageDialog(null,
                    "Could not find JMeter's test plan tree.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        originalRenderer = jmeterTree.getCellRenderer();
        jmeterTree.setCellRenderer(new InlineStatusRenderer(originalRenderer));
        jmeterTree.addMouseListener(new TreeContextMenuListener());
        jmeterTree.addMouseListener(new ViewIconClickListener());
        // Auto-rescan when user selects a different node (picks up manual parameter changes)
        jmeterTree.addTreeSelectionListener(e -> {
            if (active) {
                scan();
                // Enhance text components in the newly selected GUI
                // Use invokeLater to ensure GUI is fully loaded
                SwingUtilities.invokeLater(() -> {
                    enhanceTextComponentsInGUI();
                    // Enhance again after a short delay to catch late-loaded components
                    SwingUtilities.invokeLater(() -> enhanceTextComponentsInGUI());
                });
            }
        });

        active = true;
        scan();
        enhanceTextComponentsInGUI();

        // Start a timer to check for new listeners (like View Results Tree) for 30 seconds only
        // After that, tree selection listener will handle enhancement
        final int[] scanCount = {0};
        final int MAX_SCANS = 15; // 15 scans * 2 seconds = 30 seconds total

        enhancementTimer = new javax.swing.Timer(2000, e -> {
            if (active && scanCount[0] < MAX_SCANS) {
                enhanceTextComponentsInGUI();
                scanCount[0]++;

                if (scanCount[0] >= MAX_SCANS) {
                    // Stop timer after 30 seconds
                    if (enhancementTimer != null) {
                        enhancementTimer.stop();
                        System.out.println("[Plugin] Enhancement timer stopped after 30 seconds - will now scan only on tree selection changes");
                    }
                }
            }
        });
        enhancementTimer.setRepeats(true);
        enhancementTimer.start();
        System.out.println("[Plugin] Enhancement timer started - will scan for 30 seconds to detect listeners");
    }

    public void deactivate() {
        if (!active || jmeterTree == null) return;

        // Stop the enhancement timer
        if (enhancementTimer != null) {
            enhancementTimer.stop();
            enhancementTimer = null;
            System.out.println("[Plugin] Enhancement timer stopped");
        }

        jmeterTree.setCellRenderer(originalRenderer);
        jmeterTree.repaint();
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public void showFindJSR223Dialog(boolean isPostProcessor) {
        JMeterTreeModel treeModel = getTreeModel();
        if (treeModel == null) {
            JOptionPane.showMessageDialog(jmeterTree,
                    "Could not access JMeter tree.",
                    "Find Elements", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JMeterTreeNode rootNode = (JMeterTreeNode) treeModel.getRoot();
        Map<String, List<JSR223Element>> resultsByThreadGroup = findJSR223Elements(rootNode, isPostProcessor);

        if (resultsByThreadGroup.isEmpty()) {
            String type = isPostProcessor ? "PostProcessors" : "PreProcessors";
            JOptionPane.showMessageDialog(jmeterTree,
                    "No JSR223 " + type + " found in test plan.",
                    "Find Elements", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        showJSR223FindDialog(resultsByThreadGroup, isPostProcessor);
    }

    public void showFindExtractorsDialog() {
        JMeterTreeModel treeModel = getTreeModel();
        if (treeModel == null) {
            JOptionPane.showMessageDialog(jmeterTree,
                    "Could not access JMeter tree.",
                    "Find Elements", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JMeterTreeNode rootNode = (JMeterTreeNode) treeModel.getRoot();
        Map<String, List<ExtractorElement>> resultsByThreadGroup = findExtractorElements(rootNode);

        if (resultsByThreadGroup.isEmpty()) {
            JOptionPane.showMessageDialog(jmeterTree,
                    "No extractors found in test plan.",
                    "Find Elements", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        showExtractorsFindDialog(resultsByThreadGroup);
    }

    public void scan() {
        if (!active) return;

        SwingWorker<ElementStatus, Void> worker = new SwingWorker<ElementStatus, Void>() {
            @Override
            protected ElementStatus doInBackground() {
                JMeterTreeModel treeModel = getTreeModel();
                if (treeModel == null) return null;
                TestPlanScanner scanner = new TestPlanScanner();
                return scanner.scanFromTreeModel(treeModel);
            }

            @Override
            protected void done() {
                try {
                    ElementStatus result = get();
                    if (result != null) {
                        rootStatus = result;
                        String jmxPath = detectJmxFilePath();
                        if (jmxPath != null) {
                            OverridePersistence.load(jmxPath, rootStatus);
                        }
                        statusMap.clear();
                        buildStatusMap(rootStatus);
                        extractorSourceMap.clear();
                        paramSourceMap.clear();
                        JMeterTreeModel tm = getTreeModel();
                        if (tm != null) {
                            JMeterTreeNode root = (JMeterTreeNode) tm.getRoot();
                            buildExtractorSourceMap(root);
                            buildParamSourceMap(root);
                        }
                        if (jmeterTree != null) {
                            jmeterTree.repaint();
                        }

                        // Update total users label in toolbar
                        String totalUsers = getTotalUserCount();
                        CorrelationParameterizationMenuCreator.updateTotalUsersLabel(totalUsers);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void toggleStatus(JMeterTreeNode node, boolean isCorrelation) {
        if (node == null || rootStatus == null) return;
        String nodePath = buildNodePath(node);
        ElementStatus elementStatus = statusMap.get(nodePath);
        if (elementStatus == null) elementStatus = statusMap.get(node.getName());
        if (elementStatus == null) return;

        ConfigurationStatus current = isCorrelation
                ? elementStatus.getEffectiveCorrelationStatus()
                : elementStatus.getEffectiveParameterizationStatus();

        ConfigurationStatus next = (current == ConfigurationStatus.CONFIGURED)
                ? ConfigurationStatus.NOT_APPLICABLE
                : ConfigurationStatus.CONFIGURED;

        if (isCorrelation) {
            elementStatus.setManualCorrelationOverride(next);
        } else {
            elementStatus.setManualParameterizationOverride(next);
        }
        saveAndRepaint();
    }

    private void setStatus(JMeterTreeNode node, boolean isCorrelation, ConfigurationStatus status) {
        if (node == null || rootStatus == null) return;
        String nodePath = buildNodePath(node);
        ElementStatus elementStatus = statusMap.get(nodePath);
        if (elementStatus == null) elementStatus = statusMap.get(node.getName());
        if (elementStatus == null) return;

        if (isCorrelation) {
            elementStatus.setManualCorrelationOverride(status);
        } else {
            elementStatus.setManualParameterizationOverride(status);
        }
        saveAndRepaint();
    }

    private void clearOverrides(JMeterTreeNode node) {
        if (node == null || rootStatus == null) return;
        String nodePath = buildNodePath(node);
        ElementStatus elementStatus = statusMap.get(nodePath);
        if (elementStatus == null) elementStatus = statusMap.get(node.getName());
        if (elementStatus == null) return;
        elementStatus.setManualCorrelationOverride(null);
        elementStatus.setManualParameterizationOverride(null);
        saveAndRepaint();
    }

    private void saveAndRepaint() {
        String jmxPath = detectJmxFilePath();
        if (jmxPath != null) {
            OverridePersistence.save(jmxPath, rootStatus);
        }
        if (jmeterTree != null) {
            jmeterTree.repaint();
        }
    }

    private void buildStatusMap(ElementStatus element) {
        statusMap.put(element.getPath(), element);
        statusMap.put(element.getElementName(), element);
        for (ElementStatus child : element.getChildren()) {
            buildStatusMap(child);
        }
    }

    private void buildExtractorSourceMap(JMeterTreeNode node) {
        if (node == null) return;
        collectExtractorNodesRecursive(node);
    }

    private void collectExtractorNodesRecursive(JMeterTreeNode node) {
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            TestElement te = child.getTestElement();
            if (te != null) {
                List<String> vars = getExtractedVarsFromElement(te);
                for (String var : vars) {
                    extractorSourceMap.computeIfAbsent(var, k -> new ArrayList<>()).add(child);
                }
            }
            collectExtractorNodesRecursive(child);
        }
    }

    private List<String> getExtractedVarsFromElement(TestElement te) {
        List<String> vars = new ArrayList<>();
        String className = te.getClass().getName();

        if (className.contains("JSONPostProcessor") || className.contains("JSONExtractor")) {
            addVarNames(te.getPropertyAsString("JSONPostProcessor.referenceNames"), vars);
        } else if (className.contains("RegexExtractor")) {
            addVarNames(te.getPropertyAsString("RegexExtractor.refname"), vars);
        } else if (className.contains("BoundaryExtractor")) {
            addVarNames(te.getPropertyAsString("BoundaryExtractor.refname"), vars);
        } else if (className.contains("XPath2Extractor")) {
            addVarNames(te.getPropertyAsString("XPath2Extractor.refname"), vars);
        } else if (className.contains("XPathExtractor")) {
            addVarNames(te.getPropertyAsString("XPathExtractor.refname"), vars);
        } else if (className.contains("HtmlExtractor") || className.contains("CSSSelector")) {
            addVarNames(te.getPropertyAsString("HtmlExtractor.refname"), vars);
        } else if (className.contains("Extractor")) {
            te.propertyIterator().forEachRemaining(prop -> {
                String propName = prop.getName();
                if (propName.endsWith(".refname") || propName.endsWith(".referenceNames")) {
                    addVarNames(prop.getStringValue(), vars);
                }
            });
        } else if (className.contains("JSR223") || className.contains("BeanShell")) {
            // Check both PreProcessor and PostProcessor
            String script = te.getPropertyAsString("script");
            if (script != null && script.contains("vars.put")) {
                Matcher matcher = VARS_PUT_PATTERN.matcher(script);
                while (matcher.find()) {
                    String varName = matcher.group(1).trim();
                    if (!varName.isEmpty() && !vars.contains(varName)) {
                        vars.add(varName);
                    }
                }
            }
        }
        return vars;
    }

    private void addVarNames(String refNames, List<String> vars) {
        if (refNames == null || refNames.trim().isEmpty()) return;
        for (String name : refNames.split(";")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty() && !vars.contains(trimmed)) {
                vars.add(trimmed);
            }
        }
    }

    private void buildParamSourceMap(JMeterTreeNode node) {
        if (node == null) return;
        TestElement te = node.getTestElement();
        if (te != null) {
            String className = te.getClass().getName();

            if (className.contains("CSVDataSet")) {
                String varNames = te.getPropertyAsString("variableNames");
                if (varNames != null && !varNames.trim().isEmpty()) {
                    for (String name : varNames.split(",")) {
                        String trimmed = name.trim();
                        if (!trimmed.isEmpty()) {
                            paramSourceMap.computeIfAbsent(trimmed, k -> new ArrayList<>()).add(node);
                        }
                    }
                }
            } else if (te instanceof Arguments) {
                Arguments args = (Arguments) te;
                Map<String, String> argMap = args.getArgumentsAsMap();
                for (String name : argMap.keySet()) {
                    if (!name.isEmpty()) {
                        paramSourceMap.computeIfAbsent(name, k -> new ArrayList<>()).add(node);
                    }
                }
            }
        }
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            buildParamSourceMap(child);
        }
    }

    private JMeterTreeNode resolveParamSource(String varName, JMeterTreeNode contextNode) {
        List<JMeterTreeNode> sources = paramSourceMap.get(varName);
        if (sources == null || sources.isEmpty()) return null;
        if (sources.size() == 1) return sources.get(0);

        JMeterTreeNode contextTG = findThreadGroupAncestor(contextNode);
        if (contextTG == null) return sources.get(0);

        for (JMeterTreeNode source : sources) {
            JMeterTreeNode sourceTG = findThreadGroupAncestor(source);
            if (sourceTG == contextTG) {
                return source;
            }
        }
        return sources.get(0);
    }

    private JMeterTreeNode findThreadGroupAncestor(JMeterTreeNode node) {
        javax.swing.tree.TreeNode[] path = node.getPath();
        for (int i = path.length - 1; i >= 0; i--) {
            if (path[i] instanceof JMeterTreeNode) {
                JMeterTreeNode ancestor = (JMeterTreeNode) path[i];
                TestElement te = ancestor.getTestElement();
                if (te != null && te.getClass().getName().contains("ThreadGroup")) {
                    return ancestor;
                }
            }
        }
        return null;
    }

    private JMeterTreeNode resolveExtractorSource(String varName, JMeterTreeNode contextNode) {
        List<JMeterTreeNode> sources = extractorSourceMap.get(varName);
        if (sources == null || sources.isEmpty()) return null;
        if (sources.size() == 1) return sources.get(0);

        JMeterTreeNode contextTG = findThreadGroupAncestor(contextNode);
        if (contextTG == null) return sources.get(0);

        for (JMeterTreeNode source : sources) {
            JMeterTreeNode sourceTG = findThreadGroupAncestor(source);
            if (sourceTG == contextTG) {
                return source;
            }
        }
        return sources.get(0);
    }

    private List<String> findUsedCorrelationVariables(JMeterTreeNode node) {
        List<String> used = new ArrayList<>();
        TestElement te = node.getTestElement();
        if (te == null) return used;

        StringBuilder sb = new StringBuilder();
        te.propertyIterator().forEachRemaining(prop -> {
            String value = prop.getStringValue();
            if (value != null) sb.append(value).append("\n");
        });

        Matcher matcher = VAR_USAGE_PATTERN.matcher(sb.toString());
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!varName.startsWith("__") && !varName.contains("(")) {
                List<JMeterTreeNode> sources = extractorSourceMap.get(varName);
                if (sources != null && !sources.isEmpty() && !used.contains(varName)) {
                    used.add(varName);
                }
            }
        }
        return used;
    }

    private boolean usesCorrelationVariables(JMeterTreeNode node) {
        return !findUsedCorrelationVariables(node).isEmpty();
    }

    private boolean usesCorrelationVariablesInSubtree(JMeterTreeNode node) {
        // Check the current node first
        if (usesCorrelationVariables(node)) {
            return true;
        }

        // Recursively check all children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            if (usesCorrelationVariablesInSubtree(child)) {
                return true;
            }
        }

        return false;
    }

    private boolean usesParameterizationVariables(JMeterTreeNode node) {
        List<String> allVars = findAllVariableReferences(node);
        for (String varName : allVars) {
            // Check if this variable comes from a parameterization source
            List<JMeterTreeNode> paramSources = paramSourceMap.get(varName);
            if (paramSources != null && !paramSources.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean usesParameterizationVariablesInSubtree(JMeterTreeNode node) {
        // Check the current node first
        if (usesParameterizationVariables(node)) {
            return true;
        }

        // Recursively check all children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            if (usesParameterizationVariablesInSubtree(child)) {
                return true;
            }
        }

        return false;
    }

    private String buildNodePath(JMeterTreeNode node) {
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

    private String getNodeType(JMeterTreeNode node) {
        TestElement te = node.getTestElement();
        if (te == null) return null;
        String className = te.getClass().getName();

        if (className.contains("ThreadGroup")) return "ThreadGroup";
        if (te instanceof HTTPSamplerProxy || className.contains("HTTPSampler")) return "HTTPSampler";

        // Check for controllers - be more inclusive to catch all controller types
        // This includes: SimpleController, LoopController, IfController, ParallelController, etc.
        if (className.contains("Controller")) {
            return "Controller";
        }

        // Also check if it implements the Controller interface (for plugin controllers)
        try {
            if (te instanceof org.apache.jmeter.control.Controller) {
                return "Controller";
            }
        } catch (Exception e) {
            // Ignore - not a controller
        }

        return null;
    }

    private boolean hasExtractorInSubtree(JMeterTreeNode node) {
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            TestElement te = child.getTestElement();
            if (te == null) continue;
            String className = te.getClass().getName();

            if (className.contains("Extractor") || className.contains("JSONPostProcessor") ||
                className.contains("RegexExtractor") || className.contains("BoundaryExtractor") ||
                className.contains("XPathExtractor") || className.contains("CSSSelector")) {
                return true;
            }
            if (className.contains("JSR223") || className.contains("BeanShell")) {
                String script = te.getPropertyAsString("script");
                if (script != null && script.contains("vars.put")) {
                    return true;
                }
            }
            if (hasExtractorInSubtree(child)) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern VARS_PUT_PATTERN = Pattern.compile("vars\\.put\\s*\\(\\s*[\"']([^\"']+)[\"']");

    private List<String> collectExtractedVariables(JMeterTreeNode node) {
        List<String> variables = new ArrayList<>();
        collectExtractedVariablesRecursive(node, variables);
        return variables;
    }

    private void collectExtractedVariablesRecursive(JMeterTreeNode node, List<String> variables) {
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            TestElement te = child.getTestElement();
            if (te == null) continue;
            String className = te.getClass().getName();

            if (className.contains("JSONPostProcessor") || className.contains("JSONExtractor")) {
                addVariableNames(te.getPropertyAsString("JSONPostProcessor.referenceNames"), variables);
            } else if (className.contains("RegexExtractor")) {
                addVariableNames(te.getPropertyAsString("RegexExtractor.refname"), variables);
            } else if (className.contains("BoundaryExtractor")) {
                addVariableNames(te.getPropertyAsString("BoundaryExtractor.refname"), variables);
            } else if (className.contains("XPath2Extractor")) {
                addVariableNames(te.getPropertyAsString("XPath2Extractor.refname"), variables);
            } else if (className.contains("XPathExtractor")) {
                addVariableNames(te.getPropertyAsString("XPathExtractor.refname"), variables);
            } else if (className.contains("HtmlExtractor") || className.contains("CSSSelector")) {
                addVariableNames(te.getPropertyAsString("HtmlExtractor.refname"), variables);
            } else if (className.contains("Extractor")) {
                te.propertyIterator().forEachRemaining(prop -> {
                    String propName = prop.getName();
                    if (propName.endsWith(".refname") || propName.endsWith(".referenceNames")) {
                        addVariableNames(prop.getStringValue(), variables);
                    }
                });
            } else if (className.contains("JSR223") || className.contains("BeanShell")) {
                String script = te.getPropertyAsString("script");
                if (script != null && script.contains("vars.put")) {
                    Matcher matcher = VARS_PUT_PATTERN.matcher(script);
                    while (matcher.find()) {
                        String varName = matcher.group(1).trim();
                        if (!varName.isEmpty() && !variables.contains(varName)) {
                            variables.add(varName);
                        }
                    }
                }
            }

            collectExtractedVariablesRecursive(child, variables);
        }
    }

    private void addVariableNames(String refNames, List<String> variables) {
        if (refNames == null || refNames.trim().isEmpty()) return;
        for (String name : refNames.split(";")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty() && !variables.contains(trimmed)) {
                variables.add(trimmed);
            }
        }
    }

    private void enhanceTextComponentsInGUI() {
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage == null) return;
            MainFrame mainFrame = guiPackage.getMainFrame();
            if (mainFrame == null) return;

            // Find and enhance all text components (silently, only log new detections)
            int previousTreeCount = enhancedResultsTrees.size();
            enhanceTextComponentsRecursive(mainFrame.getContentPane());

            // Only log when new trees are detected
            if (enhancedResultsTrees.size() > previousTreeCount) {
                System.out.println("[Plugin] Detected new listener trees. Total enhanced: " + enhancedResultsTrees.size());
            }
        } catch (Exception e) {
            System.err.println("[Plugin] Error during enhancement: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void enhanceTextComponentsRecursive(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JTextComponent) {
                JTextComponent textComp = (JTextComponent) comp;
                if (!enhancedTextComponents.contains(textComp)) {
                    addVariableNavigationListener(textComp);
                    enhancedTextComponents.add(textComp);
                }
            } else if (comp instanceof JTable) {
                JTable table = (JTable) comp;
                if (!enhancedTables.contains(table)) {
                    addTableVariableNavigationListener(table);
                    // DISABLED: Enable aggregate report navigation if this is an aggregate report table
                    // if (isAggregateReportTable(table)) {
                    //     addAggregateReportNavigationFeature(table);
                    // }
                    enhancedTables.add(table);
                }
            } else if (comp instanceof JTree) {
                JTree tree = (JTree) comp;
                // Skip the main JMeter test plan tree
                if (tree != jmeterTree && !enhancedResultsTrees.contains(tree)) {
                    // DISABLED: Enable View Results Tree navigation if this is a View Results Tree
                    // if (isViewResultsTree(tree)) {
                    //     addViewResultsTreeNavigationFeature(tree);
                    //     enhancedResultsTrees.add(tree);
                    // }
                }
            } else if (comp instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) comp;
                Component viewport = scrollPane.getViewport().getView();
                if (viewport instanceof JTable) {
                    JTable table = (JTable) viewport;
                    if (!enhancedTables.contains(table)) {
                        addTableVariableNavigationListener(table);
                        // DISABLED: Enable aggregate report navigation if this is an aggregate report table
                        // if (isAggregateReportTable(table)) {
                        //     addAggregateReportNavigationFeature(table);
                        // }
                        enhancedTables.add(table);
                    }
                } else if (viewport instanceof JTree) {
                    JTree tree = (JTree) viewport;
                    // Skip the main JMeter test plan tree
                    if (tree != jmeterTree && !enhancedResultsTrees.contains(tree)) {
                        // DISABLED: Enable View Results Tree navigation if this is a View Results Tree
                        // if (isViewResultsTree(tree)) {
                        //     addViewResultsTreeNavigationFeature(tree);
                        //     enhancedResultsTrees.add(tree);
                        // }
                    }
                }
            }

            if (comp instanceof Container) {
                enhanceTextComponentsRecursive((Container) comp);
            }
        }
    }

    private void addVariableNavigationListener(JTextComponent textComp) {
        textComp.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    handleVariableDoubleClick(textComp, e);
                }
            }
        });
    }

    private void addTableVariableNavigationListener(JTable table) {
        MouseAdapter tableListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    int row = table.rowAtPoint(e.getPoint());
                    int col = table.columnAtPoint(e.getPoint());
                    if (row >= 0 && col >= 0) {
                        // Stop editing first to get correct value
                        if (table.isEditing()) {
                            table.getCellEditor().stopCellEditing();
                        }

                        Object value = table.getValueAt(row, col);
                        if (value != null) {
                            String text = value.toString().trim();
                            JMeterTreeNode currentNode = getCurrentSelectedNode();

                            if (currentNode != null) {
                                // Handle ${varName} pattern for navigation
                                String varName = extractVariableNameFromText(text);
                                if (varName != null) {
                                    navigateToVariableSource(varName, currentNode);
                                }
                            }
                        }
                    }
                }
            }
        };

        table.addMouseListener(tableListener);

        table.addPropertyChangeListener("tableCellEditor", evt -> {
            if (table.isEditing()) {
                Component editor = table.getEditorComponent();
                if (editor instanceof JTextComponent) {
                    JTextComponent textComp = (JTextComponent) editor;
                    if (!enhancedTextComponents.contains(textComp)) {
                        addVariableNavigationListener(textComp);
                        enhancedTextComponents.add(textComp);
                    }
                }
            }
        });
    }

    private void handleVariableDoubleClick(JTextComponent textComp, MouseEvent e) {
        int caretPos = textComp.viewToModel2D(e.getPoint());
        String text = textComp.getText();
        if (text == null || text.isEmpty()) return;

        JMeterTreeNode currentNode = getCurrentSelectedNode();

        // Check if this is a JSR223 script (Pre/Post/Sampler) with vars.put() calls
        if (currentNode != null && isJSR223ProcessorNode(currentNode)) {
            // Extract variable name from vars.put("varName", ...) at cursor position
            String varName = extractVarsPutVariableAtPosition(text, caretPos);
            if (varName != null && !varName.isEmpty()) {
                // Highlight usages of this correlation variable (no thread group)
                highlightVariableUsages(varName, false);
                return;
            }
        }

        // Check if this is a plain variable name (not ${...} pattern) in an extractor
        if (currentNode != null && isExtractorNode(currentNode)) {
            // This might be the "Name of created variable" field in an extractor
            // Extractors can have semicolon-delimited variable names (e.g., "userId;_sessionToken;userName")
            String plainVarName = extractVariableFromDelimitedList(text, caretPos, ';');
            if (plainVarName != null && !plainVarName.isEmpty()) {
                // Highlight usages of this extracted variable (correlation - no thread group)
                highlightVariableUsages(plainVarName, false);
                return;
            }
        }

        // Check if this is a parameterization config element (CSV Data Set Config)
        if (currentNode != null && isParameterizationConfigNode(currentNode)) {
            // For CSV Data Set Config: "Variable Names (comma-delimited)" field
            String plainVarName = extractPlainVariableName(text, caretPos);
            if (plainVarName != null && !plainVarName.isEmpty()) {
                // Highlight usages of this parameterization variable (include thread group)
                highlightVariableUsages(plainVarName, true);
                return;
            }
        }

        // Otherwise, handle normal variable navigation (${varName})
        String varName = extractVariableAtPosition(text, caretPos);
        if (varName != null) {
            if (currentNode != null) {
                navigateToVariableSource(varName, currentNode);
            }
        }
    }

    private String extractPlainVariableName(String text, int position) {
        // CSV uses comma delimiter
        return extractVariableFromDelimitedList(text, position, ',');
    }

    /**
     * Extracts a single variable name from a delimited list based on cursor position.
     * Used for:
     * - JSON Extractor "Names of created variables" field (semicolon-delimited)
     * - CSV Data Set "Variable Names" field (comma-delimited)
     *
     * @param text The full text containing delimited variable names
     * @param position The cursor position
     * @param delimiter The delimiter character (';' for extractors, ',' for CSV)
     * @return The variable name at the cursor position, or null if not found
     */
    private String extractVariableFromDelimitedList(String text, int position, char delimiter) {
        if (text == null || position < 0 || position > text.length()) return null;

        text = text.trim();

        // Check if text contains ${} patterns - if so, skip this logic
        if (text.contains("${") || text.contains("}")) {
            return null;
        }

        String delimiterStr = String.valueOf(delimiter);

        // If it's a simple single variable name (no delimiters), return it
        if (!text.contains(delimiterStr)) {
            return text.isEmpty() ? null : text;
        }

        // If it contains delimiters, extract the variable name at the cursor position
        // Split by delimiter and find which segment the cursor is in
        String[] variables = text.split(delimiterStr.equals(";") ? ";" : ",");
        int currentPos = 0;

        for (String var : variables) {
            int segmentEnd = currentPos + var.length();
            if (position >= currentPos && position <= segmentEnd) {
                return var.trim();
            }
            currentPos = segmentEnd + 1; // +1 for the delimiter
        }

        return null;
    }

    private boolean isExtractorNode(JMeterTreeNode node) {
        if (node == null) return false;
        TestElement te = node.getTestElement();
        if (te == null) return false;

        String className = te.getClass().getName();
        // Note: JSR223/BeanShell processors are handled separately by isJSR223ProcessorNode
        // to support both vars.put() and vars.get() navigation
        return className.contains("Extractor") ||
               className.contains("JSONPostProcessor");
    }

    private boolean isParameterizationConfigNode(JMeterTreeNode node) {
        if (node == null) return false;
        TestElement te = node.getTestElement();
        if (te == null) return false;

        String className = te.getClass().getName();
        return className.contains("CSVDataSet");
    }

    private boolean isJSR223ProcessorNode(JMeterTreeNode node) {
        if (node == null) return false;
        TestElement te = node.getTestElement();
        if (te == null) return false;

        String className = te.getClass().getName();
        return className.contains("JSR223PostProcessor") ||
               className.contains("JSR223PreProcessor") ||
               className.contains("JSR223Sampler") ||
               className.contains("BeanShellPostProcessor") ||
               className.contains("BeanShellPreProcessor") ||
               className.contains("BeanShellSampler");
    }

    private String extractVarsPutVariableAtPosition(String text, int position) {
        if (text == null || position < 0 || position > text.length()) return null;

        // Pattern to match vars.put("variableName", ...)
        Pattern pattern = Pattern.compile("vars\\.put\\s*\\(\\s*[\"']([^\"']+)[\"']");
        Matcher matcher = pattern.matcher(text);

        // Find all matches and check if cursor is within the variable name
        while (matcher.find()) {
            int varNameStart = matcher.start(1);  // Start of captured group (variable name)
            int varNameEnd = matcher.end(1);      // End of captured group (variable name)

            // Check if cursor position is within this variable name
            if (position >= varNameStart && position <= varNameEnd) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private String extractVariableAtPosition(String text, int position) {
        if (text == null || position < 0 || position > text.length()) return null;

        // First, try to extract vars.get("variableName") pattern
        String varsGetVar = extractVarsGetVariableAtPosition(text, position);
        if (varsGetVar != null) {
            return varsGetVar;
        }

        // Find the nearest ${ before the position and } after the position
        int startIndex = text.lastIndexOf("${", position);
        if (startIndex == -1) return null;

        int endIndex = text.indexOf("}", position);
        if (endIndex == -1) endIndex = text.indexOf("}", startIndex);
        if (endIndex == -1 || endIndex <= startIndex) return null;

        // Check if the position is within the variable
        if (position >= startIndex && position <= endIndex + 1) {
            String varName = text.substring(startIndex + 2, endIndex);
            if (!varName.startsWith("__") && !varName.contains("(")) {
                return varName;
            }
        }
        return null;
    }

    private String extractVarsGetVariableAtPosition(String text, int position) {
        if (text == null || position < 0 || position > text.length()) return null;

        // Pattern to match vars.get("variableName") - supports both single and double quotes
        Pattern pattern = Pattern.compile("vars\\.get\\s*\\(\\s*[\"']([^\"']+)[\"']");
        Matcher matcher = pattern.matcher(text);

        // Find all matches and check if cursor is within the variable name
        while (matcher.find()) {
            int varNameStart = matcher.start(1);  // Start of captured group (variable name)
            int varNameEnd = matcher.end(1);      // End of captured group (variable name)

            // Check if cursor position is within this variable name
            if (position >= varNameStart && position <= varNameEnd) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private String extractVariableNameFromText(String text) {
        if (text == null || text.trim().isEmpty()) return null;

        text = text.trim();

        Matcher matcher = VAR_USAGE_PATTERN.matcher(text);
        if (matcher.find()) {
            String varName = matcher.group(1);
            if (!varName.startsWith("__") && !varName.contains("(")) {
                return varName;
            }
        }
        return null;
    }

    private JMeterTreeNode getCurrentSelectedNode() {
        if (jmeterTree == null) return null;
        TreePath selPath = jmeterTree.getSelectionPath();
        if (selPath != null && selPath.getLastPathComponent() instanceof JMeterTreeNode) {
            return (JMeterTreeNode) selPath.getLastPathComponent();
        }
        return null;
    }

    private void navigateToVariableSource(String varName, JMeterTreeNode contextNode) {
        JMeterTreeNode sourceNode = resolveExtractorSource(varName, contextNode);
        String sourceType = "Extractor";

        if (sourceNode == null) {
            sourceNode = resolveParamSource(varName, contextNode);
            sourceType = "Param";
        }

        if (sourceNode != null) {
            navigateToNode(sourceNode);
            showNavigationFeedback(varName, sourceType);
        } else {
            showVariableNotFoundMessage(varName);
        }
    }

    private void showNavigationFeedback(String varName, String sourceType) {
    }

    private void showVariableNotFoundMessage(String varName) {
        JOptionPane.showMessageDialog(jmeterTree,
                "Source not found for variable: ${" + varName + "}",
                "Variable Navigation",
                JOptionPane.WARNING_MESSAGE);
    }

    // ========== Aggregate Report Navigation Feature ==========

    /**
     * Checks if a table is an Aggregate Report table by examining its column names.
     */
    private boolean isAggregateReportTable(JTable table) {
        try {
            if (table.getColumnCount() >= 3) {
                String col0 = table.getColumnName(0);
                String col1 = table.getColumnName(1);
                // Aggregate Report has "Label" and "# Samples" as first columns
                return ("Label".equals(col0) && "# Samples".equals(col1)) ||
                       ("Label".equals(col0) && "#Samples".equals(col1));
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
    
    private void addAggregateReportNavigationFeature(JTable table) {
        MouseAdapter aggregateReportListener = new MouseAdapter() {
            private FontMetrics fontMetrics = null;

            private FontMetrics getFontMetrics(JTable table) {
                if (fontMetrics == null) {
                    fontMetrics = table.getFontMetrics(table.getFont());
                }
                return fontMetrics;
            }

            private boolean isOverIcons(MouseEvent e, int row, int col, String labelText) {
                if (row < 0 || col != 0 || labelText == null || labelText.isEmpty()) {
                    return false;
                }

                Rectangle cellRect = table.getCellRect(row, col, false);
                int mouseX = e.getX() - cellRect.x;

                // Calculate text width
                FontMetrics fm = getFontMetrics(table);
                int textWidth = fm.stringWidth(labelText);

                // Icons start after text with some padding (about 10 pixels)
                int iconStartX = textWidth + 10;
                // Icon zone is approximately 40 pixels wide (both icons together)
                int iconEndX = iconStartX + 40;

                return mouseX >= iconStartX && mouseX <= iconEndX;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());

                if (row >= 0 && col == 0) {
                    Object value = table.getValueAt(row, col);
                    if (value != null) {
                        String labelName = value.toString().trim();
                        if (!labelName.isEmpty() && !labelName.startsWith("TOTAL")) {
                            if (isOverIcons(e, row, col, labelName)) {
                                table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                                return;
                            }
                        }
                    }
                }
                table.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                table.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 1) {
                    return;
                }

                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());

                if (row < 0 || col != 0) {
                    return;
                }

                Object value = table.getValueAt(row, col);
                if (value == null) {
                    return;
                }

                String labelName = value.toString().trim();
                if (labelName.isEmpty() || labelName.startsWith("TOTAL")) {
                    return;
                }

                if (!isOverIcons(e, row, col, labelName)) {
                    return;
                }

                Rectangle cellRect = table.getCellRect(row, col, false);
                int clickX = e.getX() - cellRect.x;

                FontMetrics fm = getFontMetrics(table);
                int textWidth = fm.stringWidth(labelName);
                int iconStartX = textWidth + 10;

                int clickOffset = clickX - iconStartX;

                if (clickOffset >= 0 && clickOffset <= 20) {
                    SwingUtilities.invokeLater(() -> highlightAllSamplersWithoutExpanding(labelName));
                } else if (clickOffset > 20 && clickOffset <= 40) {
                    SwingUtilities.invokeLater(() -> navigateToSamplerByName(labelName));
                }
            }
        };

        table.addMouseListener(aggregateReportListener);
        table.addMouseMotionListener(aggregateReportListener);

        SwingUtilities.invokeLater(() -> {
            javax.swing.table.TableCellRenderer existingRenderer = table.getColumnModel().getColumn(0).getCellRenderer();
            if (existingRenderer == null) {
                existingRenderer = table.getDefaultRenderer(Object.class);
            }

            if (existingRenderer == null) {
                existingRenderer = new javax.swing.table.DefaultTableCellRenderer();
            }

            final javax.swing.table.TableCellRenderer originalRenderer = existingRenderer;

            table.getColumnModel().getColumn(0).setCellRenderer(new javax.swing.table.TableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                                                               boolean isSelected, boolean hasFocus, int row, int column) {
                    Component comp = originalRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                    if (comp instanceof JLabel && value != null) {
                        JLabel label = (JLabel) comp;
                        String labelText = value.toString();

                        if (!labelText.isEmpty() && !labelText.startsWith("TOTAL")) {
                            String htmlText = "<html>" + escapeHtml(labelText) +
                                             "&nbsp;&nbsp;&nbsp;<font color='#0066CC'><b>ⓘ</b></font>" +
                                             "&nbsp;&nbsp;<font color='#28A745'><b>➤</b></font></html>";
                            label.setText(htmlText);
                            label.setToolTipText("Click ⓘ to highlight all | Click ➤ to navigate to first");
                        }
                    }

                    return comp;
                }
            });

            table.revalidate();
            table.repaint();
        });
    }

    private void highlightAllSamplersByName(String samplerName) {
        if (samplerName == null || samplerName.trim().isEmpty() || jmeterTree == null) {
            return;
        }

        JMeterTreeModel treeModel = getTreeModel();
        if (treeModel == null) return;

        JMeterTreeNode root = (JMeterTreeNode) treeModel.getRoot();
        List<JMeterTreeNode> matches = new ArrayList<>();

        // Search for matching samplers
        findNodesByName(root, samplerName.trim(), matches);

        if (!matches.isEmpty()) {
            for (JMeterTreeNode node : matches) {
                TreePath path = new TreePath(node.getPath());
                jmeterTree.expandPath(path);
                jmeterTree.scrollPathToVisible(path);
            }

            TreePath[] paths = new TreePath[matches.size()];
            for (int i = 0; i < matches.size(); i++) {
                paths[i] = new TreePath(matches.get(i).getPath());
            }
            jmeterTree.setSelectionPaths(paths);

            String message = matches.size() == 1 ?
                    "Found 1 sampler highlighted in the tree" :
                    "Found " + matches.size() + " samplers highlighted in the tree";

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(jmeterTree,
                        message,
                        "Samplers Highlighted",
                        JOptionPane.INFORMATION_MESSAGE);
            });
        } else {
            JOptionPane.showMessageDialog(jmeterTree,
                    "Could not find sampler: " + samplerName,
                    "Sampler Not Found",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private Map<String, List<JMeterTreeNode>> pendingHighlights = new HashMap<>();

    private void highlightAllSamplersWithoutExpanding(String samplerName) {
        if (samplerName == null || samplerName.trim().isEmpty() || jmeterTree == null) {
            return;
        }

        JMeterTreeModel treeModel = getTreeModel();
        if (treeModel == null) return;

        JMeterTreeNode root = (JMeterTreeNode) treeModel.getRoot();
        List<JMeterTreeNode> matches = new ArrayList<>();

        findNodesByName(root, samplerName.trim(), matches);

        if (!matches.isEmpty()) {
            pendingHighlights.put(samplerName, matches);

            addTreeExpansionListener();

            List<TreePath> pathsToHighlight = new ArrayList<>();

            Map<JMeterTreeNode, List<JMeterTreeNode>> matchesByThreadGroup = new HashMap<>();
            for (JMeterTreeNode node : matches) {
                JMeterTreeNode threadGroupNode = findThreadGroupParent(node);
                if (threadGroupNode != null) {
                    matchesByThreadGroup.computeIfAbsent(threadGroupNode, k -> new ArrayList<>()).add(node);
                }
            }

            for (Map.Entry<JMeterTreeNode, List<JMeterTreeNode>> entry : matchesByThreadGroup.entrySet()) {
                JMeterTreeNode threadGroupNode = entry.getKey();
                List<JMeterTreeNode> groupMatches = entry.getValue();
                TreePath threadGroupPath = new TreePath(threadGroupNode.getPath());

                pathsToHighlight.add(threadGroupPath);

                if (jmeterTree.isExpanded(threadGroupPath)) {
                    for (JMeterTreeNode node : groupMatches) {
                        JMeterTreeNode controllerNode = findControllerParent(node);
                        if (controllerNode != null) {
                            pathsToHighlight.add(new TreePath(controllerNode.getPath()));
                        }
                        pathsToHighlight.add(new TreePath(node.getPath()));
                    }
                }
            }

            if (!pathsToHighlight.isEmpty()) {
                TreePath[] paths = pathsToHighlight.toArray(new TreePath[0]);

                // Highlight samplers in tree using selection (auto-clears when user clicks elsewhere)
                // DON'T scroll or request focus - keep user on listener screen
                jmeterTree.setSelectionPaths(paths);

                // Log silently - no dialog popup
                String message = matches.size() == 1 ?
                        "Highlighted 1 sampler" :
                        "Highlighted " + matches.size() + " samplers";
                System.out.println("[Highlight] " + message + " - selection will auto-clear when you click elsewhere");
            }
        } else {
            JOptionPane.showMessageDialog(jmeterTree,
                    "Could not find sampler: " + samplerName,
                    "Sampler Not Found",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private boolean treeExpansionListenerAdded = false;

    private void addTreeExpansionListener() {
        if (treeExpansionListenerAdded || jmeterTree == null) {
            return;
        }

        jmeterTree.addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
            @Override
            public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
                TreePath expandedPath = event.getPath();
                Object lastComponent = expandedPath.getLastPathComponent();

                if (!(lastComponent instanceof JMeterTreeNode)) {
                    return;
                }

                JMeterTreeNode expandedNode = (JMeterTreeNode) lastComponent;

                TestElement testElement = expandedNode.getTestElement();
                if (testElement == null) return;

                String className = testElement.getClass().getName();
                if (!className.contains("ThreadGroup")) {
                    return;
                }

                for (Map.Entry<String, List<JMeterTreeNode>> entry : pendingHighlights.entrySet()) {
                    List<JMeterTreeNode> matches = entry.getValue();
                    List<TreePath> pathsToHighlight = new ArrayList<>();

                    for (JMeterTreeNode match : matches) {
                        JMeterTreeNode matchThreadGroup = findThreadGroupParent(match);
                        if (matchThreadGroup != null && matchThreadGroup.equals(expandedNode)) {
                            JMeterTreeNode controllerNode = findControllerParent(match);
                            if (controllerNode != null) {
                                pathsToHighlight.add(new TreePath(controllerNode.getPath()));
                            }
                            pathsToHighlight.add(new TreePath(match.getPath()));
                        }
                    }

                    if (!pathsToHighlight.isEmpty()) {
                        TreePath[] currentSelection = jmeterTree.getSelectionPaths();
                        List<TreePath> allPaths = new ArrayList<>();

                        if (currentSelection != null) {
                            for (TreePath path : currentSelection) {
                                allPaths.add(path);
                            }
                        }

                        allPaths.addAll(pathsToHighlight);

                        jmeterTree.setSelectionPaths(allPaths.toArray(new TreePath[0]));
                    }
                }
            }

            @Override
            public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {
            }
        });

        treeExpansionListenerAdded = true;
    }

    private JMeterTreeNode findThreadGroupParent(JMeterTreeNode node) {
        while (node != null) {
            TestElement testElement = node.getTestElement();
            if (testElement != null) {
                String className = testElement.getClass().getName();
                if (className.contains("ThreadGroup")) {
                    return node;
                }
            }
            node = (JMeterTreeNode) node.getParent();
        }
        return null;
    }

    private JMeterTreeNode findControllerParent(JMeterTreeNode node) {
        JMeterTreeNode parent = (JMeterTreeNode) node.getParent();
        while (parent != null) {
            TestElement testElement = parent.getTestElement();
            if (testElement != null) {
                String className = testElement.getClass().getName();
                if (className.contains("ThreadGroup")) {
                    return null;
                }
                if (className.contains("Controller")) {
                    return parent;
                }
            }
            parent = (JMeterTreeNode) parent.getParent();
        }
        return null;
    }

    private void navigateToSamplerByName(String samplerName) {
        if (samplerName == null || samplerName.trim().isEmpty() || jmeterTree == null) {
            return;
        }

        JMeterTreeModel treeModel = getTreeModel();
        if (treeModel == null) return;

        JMeterTreeNode root = (JMeterTreeNode) treeModel.getRoot();
        List<JMeterTreeNode> matches = new ArrayList<>();

        findNodesByName(root, samplerName.trim(), matches);

        if (!matches.isEmpty()) {
            navigateToNode(matches.get(0));

            if (matches.size() > 1) {
                SwingUtilities.invokeLater(() -> showNavigationDialog(samplerName, matches));
            }
        } else {
            JOptionPane.showMessageDialog(jmeterTree,
                    "Could not find sampler: " + samplerName,
                    "Sampler Not Found",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showNavigationDialog(String samplerName, List<JMeterTreeNode> matches) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(jmeterTree), "Navigate Samplers", false);
        dialog.setLayout(new BorderLayout(10, 10));

        final int[] currentIndex = {0};

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel infoLabel = new JLabel("Found " + matches.size() + " samplers: " + samplerName);
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        infoPanel.add(infoLabel);

        JLabel positionLabel = new JLabel("Showing 1 of " + matches.size());
        positionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        positionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        JPanel positionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        positionPanel.add(positionLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton prevButton = new JButton("← Previous");
        JButton nextButton = new JButton("Next →");
        JButton closeButton = new JButton("Close");

        prevButton.setEnabled(false);

        prevButton.addActionListener(e -> {
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                navigateToNode(matches.get(currentIndex[0]));
                positionLabel.setText("Showing " + (currentIndex[0] + 1) + " of " + matches.size());

                nextButton.setEnabled(true);
                if (currentIndex[0] == 0) {
                    prevButton.setEnabled(false);
                }
            }
        });

        nextButton.addActionListener(e -> {
            if (currentIndex[0] < matches.size() - 1) {
                currentIndex[0]++;
                navigateToNode(matches.get(currentIndex[0]));
                positionLabel.setText("Showing " + (currentIndex[0] + 1) + " of " + matches.size());

                prevButton.setEnabled(true);
                if (currentIndex[0] == matches.size() - 1) {
                    nextButton.setEnabled(false);
                }
            }
        });

        closeButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(prevButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(closeButton);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(positionPanel, BorderLayout.NORTH);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        dialog.add(infoPanel, BorderLayout.NORTH);
        dialog.add(centerPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setSize(450, 180);
        dialog.setResizable(false);

        dialog.setLocationRelativeTo(null);

        dialog.setVisible(true);
    }

    private void findNodesByName(JMeterTreeNode node, String name, List<JMeterTreeNode> matches) {
        if (node == null) return;

        String nodeName = node.getName();
        if (nodeName != null && nodeName.equals(name)) {
            TestElement te = node.getTestElement();
            if (te != null) {
                String className = te.getClass().getName();
                if (className.contains("Sampler") || className.contains("Request")) {
                    matches.add(node);
                }
            }
        }

        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof JMeterTreeNode) {
                findNodesByName((JMeterTreeNode) child, name, matches);
            }
        }
    }

    // ========== View Results Tree Navigation Feature ==========

    /**
     * Checks if a JTree is a View Results Tree by examining its root node structure.
     * View Results Tree uses SearchableTreeNode as its root node class.
     */
    private boolean isViewResultsTree(JTree tree) {
        try {
            if (tree == null || tree.getModel() == null) {
                return false;
            }

            Object root = tree.getModel().getRoot();
            if (root == null) {
                return false;
            }

            // Check if root is SearchableTreeNode - View Results Tree uses this
            String rootClassName = root.getClass().getName();

            // View Results Tree uses org.apache.jmeter.visualizers.SearchableTreeNode
            if (!rootClassName.contains("SearchableTreeNode")) {
                return false;
            }

            // This is a View Results Tree!
            String rootString = root.toString();
            System.out.println("✓ [View Results Tree] Detected and enhanced! Root: " + rootString);
            return true;

        } catch (Exception e) {
            // If any exception, it's not a View Results Tree
            return false;
        }
    }

    /**
     * Adds navigation features to View Results Tree.
     * Adds clickable icons next to each sample result for navigating to the sampler in test plan.
     */
    private void addViewResultsTreeNavigationFeature(JTree resultsTree) {
        MouseAdapter resultsTreeListener = new MouseAdapter() {
            private FontMetrics fontMetrics = null;

            private FontMetrics getFontMetrics(JTree tree) {
                if (fontMetrics == null) {
                    fontMetrics = tree.getFontMetrics(tree.getFont());
                }
                return fontMetrics;
            }

            private boolean isOverIcons(MouseEvent e, int row, String labelText) {
                if (row < 0 || labelText == null || labelText.isEmpty()) {
                    return false;
                }

                Rectangle rowBounds = resultsTree.getRowBounds(row);
                if (rowBounds == null) return false;

                int mouseX = e.getX() - rowBounds.x;

                // Calculate text width
                FontMetrics fm = getFontMetrics(resultsTree);
                int textWidth = fm.stringWidth(labelText);

                // Account for tree node icon/expansion control (approximately 20 pixels)
                int treeIconOffset = 20;

                // Icons start after text with some padding (about 10 pixels)
                int iconStartX = treeIconOffset + textWidth + 10;
                // Icon zone is approximately 40 pixels wide (both icons together)
                int iconEndX = iconStartX + 40;

                return mouseX >= iconStartX && mouseX <= iconEndX;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int row = resultsTree.getRowForLocation(e.getX(), e.getY());
                if (row >= 0) {
                    TreePath path = resultsTree.getPathForRow(row);
                    if (path != null && path.getLastPathComponent() != null) {
                        String labelText = path.getLastPathComponent().toString();
                        // Skip the root node
                        if (!labelText.equals("Root") && isOverIcons(e, row, labelText)) {
                            resultsTree.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            return;
                        }
                    }
                }
                resultsTree.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                resultsTree.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 1) {
                    return;
                }

                int row = resultsTree.getRowForLocation(e.getX(), e.getY());
                if (row < 0) {
                    return;
                }

                TreePath path = resultsTree.getPathForRow(row);
                if (path == null || path.getLastPathComponent() == null) {
                    return;
                }

                String labelText = path.getLastPathComponent().toString();
                // Skip the root node
                if (labelText.equals("Root")) {
                    return;
                }

                if (!isOverIcons(e, row, labelText)) {
                    return;
                }

                Rectangle rowBounds = resultsTree.getRowBounds(row);
                int clickX = e.getX() - rowBounds.x;

                FontMetrics fm = getFontMetrics(resultsTree);
                int textWidth = fm.stringWidth(labelText);
                int treeIconOffset = 20;
                int iconStartX = treeIconOffset + textWidth + 10;

                int clickOffset = clickX - iconStartX;

                // Extract the sampler name from the label
                // View Results Tree format is typically: "samplerName" or "samplerName (response code)"
                String samplerName = extractSamplerNameFromResultLabel(labelText);

                if (clickOffset >= 0 && clickOffset <= 20) {
                    // Clicked on ⓘ icon - highlight all samplers
                    SwingUtilities.invokeLater(() -> highlightAllSamplersWithoutExpanding(samplerName));
                } else if (clickOffset > 20 && clickOffset <= 40) {
                    // Clicked on ➤ icon - navigate to sampler
                    SwingUtilities.invokeLater(() -> navigateToSamplerByName(samplerName));
                }
            }
        };

        resultsTree.addMouseListener(resultsTreeListener);
        resultsTree.addMouseMotionListener(resultsTreeListener);

        // Add custom cell renderer to show navigation icons
        SwingUtilities.invokeLater(() -> {
            TreeCellRenderer existingRenderer = resultsTree.getCellRenderer();
            if (existingRenderer == null) {
                existingRenderer = new javax.swing.tree.DefaultTreeCellRenderer();
            }

            final TreeCellRenderer originalResultsRenderer = existingRenderer;

            resultsTree.setCellRenderer(new TreeCellRenderer() {
                @Override
                public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                              boolean selected, boolean expanded,
                                                              boolean leaf, int row, boolean hasFocus) {
                    Component comp = originalResultsRenderer.getTreeCellRendererComponent(
                            tree, value, selected, expanded, leaf, row, hasFocus);

                    if (comp instanceof JLabel && value != null) {
                        JLabel label = (JLabel) comp;
                        String labelText = value.toString();

                        // Don't add icons to the root node
                        if (!labelText.equals("Root")) {
                            String htmlText = "<html>" + escapeHtml(labelText) +
                                             "&nbsp;&nbsp;&nbsp;<font color='#0066CC'><b>ⓘ</b></font>" +
                                             "&nbsp;&nbsp;<font color='#28A745'><b>➤</b></font></html>";
                            label.setText(htmlText);
                            label.setToolTipText("Click ⓘ to highlight all | Click ➤ to navigate to sampler");
                        }
                    }

                    return comp;
                }
            });

            resultsTree.revalidate();
            resultsTree.repaint();
        });
    }

    /**
     * Extracts the sampler name from a View Results Tree label.
     * Handles various formats like "samplerName", "samplerName (200)", etc.
     */
    private String extractSamplerNameFromResultLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return label;
        }

        // Remove response code and other metadata in parentheses
        // Format: "samplerName (200)" or "samplerName (Failed)"
        int parenIndex = label.indexOf('(');
        if (parenIndex > 0) {
            return label.substring(0, parenIndex).trim();
        }

        return label.trim();
    }

    private JTree findJMeterTree() {
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage == null) return null;
            MainFrame mainFrame = guiPackage.getMainFrame();
            if (mainFrame == null) return null;
            try {
                Field treeField = MainFrame.class.getDeclaredField("tree");
                treeField.setAccessible(true);
                Object tree = treeField.get(mainFrame);
                if (tree instanceof JTree) return (JTree) tree;
            } catch (Exception e) { }
            return findJTreeRecursive(mainFrame.getContentPane());
        } catch (Exception e) {
            return null;
        }
    }

    private JTree findJTreeRecursive(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JTree) {
                JTree tree = (JTree) comp;
                if (tree.getModel() != null && tree.getModel().getRoot() instanceof JMeterTreeNode) {
                    return tree;
                }
            }
            if (comp instanceof Container) {
                JTree found = findJTreeRecursive((Container) comp);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JMeterTreeModel getTreeModel() {
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) return guiPackage.getTreeModel();
        } catch (Exception e) { }
        return null;
    }

    private String detectJmxFilePath() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage != null) {
            String path = guiPackage.getTestPlanFile();
            if (path != null && !path.isEmpty()) return path;
        }
        return null;
    }

    private class TreeContextMenuListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) scheduleAppend(e);
        }
        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) scheduleAppend(e);
        }

        private void scheduleAppend(MouseEvent e) {
            TreePath path = jmeterTree.getPathForLocation(e.getX(), e.getY());
            if (path == null) return;
            Object lastComponent = path.getLastPathComponent();
            if (!(lastComponent instanceof JMeterTreeNode)) return;

            JMeterTreeNode node = (JMeterTreeNode) lastComponent;
            String nodeType = getNodeType(node);
            if (nodeType == null) return;

            SwingUtilities.invokeLater(() -> {
                MenuElement[] selectedPath = MenuSelectionManager.defaultManager().getSelectedPath();
                if (selectedPath.length > 0 && selectedPath[0] instanceof JPopupMenu) {
                    JPopupMenu popup = (JPopupMenu) selectedPath[0];
                    appendCPItems(popup, node);
                }
            });
        }
    }

    private void appendCPItems(JPopupMenu popup, JMeterTreeNode node) {
        String nodePath = buildNodePath(node);
        ElementStatus status = statusMap.get(nodePath);
        if (status == null) status = statusMap.get(node.getName());
        ConfigurationStatus corrStatus = (status != null)
                ? status.getEffectiveCorrelationStatus() : ConfigurationStatus.NOT_APPLICABLE;
        ConfigurationStatus paramStatus = (status != null)
                ? status.getEffectiveParameterizationStatus() : ConfigurationStatus.NOT_APPLICABLE;

        String nodeType = getNodeType(node);
        boolean hasExtractor = false;
        boolean hasManualCorrOverride = (status != null && status.getManualCorrelationOverride() != null);
        if ("HTTPSampler".equals(nodeType) || "Controller".equals(nodeType)) {
            hasExtractor = hasExtractorInSubtree(node);
        }

        popup.addSeparator();

        if (hasExtractor && !hasManualCorrOverride) {
            JMenuItem extUnmark = new JMenuItem("C: Unmark Ext \u2713 (override auto-detect)");
            extUnmark.addActionListener(ev -> setStatus(node, true, ConfigurationStatus.NOT_APPLICABLE));
            popup.add(extUnmark);
        } else {
            JMenuItem corrToggle = new JMenuItem(
                    corrStatus == ConfigurationStatus.CONFIGURED
                            ? "C: Uncheck Correlation" : "C: Mark Correlation Done \u2713");
            corrToggle.addActionListener(ev -> toggleStatus(node, true));
            popup.add(corrToggle);
        }

        JMenuItem paramToggle = new JMenuItem(
                paramStatus == ConfigurationStatus.CONFIGURED
                        ? "P: Uncheck Parameterization" : "P: Mark Parameterization Done \u2713");
        paramToggle.addActionListener(ev -> toggleStatus(node, false));
        popup.add(paramToggle);

        JMenuItem markBothDone = new JMenuItem("Mark Both Done \u2713");
        markBothDone.addActionListener(ev -> {
            setStatus(node, true, ConfigurationStatus.CONFIGURED);
            setStatus(node, false, ConfigurationStatus.CONFIGURED);
        });
        popup.add(markBothDone);

        JMenuItem markBothNA = new JMenuItem("Mark Both N/A \u2014");
        markBothNA.addActionListener(ev -> {
            setStatus(node, true, ConfigurationStatus.NOT_APPLICABLE);
            setStatus(node, false, ConfigurationStatus.NOT_APPLICABLE);
        });
        popup.add(markBothNA);

        JMenuItem resetItem = new JMenuItem("Reset to Auto-Detected");
        resetItem.addActionListener(ev -> clearOverrides(node));
        popup.add(resetItem);

        // For controllers, check the entire subtree for correlation variables
        // For samplers, only check the sampler itself
        boolean usesCorVars;
        if ("Controller".equals(nodeType)) {
            usesCorVars = usesCorrelationVariablesInSubtree(node);
        } else {
            usesCorVars = usesCorrelationVariables(node);
        }

        if (hasExtractor || usesCorVars) {
            popup.addSeparator();
            JMenuItem viewTraceItem = new JMenuItem("View / Trace Correlation Variables ⓘ");
            viewTraceItem.addActionListener(ev -> showCombinedCorrelationDialog(node));
            popup.add(viewTraceItem);
        }

        popup.pack();
    }

    private class ViewIconClickListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 1) return;

            TreePath path = jmeterTree.getClosestPathForLocation(e.getX(), e.getY());
            if (path == null) return;

            int row = jmeterTree.getRowForPath(path);
            if (row < 0) return;
            Rectangle rowBounds = jmeterTree.getRowBounds(row);
            if (rowBounds == null) return;

            if (e.getY() < rowBounds.y || e.getY() > rowBounds.y + rowBounds.height) return;

            Object lastComponent = path.getLastPathComponent();
            if (!(lastComponent instanceof JMeterTreeNode)) return;

            JMeterTreeNode node = (JMeterTreeNode) lastComponent;
            String nodeType = getNodeType(node);
            if (nodeType == null || "ThreadGroup".equals(nodeType)) return;

            boolean hasExtractor = hasExtractorInSubtree(node);

            // For controllers, check the entire subtree for correlation variables
            // For samplers, only check the sampler itself
            boolean usesCorVars;
            if ("Controller".equals(nodeType)) {
                usesCorVars = usesCorrelationVariablesInSubtree(node);
            } else {
                usesCorVars = usesCorrelationVariables(node);
            }

            if (!hasExtractor && !usesCorVars) return;

            Component origComp = originalRenderer.getTreeCellRendererComponent(
                    jmeterTree, node, jmeterTree.isRowSelected(row),
                    jmeterTree.isExpanded(row), node.isLeaf(), row, false);
            int originalWidth = origComp.getPreferredSize().width;

            int clickXInRow = e.getX() - rowBounds.x;
            if (clickXInRow > originalWidth) {
                showCombinedCorrelationDialog(node);
            }
        }
    }

    private void showCombinedCorrelationDialog(JMeterTreeNode node) {
        // Check if a correlation dialog is already open
        if (currentCorrelationDialog != null && currentCorrelationDialog.isVisible()) {
            // Bring existing dialog to front instead of creating a new one
            currentCorrelationDialog.toFront();
            currentCorrelationDialog.requestFocus();
            return;
        }

        // Dispose of any previously closed dialog
        if (currentCorrelationDialog != null) {
            currentCorrelationDialog.dispose();
            currentCorrelationDialog = null;
        }

        // For controllers, collect from entire subtree
        // For samplers, only collect from the sampler itself
        String nodeType = getNodeType(node);
        List<String> extractedVars = collectExtractedVariables(node);

        List<String> allUsedVars;
        if ("Controller".equals(nodeType)) {
            // Collect all variables used in the entire controller subtree
            allUsedVars = findAllVariableReferencesInSubtree(node);
        } else {
            // For samplers, only collect from the sampler itself
            allUsedVars = findAllVariableReferences(node);
        }

        allUsedVars.removeAll(extractedVars);

        if (extractedVars.isEmpty() && allUsedVars.isEmpty()) {
            JOptionPane.showMessageDialog(jmeterTree,
                    "No variables found in this request.",
                    "Correlation Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Create main dialog
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(jmeterTree),
                "Correlation Info", false);
        currentCorrelationDialog = dialog; // Track this dialog
        dialog.setLayout(new BorderLayout());
        dialog.setBackground(Color.WHITE);

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(222, 226, 230)),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        // Left side: Title + Path
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Correlation Info");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(titleLabel);

        JLabel pathLabel = new JLabel(node.getName());
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        pathLabel.setForeground(new Color(108, 117, 125));
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(pathLabel);

        headerPanel.add(titlePanel, BorderLayout.WEST);

        dialog.add(headerPanel, BorderLayout.NORTH);

        // Content Panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        int totalVars = extractedVars.size() + allUsedVars.size();

        // Main Header
        JPanel mainHeader = new JPanel(new BorderLayout());
        mainHeader.setOpaque(false);
        mainHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel mainTitle = new JLabel("Variables in Request");
        mainTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        mainTitle.setForeground(new Color(73, 80, 87));
        mainHeader.add(mainTitle, BorderLayout.WEST);

        JLabel countLabel = new JLabel(totalVars + " Variables");
        countLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        countLabel.setForeground(new Color(0, 123, 255));
        mainHeader.add(countLabel, BorderLayout.EAST);

        contentPanel.add(mainHeader);
        contentPanel.add(Box.createVerticalStrut(3));

        JLabel viewLabel = new JLabel("View and navigate the variables extracted and used in this request");
        viewLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        viewLabel.setForeground(new Color(108, 117, 125));
        viewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(viewLabel);
        contentPanel.add(Box.createVerticalStrut(8));

        // Section 1: Extracted Variables (if any)
        if (!extractedVars.isEmpty()) {
            // Section header with icon
            JPanel extractedHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            extractedHeader.setOpaque(false);
            extractedHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
            extractedHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

            JLabel extractedIcon = new JLabel("↓");
            extractedIcon.setFont(new Font("Segoe UI", Font.BOLD, 13));
            extractedIcon.setForeground(new Color(40, 167, 69));
            extractedHeader.add(extractedIcon);
            extractedHeader.add(Box.createHorizontalStrut(5));

            JLabel extractedTitle = new JLabel("Extracted by This Sampler");
            extractedTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
            extractedTitle.setForeground(new Color(73, 80, 87));
            extractedHeader.add(extractedTitle);

            contentPanel.add(extractedHeader);
            contentPanel.add(Box.createVerticalStrut(6));

            for (String varName : extractedVars) {
                // Find the actual extractor node for this variable
                JMeterTreeNode extractorNode = resolveExtractorSource(varName, node);
                String sourceTypeDisplay = "Extractor";
                if (extractorNode != null) {
                    TestElement te = extractorNode.getTestElement();
                    if (te != null) {
                        String className = te.getClass().getName();
                        if (className.contains("JSONPostProcessor") || className.contains("JSONExtractor")) {
                            sourceTypeDisplay = "JSON Extractor";
                        } else if (className.contains("JSR223PreProcessor")) {
                            sourceTypeDisplay = "JSR223 PreProcessor";
                        } else if (className.contains("JSR223PostProcessor")) {
                            sourceTypeDisplay = "JSR223 PostProcessor";
                        } else if (className.contains("BeanShellPreProcessor")) {
                            sourceTypeDisplay = "BeanShell PreProcessor";
                        } else if (className.contains("BeanShellPostProcessor")) {
                            sourceTypeDisplay = "BeanShell PostProcessor";
                        } else if (className.contains("BoundaryExtractor")) {
                            sourceTypeDisplay = "Boundary Extractor";
                        } else if (className.contains("XPathExtractor")) {
                            sourceTypeDisplay = "XPath Extractor";
                        } else if (className.contains("RegexExtractor")) {
                            sourceTypeDisplay = "Regular Expression Extractor";
                        }
                    }
                }

                JPanel varRow = createSimpleVariableRow(varName, extractorNode, sourceTypeDisplay, "Extractor", node);
                contentPanel.add(varRow);
            }

            contentPanel.add(Box.createVerticalStrut(8));
        }

        // Section 2: Variables Used in Request (if any)
        if (!allUsedVars.isEmpty()) {
            // Section header with icon
            JPanel usedHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            usedHeader.setOpaque(false);
            usedHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
            usedHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

            JLabel usedIcon = new JLabel("↑");
            usedIcon.setFont(new Font("Segoe UI", Font.BOLD, 13));
            usedIcon.setForeground(new Color(0, 123, 255));
            usedHeader.add(usedIcon);
            usedHeader.add(Box.createHorizontalStrut(5));

            JLabel usedTitle = new JLabel("Variables Used by This Sampler");
            usedTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
            usedTitle.setForeground(new Color(73, 80, 87));
            usedHeader.add(usedTitle);

            contentPanel.add(usedHeader);
            contentPanel.add(Box.createVerticalStrut(6));
        }

        // Used Variables List
        for (String varName : allUsedVars) {
            JMeterTreeNode sourceNode = resolveExtractorSource(varName, node);
            String sourceType = "Extractor";
            String sourceTypeDisplay = "Regex Extractor";

            if (sourceNode == null) {
                sourceNode = resolveParamSource(varName, node);
                sourceType = "Param";
                sourceTypeDisplay = "User Defined";
            } else {
                // Determine extractor type
                TestElement te = sourceNode.getTestElement();
                if (te != null) {
                    String className = te.getClass().getName();
                    if (className.contains("JSONPostProcessor") || className.contains("JSONExtractor")) {
                        sourceTypeDisplay = "JSON Extractor";
                    } else if (className.contains("JSR223PreProcessor")) {
                        sourceTypeDisplay = "JSR223 PreProcessor";
                    } else if (className.contains("JSR223PostProcessor")) {
                        sourceTypeDisplay = "JSR223 PostProcessor";
                    } else if (className.contains("BeanShellPreProcessor")) {
                        sourceTypeDisplay = "BeanShell PreProcessor";
                    } else if (className.contains("BeanShellPostProcessor")) {
                        sourceTypeDisplay = "BeanShell PostProcessor";
                    } else if (className.contains("BoundaryExtractor")) {
                        sourceTypeDisplay = "Boundary Extractor";
                    } else if (className.contains("XPathExtractor")) {
                        sourceTypeDisplay = "XPath Extractor";
                    } else if (className.contains("RegexExtractor")) {
                        sourceTypeDisplay = "Regular Expression Extractor";
                    }
                }
            }

            JPanel varRow = createSimpleVariableRow(varName, sourceNode, sourceTypeDisplay, sourceType, node);
            contentPanel.add(varRow);
        }

        // Use scrollbar only if more than 10 variables
        if (totalVars > 10) {
            JScrollPane scrollPane = new JScrollPane(contentPanel);
            scrollPane.setBorder(null);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            dialog.add(scrollPane, BorderLayout.CENTER);
        } else {
            // For 10 or fewer variables, no scrollbar - let dialog auto-size
            dialog.add(contentPanel, BorderLayout.CENTER);
        }

        // Footer Panel
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(new Color(248, 249, 250));
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(222, 226, 230)),
            BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));

        JLabel footerText = new JLabel("Double-click a variable name or click \"Go To\" to navigate to its source.");
        footerText.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        footerText.setForeground(new Color(108, 117, 125));
        footerPanel.add(footerText, BorderLayout.WEST);

        JButton closeButton = new JButton("Close");
        closeButton.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        closeButton.setForeground(new Color(108, 117, 125));
        closeButton.setBackground(Color.WHITE);
        closeButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230), 1),
            BorderFactory.createEmptyBorder(4, 14, 4, 14)
        ));
        closeButton.setFocusPainted(false);
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setBackground(new Color(248, 249, 250));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setBackground(Color.WHITE);
            }
        });
        footerPanel.add(closeButton, BorderLayout.EAST);

        dialog.add(footerPanel, BorderLayout.SOUTH);

        // Size calculation based on number of variables
        int preferredWidth = 700;

        if (totalVars > 10) {
            // Fixed height with scrollbar for many variables
            int maxHeight = 450;
            dialog.setSize(preferredWidth, maxHeight);
        } else {
            dialog.pack();
            int preferredHeight = dialog.getPreferredSize().height;
            dialog.setSize(preferredWidth, preferredHeight);
        }

        dialog.setLocationRelativeTo(jmeterTree);

        // Add window listener to clear reference when dialog is closed
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (currentCorrelationDialog == dialog) {
                    currentCorrelationDialog = null;
                }
            }
        });

        dialog.setVisible(true);
    }

    /**
     * Creates a simple, clean variable row without icons.
     */
    private JPanel createSimpleVariableRow(String varName, JMeterTreeNode sourceNode, String sourceTypeDisplay,
                                           String sourceType, JMeterTreeNode contextNode) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(233, 236, 239)),
            BorderFactory.createEmptyBorder(5, 0, 5, 0)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);

        JLabel varLabel = new JLabel("${" + varName + "}");
        varLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        varLabel.setForeground("Extractor".equals(sourceType) ? new Color(111, 66, 193) : new Color(25, 135, 84));
        leftPanel.add(varLabel);

        leftPanel.add(Box.createHorizontalStrut(6));

        JLabel badgeLabel = new JLabel(sourceTypeDisplay);
        badgeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        badgeLabel.setForeground("Extractor".equals(sourceType) ? new Color(111, 66, 193) : new Color(25, 135, 84));
        badgeLabel.setOpaque(true);
        badgeLabel.setBackground("Extractor".equals(sourceType) ? new Color(237, 233, 254) : new Color(209, 231, 221));
        badgeLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder("Extractor".equals(sourceType) ? new Color(111, 66, 193) : new Color(25, 135, 84), 1),
            BorderFactory.createEmptyBorder(1, 4, 1, 4)
        ));
        leftPanel.add(badgeLabel);

        if (sourceNode != null) {
            leftPanel.add(Box.createHorizontalStrut(5));
            JLabel sourceNameLabel = new JLabel(sourceNode.getName());
            sourceNameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            sourceNameLabel.setForeground(new Color(108, 117, 125));
            leftPanel.add(sourceNameLabel);
        } else {
            leftPanel.add(Box.createHorizontalStrut(5));
            JLabel notFoundLabel = new JLabel("source not found");
            notFoundLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
            notFoundLabel.setForeground(new Color(173, 181, 189));
            leftPanel.add(notFoundLabel);
        }

        row.add(leftPanel, BorderLayout.CENTER);

        if (sourceNode != null) {
            JButton goToBtn = new JButton("Go To  →");
            goToBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            goToBtn.setForeground(Color.WHITE);
            goToBtn.setBackground(new Color(0, 123, 255));
            goToBtn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            goToBtn.setFocusPainted(false);
            goToBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            goToBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    goToBtn.setBackground(new Color(0, 105, 217));
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    goToBtn.setBackground(new Color(0, 123, 255));
                }
            });

            final JMeterTreeNode target = sourceNode;
            goToBtn.addActionListener(ev -> navigateToNode(target));
            row.add(goToBtn, BorderLayout.EAST);
        } else {
            JLabel naLabel = new JLabel("N/A");
            naLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            naLabel.setForeground(new Color(173, 181, 189));
            naLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            row.add(naLabel, BorderLayout.EAST);
        }

        return row;
    }

    private List<String> findAllVariableReferences(JMeterTreeNode node) {
        List<String> vars = new ArrayList<>();
        TestElement te = node.getTestElement();
        if (te == null) return vars;

        StringBuilder sb = new StringBuilder();
        te.propertyIterator().forEachRemaining(prop -> {
            String value = prop.getStringValue();
            if (value != null) sb.append(value).append("\n");
        });

        Matcher matcher = VAR_USAGE_PATTERN.matcher(sb.toString());
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!varName.startsWith("__") && !varName.contains("(") && !vars.contains(varName)) {
                vars.add(varName);
            }
        }
        return vars;
    }

    /**
     * Finds all variable references in the entire subtree (node + all children).
     * Used for showing all variables used within a controller.
     */
    private List<String> findAllVariableReferencesInSubtree(JMeterTreeNode node) {
        List<String> vars = new ArrayList<>();
        collectVariableReferencesRecursive(node, vars);
        return vars;
    }

    private void collectVariableReferencesRecursive(JMeterTreeNode node, List<String> vars) {
        if (node == null) return;

        // Collect from current node
        TestElement te = node.getTestElement();
        if (te != null) {
            StringBuilder sb = new StringBuilder();
            te.propertyIterator().forEachRemaining(prop -> {
                String value = prop.getStringValue();
                if (value != null) sb.append(value).append("\n");
            });

            Matcher matcher = VAR_USAGE_PATTERN.matcher(sb.toString());
            while (matcher.find()) {
                String varName = matcher.group(1);
                if (!varName.startsWith("__") && !varName.contains("(") && !vars.contains(varName)) {
                    vars.add(varName);
                }
            }
        }

        // Recurse through children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            collectVariableReferencesRecursive(child, vars);
        }
    }

    private void navigateToNode(JMeterTreeNode targetNode) {
        if (targetNode == null || jmeterTree == null) return;
        TreePath targetPath = new TreePath(targetNode.getPath());
        jmeterTree.setSelectionPath(targetPath);
        jmeterTree.scrollPathToVisible(targetPath);
    }

    /**
     * Shows a brief, auto-dismissing toast notification without blocking the user.
     * Displays in the bottom-right corner for 3 seconds.
     */
    private void showToastNotification(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                GuiPackage guiPackage = GuiPackage.getInstance();
                if (guiPackage == null) return;
                MainFrame mainFrame = guiPackage.getMainFrame();
                if (mainFrame == null) return;

                // Create a simple, non-modal toast panel
                JPanel toastPanel = new JPanel();
                toastPanel.setLayout(new BorderLayout(10, 10));
                toastPanel.setBackground(new Color(51, 51, 51)); // Dark background
                toastPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(100, 100, 100), 1),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
                ));

                JLabel messageLabel = new JLabel(message);
                messageLabel.setForeground(Color.WHITE);
                messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
                toastPanel.add(messageLabel, BorderLayout.CENTER);

                // Create a non-modal window
                final JWindow toast = new JWindow(mainFrame);
                toast.setAlwaysOnTop(true);
                toast.getContentPane().add(toastPanel);
                toast.pack();

                // Position in bottom-right corner
                Dimension screenSize = mainFrame.getSize();
                Point mainFrameLocation = mainFrame.getLocationOnScreen();
                int x = mainFrameLocation.x + screenSize.width - toast.getWidth() - 20;
                int y = mainFrameLocation.y + screenSize.height - toast.getHeight() - 60;
                toast.setLocation(x, y);

                toast.setVisible(true);

                // Auto-dismiss after 3 seconds
                javax.swing.Timer timer = new javax.swing.Timer(3000, e -> toast.dispose());
                timer.setRepeats(false);
                timer.start();

            } catch (Exception e) {
                // Fallback: print to console if toast fails
                System.out.println("[Toast] " + message);
            }
        });
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Highlights all samplers that use the specified variable with purple border.
     * Also highlights their parent controllers and thread groups for better visibility.
     */
    private void highlightVariableUsages(String variableName) {
        highlightVariableUsages(variableName, false);
    }

    /**
     * Highlights all samplers that use the specified variable with purple border.
     * @param variableName The variable name to search for
     * @param includeThreadGroup Whether to highlight thread groups (true for parameterization, false for correlation)
     */
    private void highlightVariableUsages(String variableName, boolean includeThreadGroup) {
        if (variableName == null || variableName.trim().isEmpty()) return;

        // Clear previous highlights
        clearHighlights();

        currentHighlightedVariable = variableName;
        JMeterTreeModel treeModel = getTreeModel();
        if (treeModel == null) return;

        JMeterTreeNode root = (JMeterTreeNode) treeModel.getRoot();
        List<JMeterTreeNode> matchingNodes = new ArrayList<>();

        // Find all samplers that use this variable
        findNodesUsingVariable(root, variableName, matchingNodes);

        if (matchingNodes.isEmpty()) {
            // Silently do nothing if variable is not used
            return;
        }

        // Add all matching node paths to highlight set
        for (JMeterTreeNode node : matchingNodes) {
            String nodePath = buildNodePath(node);
            highlightedNodePaths.add(nodePath);

            // Also highlight parent controllers and optionally thread groups
            highlightParentControllers(node, includeThreadGroup);
        }

        // Repaint tree to show highlights
        if (jmeterTree != null) {
            jmeterTree.repaint();
        }

        // No dialog - just highlight instantly
    }

    /**
     * Highlights all parent controllers of a node.
     * @param node The node whose parents should be highlighted
     * @param includeThreadGroup Whether to highlight thread groups (true for parameterization, false for correlation)
     */
    private void highlightParentControllers(JMeterTreeNode node, boolean includeThreadGroup) {
        if (node == null) return;

        javax.swing.tree.TreeNode parent = node.getParent();
        while (parent != null && parent instanceof JMeterTreeNode) {
            JMeterTreeNode parentNode = (JMeterTreeNode) parent;
            TestElement te = parentNode.getTestElement();

            if (te != null) {
                String className = te.getClass().getName();

                // Highlight controllers (not Thread Groups)
                if (className.contains("Controller") && !className.contains("ThreadGroup")) {
                    String parentPath = buildNodePath(parentNode);
                    highlightedNodePaths.add(parentPath);
                }

                // Handle ThreadGroup
                if (className.contains("ThreadGroup")) {
                    // Highlight ThreadGroup if requested (for parameterization variables)
                    if (includeThreadGroup) {
                        String parentPath = buildNodePath(parentNode);
                        highlightedNodePaths.add(parentPath);
                    }
                    break; // Stop at ThreadGroup level
                }
            }

            parent = parentNode.getParent();
        }
    }

    private void findNodesUsingVariable(JMeterTreeNode node, String variableName, List<JMeterTreeNode> results) {
        if (node == null) return;

        TestElement te = node.getTestElement();
        if (te != null) {
            // Check if this is a sampler
            String className = te.getClass().getName();
            if (te instanceof HTTPSamplerProxy || className.contains("Sampler")) {
                // Check if this sampler uses the variable
                if (samplerUsesVariable(te, variableName)) {
                    results.add(node);
                }
            }
        }

        // Recurse through children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof JMeterTreeNode) {
                findNodesUsingVariable((JMeterTreeNode) child, variableName, results);
            }
        }
    }

    private boolean samplerUsesVariable(TestElement element, String variableName) {
        StringBuilder sb = new StringBuilder();
        element.propertyIterator().forEachRemaining(prop -> {
            String value = prop.getStringValue();
            if (value != null) {
                sb.append(value).append("\n");
            }
        });

        String text = sb.toString();
        // Look for ${variableName} pattern
        Pattern pattern = Pattern.compile("\\$\\{" + Pattern.quote(variableName) + "\\}");
        Matcher matcher = pattern.matcher(text);
        return matcher.find();
    }

    /**
     * Clears all purple highlights from the tree.
     */
    public void clearHighlights() {
        highlightedNodePaths.clear();
        currentHighlightedVariable = null;
        if (jmeterTree != null) {
            jmeterTree.repaint();
        }
    }

    private class InlineStatusRenderer implements TreeCellRenderer {
        private final TreeCellRenderer delegate;

        InlineStatusRenderer(TreeCellRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component original = delegate.getTreeCellRendererComponent(
                    tree, value, selected, expanded, leaf, row, hasFocus);

            if (!(value instanceof JMeterTreeNode)) return original;

            JMeterTreeNode node = (JMeterTreeNode) value;
            String nodeType = getNodeType(node);
            if (nodeType == null) return original;

            String nodePath = buildNodePath(node);
            ElementStatus status = statusMap.get(nodePath);
            if (status == null) {
                status = statusMap.get(node.getName());
            }
            ConfigurationStatus corrStatus;
            ConfigurationStatus paramStatus;

            if (status != null) {
                corrStatus = status.getEffectiveCorrelationStatus();
                paramStatus = status.getEffectiveParameterizationStatus();
            } else {
                corrStatus = ConfigurationStatus.NOT_APPLICABLE;
                paramStatus = ConfigurationStatus.NOT_APPLICABLE;
            }

            boolean hasExtractor = hasExtractorInSubtree(node);
            boolean hasManualCorrOverride = (status != null && status.getManualCorrelationOverride() != null);

            // For controllers and thread groups, check the entire subtree for variable usage
            // For samplers, only check the sampler itself
            boolean usesCorrelationVars;
            boolean usesParamVars;

            if ("Controller".equals(nodeType) || "ThreadGroup".equals(nodeType)) {
                usesCorrelationVars = usesCorrelationVariablesInSubtree(node);
                usesParamVars = usesParameterizationVariablesInSubtree(node);
            } else {
                usesCorrelationVars = usesCorrelationVariables(node);
                usesParamVars = usesParameterizationVariables(node);
            }

            String statusText = buildStatusText(nodeType, corrStatus, paramStatus, hasExtractor, hasManualCorrOverride, usesCorrelationVars, usesParamVars, node);

            if (original instanceof JLabel) {
                JLabel label = (JLabel) original;
                String existingText = label.getText();
                label.setText("<html>" + escapeHtml(existingText) + " " + statusText + "</html>");
            }

            // Apply purple border if this is a highlighted element (HTTP Sampler, Controller, or ThreadGroup)
            if (highlightedNodePaths.contains(nodePath) &&
                ("HTTPSampler".equals(nodeType) || "Controller".equals(nodeType) || "ThreadGroup".equals(nodeType))) {
                // Create a panel with purple border to wrap the original component
                JPanel wrapper = new JPanel(new BorderLayout());
                wrapper.setOpaque(false);
                wrapper.setBorder(BorderFactory.createLineBorder(new Color(153, 51, 255), 2)); // Purple border, 2px thick
                wrapper.add(original, BorderLayout.CENTER);
                return wrapper;
            }

            return original;
        }

        private String buildStatusText(String nodeType, ConfigurationStatus corrStatus,
                                       ConfigurationStatus paramStatus, boolean hasExtractor,
                                       boolean hasManualCorrOverride, boolean usesCorrelationVars, boolean usesParamVars,
                                       JMeterTreeNode node) {
            StringBuilder sb = new StringBuilder();

            boolean isThreadGroup = "ThreadGroup".equals(nodeType);
            sb.append("<b><font color='");
            if (hasExtractor && !isThreadGroup && !hasManualCorrOverride) {
                sb.append("#008C00'>C:Ext\u2713</font></b>");
                sb.append("<font color='#0066CC'> \u24D8</font>");
            } else if (usesCorrelationVars && !isThreadGroup) {
                sb.append("#008C00'>C:\u2713</font></b>");
                sb.append("<font color='#0066CC'> \u24D8</font>");
            } else if (corrStatus == ConfigurationStatus.CONFIGURED) {
                sb.append("#008C00'>C:\u2713</font></b>");
            } else if (hasExtractor && isThreadGroup && !hasManualCorrOverride) {
                sb.append("#008C00'>C:\u2713</font></b>");
            } else {
                sb.append("#999999'>C:\u2014</font></b>");
            }

            sb.append("&nbsp;");

            sb.append("<b><font color='");
            if (usesParamVars || paramStatus == ConfigurationStatus.CONFIGURED) {
                sb.append("#008C00'>P:\u2713");
            } else {
                sb.append("#999999'>P:\u2014");
            }
            sb.append("</font></b>");

            // Add user count for ThreadGroups
            if (isThreadGroup) {
                String userCount = getThreadGroupUserCount(node);

                // Check if thread group is disabled
                TestElement te = node.getTestElement();
                boolean isEnabled = (te != null && te.isEnabled());

                if (isEnabled) {
                    sb.append("&nbsp;&nbsp;<b><font color='#0066CC'>Users:");
                    sb.append(escapeHtml(userCount));
                    sb.append("</font></b>");
                } else {
                    sb.append("&nbsp;&nbsp;<b><font color='#999999'>Users:");
                    sb.append(escapeHtml(userCount));
                    sb.append("&nbsp;(disabled)</font></b>");
                }
            }

            return sb.toString();
        }
    }

    private static class JSR223Element {
        JMeterTreeNode samplerNode;
        JMeterTreeNode jsr223Node;
        String threadGroupName;

        JSR223Element(JMeterTreeNode samplerNode, JMeterTreeNode jsr223Node, String threadGroupName) {
            this.samplerNode = samplerNode;
            this.jsr223Node = jsr223Node;
            this.threadGroupName = threadGroupName;
        }
    }

    private static class ExtractorElement {
        JMeterTreeNode samplerNode;
        JMeterTreeNode extractorNode;
        String threadGroupName;
        String extractorType;

        ExtractorElement(JMeterTreeNode samplerNode, JMeterTreeNode extractorNode, String threadGroupName, String extractorType) {
            this.samplerNode = samplerNode;
            this.extractorNode = extractorNode;
            this.threadGroupName = threadGroupName;
            this.extractorType = extractorType;
        }
    }

    private Map<String, List<JSR223Element>> findJSR223Elements(JMeterTreeNode node, boolean isPostProcessor) {
        Map<String, List<JSR223Element>> results = new HashMap<>();
        String targetClassName = isPostProcessor ? "JSR223PostProcessor" : "JSR223PreProcessor";
        findJSR223ElementsRecursive(node, results, targetClassName, null, null);
        return results;
    }

    private void findJSR223ElementsRecursive(JMeterTreeNode node, Map<String, List<JSR223Element>> results,
                                              String targetClassName, String currentThreadGroup, JMeterTreeNode currentSampler) {
        if (node == null) return;

        TestElement te = node.getTestElement();
        if (te != null) {
            String className = te.getClass().getName();

            if (className.contains("ThreadGroup")) {
                currentThreadGroup = te.getName();
            }

            if (te instanceof HTTPSamplerProxy || className.contains("Sampler")) {
                currentSampler = node;
            }

            if (className.contains(targetClassName) || className.contains("BeanShell" + targetClassName.replace("JSR223", ""))) {
                if (currentThreadGroup != null && currentSampler != null) {
                    JSR223Element element = new JSR223Element(currentSampler, node, currentThreadGroup);
                    results.computeIfAbsent(currentThreadGroup, k -> new ArrayList<>()).add(element);
                }
            }
        }

        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            findJSR223ElementsRecursive(child, results, targetClassName, currentThreadGroup, currentSampler);
        }
    }

    private Map<String, List<ExtractorElement>> findExtractorElements(JMeterTreeNode node) {
        Map<String, List<ExtractorElement>> results = new HashMap<>();
        findExtractorElementsRecursive(node, results, null, null);
        return results;
    }

    private void findExtractorElementsRecursive(JMeterTreeNode node, Map<String, List<ExtractorElement>> results,
                                                 String currentThreadGroup, JMeterTreeNode currentSampler) {
        if (node == null) return;

        TestElement te = node.getTestElement();
        if (te != null) {
            String className = te.getClass().getName();

            if (className.contains("ThreadGroup")) {
                currentThreadGroup = te.getName();
            }

            if (te instanceof HTTPSamplerProxy || className.contains("Sampler")) {
                currentSampler = node;
            }

            String extractorType = getExtractorType(className);
            if (extractorType != null && currentThreadGroup != null && currentSampler != null) {
                ExtractorElement element = new ExtractorElement(currentSampler, node, currentThreadGroup, extractorType);
                results.computeIfAbsent(currentThreadGroup, k -> new ArrayList<>()).add(element);
            }
        }

        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            findExtractorElementsRecursive(child, results, currentThreadGroup, currentSampler);
        }
    }

    private String getExtractorType(String className) {
        if (className.contains("JSONPostProcessor") || className.contains("JSONExtractor")) return "JSON Extractor";
        if (className.contains("RegexExtractor")) return "Regex Extractor";
        if (className.contains("BoundaryExtractor")) return "Boundary Extractor";
        if (className.contains("XPath2Extractor")) return "XPath2 Extractor";
        if (className.contains("XPathExtractor")) return "XPath Extractor";
        if (className.contains("HtmlExtractor") || className.contains("CSSSelector")) return "CSS/HTML Extractor";
        if (className.contains("JSR223PostProcessor")) return "JSR223 PostProcessor";
        return null;
    }

    private void showJSR223FindDialog(Map<String, List<JSR223Element>> resultsByThreadGroup, boolean isPostProcessor) {
        String type = isPostProcessor ? "PostProcessors" : "PreProcessors";
        int totalCount = resultsByThreadGroup.values().stream().mapToInt(List::size).sum();

        // Create main container
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Search panel at top
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        JLabel searchLabel = new JLabel("Search: ");
        JTextField searchField = new JTextField();
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Results count label
        JLabel countLabel = new JLabel("Showing " + totalCount + " of " + totalCount + " items");
        countLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        countLabel.setForeground(Color.GRAY);
        searchPanel.add(countLabel, BorderLayout.SOUTH);

        mainPanel.add(searchPanel, BorderLayout.NORTH);

        // Results panel
        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));

        // Build all results
        List<JSR223Element> allElements = new ArrayList<>();
        resultsByThreadGroup.values().forEach(allElements::addAll);

        // Create scroll pane
        JScrollPane scrollPane = new JScrollPane(resultsPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Function to update results display
        Runnable updateResults = () -> {
            String searchText = searchField.getText().trim().toLowerCase();
            resultsPanel.removeAll();

            JLabel header = new JLabel("<html><b>JSR223 " + type + "</b></html>");
            header.setFont(new Font("SansSerif", Font.BOLD, 14));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            resultsPanel.add(header);
            resultsPanel.add(Box.createVerticalStrut(10));

            List<String> sortedThreadGroups = new ArrayList<>(resultsByThreadGroup.keySet());
            sortedThreadGroups.sort(String.CASE_INSENSITIVE_ORDER);

            int matchCount = 0;

            for (String threadGroup : sortedThreadGroups) {
                List<JSR223Element> elements = resultsByThreadGroup.get(threadGroup);
                List<JSR223Element> filteredElements = new ArrayList<>();

                for (JSR223Element element : elements) {
                    String samplerName = element.samplerNode.getName().toLowerCase();
                    String jsr223Name = element.jsr223Node.getName().toLowerCase();
                    if (searchText.isEmpty() || samplerName.contains(searchText) || jsr223Name.contains(searchText)) {
                        filteredElements.add(element);
                        matchCount++;
                    }
                }

                if (!filteredElements.isEmpty()) {
                    JLabel tgLabel = new JLabel("<html><b>Thread Group: " + escapeHtml(threadGroup) + "</b> (" + filteredElements.size() + ")</html>");
                    tgLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                    tgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    resultsPanel.add(tgLabel);
                    resultsPanel.add(Box.createVerticalStrut(5));

                    for (JSR223Element element : filteredElements) {
                        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
                        row.setAlignmentX(Component.LEFT_ALIGNMENT);

                        String samplerName = element.samplerNode.getName();
                        String jsr223Name = element.jsr223Node.getName();

                        JLabel label = new JLabel("<html>&nbsp;&nbsp;• <b>" + escapeHtml(samplerName) + "</b>"
                                + " → <i>" + escapeHtml(jsr223Name) + "</i></html>");
                        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
                        row.add(label);

                        JButton goToBtn = new JButton("Go To ▸");
                        goToBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
                        goToBtn.setMargin(new Insets(2, 8, 2, 8));
                        final JMeterTreeNode targetNode = element.jsr223Node;
                        goToBtn.addActionListener(ev -> navigateToNode(targetNode));
                        row.add(goToBtn);

                        resultsPanel.add(row);
                    }

                    resultsPanel.add(Box.createVerticalStrut(8));
                }
            }

            countLabel.setText("Showing " + matchCount + " of " + totalCount + " items");

            resultsPanel.revalidate();
            resultsPanel.repaint();
        };

        // Search field listener
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateResults.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateResults.run(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateResults.run(); }
        });

        // Initial display
        updateResults.run();

        // Create resizable dialog
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(jmeterTree),
                "Find JSR223 " + type, false);
        dialog.setContentPane(mainPanel);
        dialog.setSize(700, 500);
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(jmeterTree);
        dialog.setVisible(true);
    }

    private void showExtractorsFindDialog(Map<String, List<ExtractorElement>> resultsByThreadGroup) {
        int totalCount = resultsByThreadGroup.values().stream().mapToInt(List::size).sum();

        // Create main container
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Search panel at top
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        JLabel searchLabel = new JLabel("Search: ");
        JTextField searchField = new JTextField();
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Results count label
        JLabel countLabel = new JLabel("Showing " + totalCount + " of " + totalCount + " items");
        countLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        countLabel.setForeground(Color.GRAY);
        searchPanel.add(countLabel, BorderLayout.SOUTH);

        mainPanel.add(searchPanel, BorderLayout.NORTH);

        // Results panel
        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));

        // Build all results
        List<ExtractorElement> allElements = new ArrayList<>();
        resultsByThreadGroup.values().forEach(allElements::addAll);

        // Create scroll pane
        JScrollPane scrollPane = new JScrollPane(resultsPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Function to update results display
        Runnable updateResults = () -> {
            String searchText = searchField.getText().trim().toLowerCase();
            resultsPanel.removeAll();

            JLabel header = new JLabel("<html><b>All Extractors</b></html>");
            header.setFont(new Font("SansSerif", Font.BOLD, 14));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            resultsPanel.add(header);
            resultsPanel.add(Box.createVerticalStrut(10));

            List<String> sortedThreadGroups = new ArrayList<>(resultsByThreadGroup.keySet());
            sortedThreadGroups.sort(String.CASE_INSENSITIVE_ORDER);

            int matchCount = 0;

            for (String threadGroup : sortedThreadGroups) {
                List<ExtractorElement> elements = resultsByThreadGroup.get(threadGroup);
                List<ExtractorElement> filteredElements = new ArrayList<>();

                for (ExtractorElement element : elements) {
                    String samplerName = element.samplerNode.getName().toLowerCase();
                    String extractorName = element.extractorNode.getName().toLowerCase();
                    String extractorType = element.extractorType.toLowerCase();
                    if (searchText.isEmpty() || samplerName.contains(searchText) ||
                        extractorName.contains(searchText) || extractorType.contains(searchText)) {
                        filteredElements.add(element);
                        matchCount++;
                    }
                }

                if (!filteredElements.isEmpty()) {
                    JLabel tgLabel = new JLabel("<html><b>Thread Group: " + escapeHtml(threadGroup) + "</b> (" + filteredElements.size() + ")</html>");
                    tgLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                    tgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    resultsPanel.add(tgLabel);
                    resultsPanel.add(Box.createVerticalStrut(5));

                    for (ExtractorElement element : filteredElements) {
                        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
                        row.setAlignmentX(Component.LEFT_ALIGNMENT);

                        String samplerName = element.samplerNode.getName();
                        String extractorName = element.extractorNode.getName();
                        String extractorType = element.extractorType;

                        JLabel label = new JLabel("<html>&nbsp;&nbsp;• <b>" + escapeHtml(samplerName) + "</b>"
                                + " → <i>" + escapeHtml(extractorName) + "</i>"
                                + " <font color='#666'>(" + extractorType + ")</font></html>");
                        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
                        row.add(label);

                        JButton goToBtn = new JButton("Go To ▸");
                        goToBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
                        goToBtn.setMargin(new Insets(2, 8, 2, 8));
                        final JMeterTreeNode targetNode = element.extractorNode;
                        goToBtn.addActionListener(ev -> navigateToNode(targetNode));
                        row.add(goToBtn);

                        resultsPanel.add(row);
                    }

                    resultsPanel.add(Box.createVerticalStrut(8));
                }
            }

            countLabel.setText("Showing " + matchCount + " of " + totalCount + " items");

            resultsPanel.revalidate();
            resultsPanel.repaint();
        };

        // Search field listener
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateResults.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateResults.run(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateResults.run(); }
        });

        // Initial display
        updateResults.run();

        // Create resizable dialog
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(jmeterTree),
                "Find All Extractors", false);
        dialog.setContentPane(mainPanel);
        dialog.setSize(750, 550);
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(jmeterTree);
        dialog.setVisible(true);
    }

    /**
     * Gets the user count from a ThreadGroup node.
     * Supports standard ThreadGroup and jp@gc plugin thread groups.
     * Resolves variables from User Defined Variables and returns the actual value.
     */
    private String getThreadGroupUserCount(JMeterTreeNode node) {
        if (node == null) return "?";

        TestElement te = node.getTestElement();
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
            String resolved = resolveVariable(numThreads.trim());
            return resolved;
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
            System.err.println("[Plugin] Error reading Ultimate Thread Group: " + e.getMessage());
        }

        // Last resort: try standard property
        String standard = te.getPropertyAsString("ThreadGroup.num_threads");
        if (standard != null && !standard.isEmpty() && !standard.equals("null")) {
            return resolveVariable(standard);
        }

        return "?";
    }

    /**
     * Calculates total user count across all thread groups.
     * Variables are resolved from User Defined Variables, and totals are summed.
     * If variables cannot be resolved, they are shown in the total.
     */
    public String getTotalUserCount() {
        JMeterTreeModel treeModel = getTreeModel();
        if (treeModel == null) return "N/A";

        JMeterTreeNode root = (JMeterTreeNode) treeModel.getRoot();
        if (root == null) return "N/A";

        int[] numericTotal = {0}; // Use array to allow modification in recursive method
        List<String> unresolvedVariables = new ArrayList<>();

        collectUserCounts(root, numericTotal, unresolvedVariables);

        // Build result string
        StringBuilder result = new StringBuilder();

        // Show numeric total
        if (numericTotal[0] > 0 || unresolvedVariables.isEmpty()) {
            result.append(numericTotal[0]);
        }

        // Show unresolved variables (these couldn't be looked up from UDV)
        if (!unresolvedVariables.isEmpty()) {
            if (result.length() > 0) {
                result.append(" + ");
            }
            result.append(String.join(" + ", unresolvedVariables));
        }

        if (result.length() == 0) {
            return "0";
        }

        return result.toString();
    }

    /**
     * Recursively collects user counts from all thread groups.
     * Variables are resolved before counting.
     * Only counts ENABLED thread groups - disabled thread groups are excluded.
     */
    private void collectUserCounts(JMeterTreeNode node, int[] numericTotal, List<String> parameterizedValues) {
        if (node == null) return;

        TestElement te = node.getTestElement();
        if (te != null) {
            String className = te.getClass().getName();
            if (className.contains("ThreadGroup")) {
                // Check if thread group is enabled
                boolean isEnabled = te.isEnabled();

                if (isEnabled) {
                    String userCount = getThreadGroupUserCount(node);
                    if (!"?".equals(userCount)) {
                        // Try to parse as integer (already resolved by getThreadGroupUserCount)
                        try {
                            numericTotal[0] += Integer.parseInt(userCount);
                        } catch (NumberFormatException e) {
                            // Still parameterized (couldn't resolve) - show as unresolved
                            // This happens when variable is not in UDV or is from CSV at runtime
                            if (!parameterizedValues.contains(userCount)) {
                                parameterizedValues.add(userCount);
                            }
                        }
                    }
                }
                // If disabled, skip this thread group entirely from the total count
            }
        }

        // Recurse to children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof JMeterTreeNode) {
                collectUserCounts((JMeterTreeNode) child, numericTotal, parameterizedValues);
            }
        }
    }

    /**
     * Resolves a variable reference to its actual value.
     * If the value contains ${varName}, looks up varName in User Defined Variables.
     * Returns the resolved value, or the original if not found.
     */
    private String resolveVariable(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        // Build variable map from test plan
        Map<String, String> variableMap = buildVariableMap();

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
     * Builds a map of all variables and their values from User Defined Variables and CSV configs.
     */
    private Map<String, String> buildVariableMap() {
        Map<String, String> variableMap = new HashMap<>();

        JMeterTreeModel treeModel = getTreeModel();
        if (treeModel == null) return variableMap;

        JMeterTreeNode root = (JMeterTreeNode) treeModel.getRoot();
        if (root == null) return variableMap;

        collectVariables(root, variableMap);
        return variableMap;
    }

    /**
     * Recursively collects all variables from User Defined Variables and CSV configs.
     */
    private void collectVariables(JMeterTreeNode node, Map<String, String> variableMap) {
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

            // Collect from CSV Data Set Config
            if (className.contains("CSVDataSet")) {
                String varNames = te.getPropertyAsString("variableNames");
                if (varNames != null && !varNames.isEmpty()) {
                    // For CSV, we don't have actual values at design time
                    // So we just mark them as "CSV" or skip them
                    // User will need to use hardcoded values or UDV for resolution
                }
            }
        }

        // Recurse to children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof JMeterTreeNode) {
                collectVariables((JMeterTreeNode) child, variableMap);
            }
        }
    }
}
