package com.performance.jmeter.correlation.ui;

import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.ElementStatus;
import com.performance.jmeter.correlation.scanner.TestPlanScanner;
import com.performance.jmeter.correlation.util.OverridePersistence;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jorphan.collections.HashTree;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;

public class CorrelationParameterizationPanel extends JPanel {

    private final JTree statusTree;
    private final DefaultTreeModel treeModel;
    private final DetailsPanel detailsPanel;
    private final SummaryPanel summaryPanel;
    private JTextField searchField;
    private JComboBox<String> filterCombo;
    private JLabel statusBarLabel;

    private ElementStatus currentScanResult;
    private String currentJmxFilePath;
    private final TestPlanScanner scanner;

    public CorrelationParameterizationPanel() {
        this.scanner = new TestPlanScanner();
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        add(createToolbar(), BorderLayout.NORTH);

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Test Plan (Not Scanned)");
        treeModel = new DefaultTreeModel(rootNode);
        statusTree = new JTree(treeModel);
        statusTree.setCellRenderer(new StatusTreeCellRenderer());
        statusTree.setRowHeight(28);
        statusTree.addTreeSelectionListener(this::onTreeSelectionChanged);
        statusTree.setComponentPopupMenu(createContextMenu());
        ToolTipManager.sharedInstance().registerComponent(statusTree);

        JScrollPane treeScrollPane = new JScrollPane(statusTree);
        treeScrollPane.setPreferredSize(new Dimension(450, 400));

        detailsPanel = new DetailsPanel();
        detailsPanel.setPreferredSize(new Dimension(350, 400));

        summaryPanel = new SummaryPanel();
        summaryPanel.setPreferredSize(new Dimension(250, 400));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, detailsPanel, summaryPanel);
        rightSplit.setDividerLocation(350);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, rightSplit);
        mainSplit.setDividerLocation(450);

        add(mainSplit, BorderLayout.CENTER);

        statusBarLabel = new JLabel("Ready. Click 'Scan Test Plan' to analyze.");
        statusBarLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        add(statusBarLabel, BorderLayout.SOUTH);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));

        JButton scanButton = new JButton("Scan Test Plan");
        scanButton.addActionListener(this::onScanClicked);
        toolbar.add(scanButton);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        toolbar.add(new JLabel("Filter:"));
        filterCombo = new JComboBox<>(new String[]{
                "All",
                "Correlation Issues",
                "Parameterization Issues",
                "Partial",
                "Not Configured"
        });
        filterCombo.addActionListener(this::onFilterChanged);
        toolbar.add(filterCombo);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        toolbar.add(new JLabel("Search:"));
        searchField = new JTextField(15);
        searchField.addActionListener(this::onSearchPerformed);
        toolbar.add(searchField);

        JButton searchBtn = new JButton("Go");
        searchBtn.addActionListener(this::onSearchPerformed);
        toolbar.add(searchBtn);

        toolbar.add(Box.createHorizontalStrut(20));

        JButton exportButton = new JButton("Export Report");
        exportButton.addActionListener(this::onExportClicked);
        toolbar.add(exportButton);

        return toolbar;
    }

    private void onScanClicked(ActionEvent e) {
        statusBarLabel.setText("Scanning test plan...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Detect current JMX file path
        currentJmxFilePath = detectJmxFilePath();

        SwingWorker<ElementStatus, Void> worker = new SwingWorker<>() {
            @Override
            protected ElementStatus doInBackground() {
                JMeterTreeModel jmeterTreeModel = getJMeterTreeModel();
                if (jmeterTreeModel != null) {
                    return scanner.scanFromTreeModel(jmeterTreeModel);
                }
                HashTree testPlanTree = getTestPlanTree();
                if (testPlanTree == null) {
                    return null;
                }
                return scanner.scan(testPlanTree);
            }

            @Override
            protected void done() {
                try {
                    ElementStatus result = get();
                    if (result != null) {
                        currentScanResult = result;

                        // Load previously saved overrides
                        if (currentJmxFilePath != null) {
                            OverridePersistence.load(currentJmxFilePath, currentScanResult);
                        }

                        updateTree(result);
                        summaryPanel.updateSummary(result);
                        statusBarLabel.setText(String.format(
                                "Scan complete. Thread Groups: %d | Samplers: %d | Extractors: %d",
                                scanner.getTotalThreadGroups(),
                                scanner.getTotalSamplers(),
                                scanner.getTotalExtractors()
                        ));
                    } else {
                        statusBarLabel.setText("No test plan found. Open a test plan in JMeter first.");
                    }
                } catch (Exception ex) {
                    statusBarLabel.setText("Scan failed: " + ex.getMessage());
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        worker.execute();
    }

    private String detectJmxFilePath() {
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                String testPlanFile = guiPackage.getTestPlanFile();
                if (testPlanFile != null && !testPlanFile.isEmpty()) {
                    return testPlanFile;
                }
            }
        } catch (Exception ex) {
            // Not running inside JMeter
        }
        return null;
    }

    private JMeterTreeModel getJMeterTreeModel() {
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                return guiPackage.getTreeModel();
            }
        } catch (Exception e) {
            // Not running inside JMeter
        }
        return null;
    }

    private HashTree getTestPlanTree() {
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                JMeterTreeModel treeModel = guiPackage.getTreeModel();
                if (treeModel != null) {
                    return treeModel.getTestPlan();
                }
            }
        } catch (Exception e) {
            // Fallback for testing outside JMeter
        }
        return null;
    }

    private void updateTree(ElementStatus rootStatus) {
        DefaultMutableTreeNode rootNode = buildTreeNode(rootStatus);
        treeModel.setRoot(rootNode);
        expandAllNodes(statusTree, 0, statusTree.getRowCount());
    }

    private DefaultMutableTreeNode buildTreeNode(ElementStatus status) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(status);
        for (ElementStatus child : status.getChildren()) {
            node.add(buildTreeNode(child));
        }
        return node;
    }

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; i++) {
            tree.expandRow(i);
        }
        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }

    private void onTreeSelectionChanged(TreeSelectionEvent e) {
        TreePath path = e.getNewLeadSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObject = node.getUserObject();
            if (userObject instanceof ElementStatus) {
                detailsPanel.showDetails((ElementStatus) userObject);
            }
        }
    }

    private void onFilterChanged(ActionEvent e) {
        if (currentScanResult == null) return;

        String filter = (String) filterCombo.getSelectedItem();
        if ("All".equals(filter)) {
            updateTree(currentScanResult);
            return;
        }

        ElementStatus filtered = filterTree(currentScanResult, filter);
        updateTree(filtered);
    }

    private ElementStatus filterTree(ElementStatus root, String filter) {
        ElementStatus filteredRoot = new ElementStatus(root.getElementName(), root.getElementType(), root.getPath());
        filteredRoot.setCorrelationResult(root.getCorrelationResult());
        filteredRoot.setParameterizationResult(root.getParameterizationResult());

        for (ElementStatus child : root.getChildren()) {
            ElementStatus filteredChild = filterElement(child, filter);
            if (filteredChild != null) {
                filteredRoot.addChild(filteredChild);
            }
        }
        return filteredRoot;
    }

    private ElementStatus filterElement(ElementStatus element, String filter) {
        boolean matches = matchesFilter(element, filter);

        ElementStatus result = new ElementStatus(element.getElementName(), element.getElementType(), element.getPath());
        result.setCorrelationResult(element.getCorrelationResult());
        result.setParameterizationResult(element.getParameterizationResult());

        boolean hasMatchingChildren = false;
        for (ElementStatus child : element.getChildren()) {
            ElementStatus filteredChild = filterElement(child, filter);
            if (filteredChild != null) {
                result.addChild(filteredChild);
                hasMatchingChildren = true;
            }
        }

        if (matches || hasMatchingChildren) {
            return result;
        }
        return null;
    }

    private boolean matchesFilter(ElementStatus element, String filter) {
        switch (filter) {
            case "Correlation Issues":
                return element.getEffectiveCorrelationStatus() == ConfigurationStatus.NOT_CONFIGURED ||
                       element.getEffectiveCorrelationStatus() == ConfigurationStatus.PARTIAL;
            case "Parameterization Issues":
                return element.getEffectiveParameterizationStatus() == ConfigurationStatus.NOT_CONFIGURED ||
                       element.getEffectiveParameterizationStatus() == ConfigurationStatus.PARTIAL;
            case "Partial":
                return element.getEffectiveCorrelationStatus() == ConfigurationStatus.PARTIAL ||
                       element.getEffectiveParameterizationStatus() == ConfigurationStatus.PARTIAL;
            case "Not Configured":
                return element.getEffectiveCorrelationStatus() == ConfigurationStatus.NOT_CONFIGURED ||
                       element.getEffectiveParameterizationStatus() == ConfigurationStatus.NOT_CONFIGURED;
            default:
                return true;
        }
    }

    private void onSearchPerformed(ActionEvent e) {
        String searchText = searchField.getText().trim().toLowerCase();
        if (searchText.isEmpty() || currentScanResult == null) {
            if (currentScanResult != null) updateTree(currentScanResult);
            return;
        }

        ElementStatus searchResult = searchTree(currentScanResult, searchText);
        updateTree(searchResult);
    }

    private ElementStatus searchTree(ElementStatus root, String searchText) {
        ElementStatus result = new ElementStatus(root.getElementName(), root.getElementType(), root.getPath());
        result.setCorrelationResult(root.getCorrelationResult());
        result.setParameterizationResult(root.getParameterizationResult());

        for (ElementStatus child : root.getChildren()) {
            ElementStatus searched = searchElement(child, searchText);
            if (searched != null) {
                result.addChild(searched);
            }
        }
        return result;
    }

    private ElementStatus searchElement(ElementStatus element, String searchText) {
        boolean nameMatches = element.getElementName().toLowerCase().contains(searchText);

        ElementStatus result = new ElementStatus(element.getElementName(), element.getElementType(), element.getPath());
        result.setCorrelationResult(element.getCorrelationResult());
        result.setParameterizationResult(element.getParameterizationResult());

        boolean hasMatchingChildren = false;
        for (ElementStatus child : element.getChildren()) {
            ElementStatus searched = searchElement(child, searchText);
            if (searched != null) {
                result.addChild(searched);
                hasMatchingChildren = true;
            }
        }

        if (nameMatches || hasMatchingChildren) {
            return result;
        }
        return null;
    }

    private void onExportClicked(ActionEvent e) {
        if (currentScanResult == null) {
            JOptionPane.showMessageDialog(this,
                    "No scan results to export. Please scan the test plan first.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Detailed Report");
        fileChooser.setSelectedFile(new java.io.File("correlation_parameterization_detailed_report.txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                EnhancedReportExporter exporter = new EnhancedReportExporter();
                exporter.exportToDetailedReport(fileChooser.getSelectedFile(), currentScanResult);
                statusBarLabel.setText("Report exported: " + fileChooser.getSelectedFile().getAbsolutePath());
                JOptionPane.showMessageDialog(this,
                        "Detailed report exported successfully!\n\nLocation: " +
                        fileChooser.getSelectedFile().getAbsolutePath(),
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void loadTestPlan(HashTree testPlanTree) {
        if (testPlanTree != null) {
            ElementStatus result = scanner.scan(testPlanTree);
            currentScanResult = result;
            updateTree(result);
            summaryPanel.updateSummary(result);
            statusBarLabel.setText(String.format(
                    "Scan complete. Thread Groups: %d | Samplers: %d",
                    scanner.getTotalThreadGroups(),
                    scanner.getTotalSamplers()
            ));
        }
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem markCorrNA = new JMenuItem("Mark Correlation as N/A");
        markCorrNA.addActionListener(e -> markSelectedAs(true, ConfigurationStatus.NOT_APPLICABLE));
        menu.add(markCorrNA);

        JMenuItem markParamNA = new JMenuItem("Mark Parameterization as N/A");
        markParamNA.addActionListener(e -> markSelectedAs(false, ConfigurationStatus.NOT_APPLICABLE));
        menu.add(markParamNA);

        JMenuItem markBothNA = new JMenuItem("Mark Both as N/A");
        markBothNA.addActionListener(e -> {
            markSelectedAs(true, ConfigurationStatus.NOT_APPLICABLE);
            markSelectedAs(false, ConfigurationStatus.NOT_APPLICABLE);
        });
        menu.add(markBothNA);

        menu.addSeparator();

        JMenuItem clearOverride = new JMenuItem("Clear Manual Override");
        clearOverride.addActionListener(e -> {
            ElementStatus selected = getSelectedElementStatus();
            if (selected != null) {
                selected.setManualCorrelationOverride(null);
                selected.setManualParameterizationOverride(null);
                refreshTreeAndSummary();
            }
        });
        menu.add(clearOverride);

        menu.addSeparator();

        JMenuItem markCorrConfigured = new JMenuItem("Mark Correlation as Configured");
        markCorrConfigured.addActionListener(e -> markSelectedAs(true, ConfigurationStatus.CONFIGURED));
        menu.add(markCorrConfigured);

        JMenuItem markParamConfigured = new JMenuItem("Mark Parameterization as Configured");
        markParamConfigured.addActionListener(e -> markSelectedAs(false, ConfigurationStatus.CONFIGURED));
        menu.add(markParamConfigured);

        return menu;
    }

    private void markSelectedAs(boolean isCorrelation, ConfigurationStatus status) {
        ElementStatus selected = getSelectedElementStatus();
        if (selected == null) return;

        if (isCorrelation) {
            selected.setManualCorrelationOverride(status);
        } else {
            selected.setManualParameterizationOverride(status);
        }
        refreshTreeAndSummary();
    }

    private ElementStatus getSelectedElementStatus() {
        TreePath path = statusTree.getSelectionPath();
        if (path == null) return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (userObject instanceof ElementStatus) {
            return (ElementStatus) userObject;
        }
        return null;
    }

    private void refreshTreeAndSummary() {
        statusTree.repaint();
        if (currentScanResult != null) {
            summaryPanel.updateSummary(currentScanResult);
            // Auto-save overrides
            if (currentJmxFilePath != null) {
                OverridePersistence.save(currentJmxFilePath, currentScanResult);
                statusBarLabel.setText("Changes saved to: " +
                        OverridePersistence.getStatusFile(currentJmxFilePath).getName());
            }
        }
        ElementStatus selected = getSelectedElementStatus();
        if (selected != null) {
            detailsPanel.showDetails(selected);
        }
    }
}
