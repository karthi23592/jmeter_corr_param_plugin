package com.performance.jmeter.correlation.ui;

import com.performance.jmeter.correlation.model.*;
import com.performance.jmeter.correlation.scanner.StatusAggregator;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Standalone launcher for demo/testing outside JMeter.
 * Run this main class to see the plugin UI with sample data.
 */
public class StandaloneLauncher {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // fallback to default
            }

            JFrame frame = new JFrame("Correlation & Parameterization Status - Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 700);
            frame.setLocationRelativeTo(null);

            CorrelationParameterizationPanel panel = new CorrelationParameterizationPanel();
            frame.add(panel);

            // Load demo data
            loadDemoData(panel);

            frame.setVisible(true);
        });
    }

    private static void loadDemoData(CorrelationParameterizationPanel panel) {
        ElementStatus root = createDemoTestPlan();
        panel.loadTestPlan(null); // Will use demo data fallback below

        // Directly set demo data via reflection-free approach:
        // We use the tree update mechanism by calling with pre-built status
        SwingUtilities.invokeLater(() -> {
            try {
                java.lang.reflect.Method method = panel.getClass().getDeclaredMethod("updateTree", ElementStatus.class);
                method.setAccessible(true);
                method.invoke(panel, root);

                java.lang.reflect.Field summaryField = panel.getClass().getDeclaredField("summaryPanel");
                summaryField.setAccessible(true);
                SummaryPanel summaryPanel = (SummaryPanel) summaryField.get(panel);
                summaryPanel.updateSummary(root);

                java.lang.reflect.Field scanResultField = panel.getClass().getDeclaredField("currentScanResult");
                scanResultField.setAccessible(true);
                scanResultField.set(panel, root);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static ElementStatus createDemoTestPlan() {
        ElementStatus root = new ElementStatus("Sample Test Plan", "TestPlan", "/");

        // Thread Group 1 - Login Flow
        ElementStatus loginFlow = new ElementStatus("Thread Group - Login Flow", "ThreadGroup", "/Login Flow");

        ElementStatus loginSampler = createSampler("HTTP Request - Login", "/Login Flow/Login",
                ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED,
                Arrays.asList(new DetectedItem("JSON Extractor", "authToken", "Extract Auth Token", true),
                        new DetectedItem("JSON Extractor", "userId", "Extract User ID", true)),
                Arrays.asList(new DetectedItem("CSV Data Set Config", "username", "users.csv", true),
                        new DetectedItem("CSV Data Set Config", "password", "users.csv", true)));

        ElementStatus getProfileSampler = createSampler("HTTP Request - Get Profile", "/Login Flow/Get Profile",
                ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED,
                Arrays.asList(new DetectedItem("JSON Extractor", "accountId", "Extract Account", true)),
                Arrays.asList(new DetectedItem("JMeter Function", "__UUID()", "Request", true)));

        ElementStatus updateProfileSampler = createSampler("HTTP Request - Update Profile", "/Login Flow/Update Profile",
                ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.CONFIGURED,
                Arrays.asList(),
                Arrays.asList(new DetectedItem("CSV Variable", "email", "users.csv", true)));

        ElementStatus logoutSampler = createSampler("HTTP Request - Logout", "/Login Flow/Logout",
                ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED,
                Arrays.asList(new DetectedItem("Regular Expression Extractor", "sessionId", "Extract Session", true)),
                Arrays.asList(new DetectedItem("JMeter Function", "__time()", "Request", true)));

        loginFlow.addChild(loginSampler);
        loginFlow.addChild(getProfileSampler);
        loginFlow.addChild(updateProfileSampler);
        loginFlow.addChild(logoutSampler);
        StatusAggregator.aggregate(loginFlow);

        // Thread Group 2 - Search Flow
        ElementStatus searchFlow = new ElementStatus("Thread Group - Search Flow", "ThreadGroup", "/Search Flow");

        ElementStatus searchSampler = createSampler("HTTP Request - Search", "/Search Flow/Search",
                ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED,
                Arrays.asList(new DetectedItem("JSON Extractor", "searchId", "Extract Search ID", true)),
                Arrays.asList(new DetectedItem("JMeter Function", "__RandomString()", "Search Term", true)));

        ElementStatus searchResultSampler = createSampler("HTTP Request - Search Result", "/Search Flow/Result",
                ConfigurationStatus.CONFIGURED, ConfigurationStatus.CONFIGURED,
                Arrays.asList(new DetectedItem("JSON Extractor", "resultId", "Extract Result", true)),
                Arrays.asList(new DetectedItem("CSV Variable", "category", "search_data.csv", true)));

        ElementStatus viewDetailsSampler = createSampler("HTTP Request - View Details", "/Search Flow/Details",
                ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.CONFIGURED,
                Arrays.asList(),
                Arrays.asList(new DetectedItem("JMeter Function", "__Random()", "Page Number", true)));

        searchFlow.addChild(searchSampler);
        searchFlow.addChild(searchResultSampler);
        searchFlow.addChild(viewDetailsSampler);
        StatusAggregator.aggregate(searchFlow);

        // Thread Group 3 - Order Flow
        ElementStatus orderFlow = new ElementStatus("Thread Group - Order Flow", "ThreadGroup", "/Order Flow");

        ElementStatus addToCartSampler = createSampler("HTTP Request - Add to Cart", "/Order Flow/Add to Cart",
                ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.NOT_CONFIGURED,
                Arrays.asList(),
                Arrays.asList());

        ElementStatus checkoutSampler = createSampler("HTTP Request - Checkout", "/Order Flow/Checkout",
                ConfigurationStatus.NOT_CONFIGURED, ConfigurationStatus.NOT_CONFIGURED,
                Arrays.asList(),
                Arrays.asList());

        ElementStatus paymentSampler = createSampler("HTTP Request - Payment", "/Order Flow/Payment",
                ConfigurationStatus.PARTIAL, ConfigurationStatus.CONFIGURED,
                Arrays.asList(new DetectedItem("JSON Extractor", "transactionId", "Extract Transaction", false)),
                Arrays.asList(new DetectedItem("JMeter Function", "__UUID()", "OrderRef", true)));

        orderFlow.addChild(addToCartSampler);
        orderFlow.addChild(checkoutSampler);
        orderFlow.addChild(paymentSampler);
        StatusAggregator.aggregate(orderFlow);

        root.addChild(loginFlow);
        root.addChild(searchFlow);
        root.addChild(orderFlow);
        StatusAggregator.aggregate(root);

        return root;
    }

    private static ElementStatus createSampler(String name, String path,
                                               ConfigurationStatus corrStatus, ConfigurationStatus paramStatus,
                                               java.util.List<DetectedItem> corrItems,
                                               java.util.List<DetectedItem> paramItems) {
        ElementStatus sampler = new ElementStatus(name, "HTTPSampler", path);

        AnalysisResult corrResult = new AnalysisResult(corrStatus);
        corrItems.forEach(corrResult::addDetectedItem);
        if (corrStatus == ConfigurationStatus.PARTIAL) {
            corrResult.addWarning("Some extracted variables may not be used in subsequent requests");
        }

        AnalysisResult paramResult = new AnalysisResult(paramStatus);
        paramItems.forEach(paramResult::addDetectedItem);

        sampler.setCorrelationResult(corrResult);
        sampler.setParameterizationResult(paramResult);
        return sampler;
    }
}
