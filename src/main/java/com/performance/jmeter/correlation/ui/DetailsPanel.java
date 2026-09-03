package com.performance.jmeter.correlation.ui;

import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.DetectedItem;
import com.performance.jmeter.correlation.model.ElementStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class DetailsPanel extends JPanel {

    private final JLabel elementNameLabel;
    private final JPanel correlationPanel;
    private final JPanel parameterizationPanel;
    private final JPanel warningsPanel;

    public DetailsPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        elementNameLabel = new JLabel("Select a sampler to view details");
        elementNameLabel.setFont(elementNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        elementNameLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(elementNameLabel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        correlationPanel = new JPanel();
        correlationPanel.setLayout(new BoxLayout(correlationPanel, BoxLayout.Y_AXIS));
        correlationPanel.setBorder(new TitledBorder("Correlation"));

        parameterizationPanel = new JPanel();
        parameterizationPanel.setLayout(new BoxLayout(parameterizationPanel, BoxLayout.Y_AXIS));
        parameterizationPanel.setBorder(new TitledBorder("Parameterization"));

        warningsPanel = new JPanel();
        warningsPanel.setLayout(new BoxLayout(warningsPanel, BoxLayout.Y_AXIS));
        warningsPanel.setBorder(new TitledBorder("Warnings"));
        warningsPanel.setVisible(false);

        contentPanel.add(correlationPanel);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(parameterizationPanel);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(warningsPanel);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void showDetails(ElementStatus status) {
        if (status == null) {
            clearDetails();
            return;
        }

        elementNameLabel.setText(status.getElementType() + " - " + status.getElementName());

        populateCorrelationPanel(status);
        populateParameterizationPanel(status);
        populateWarningsPanel(status);

        revalidate();
        repaint();
    }

    private void populateCorrelationPanel(ElementStatus status) {
        correlationPanel.removeAll();

        ConfigurationStatus corrStatus = status.getEffectiveCorrelationStatus();
        JLabel statusLabel = new JLabel(corrStatus.getSymbol() + " " + corrStatus.getLabel());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        correlationPanel.add(statusLabel);
        correlationPanel.add(Box.createVerticalStrut(5));

        if (status.getCorrelationResult().hasItems()) {
            JLabel extractorsLabel = new JLabel("Detected Extractors:");
            extractorsLabel.setAlignmentX(LEFT_ALIGNMENT);
            correlationPanel.add(extractorsLabel);
            correlationPanel.add(Box.createVerticalStrut(3));

            for (DetectedItem item : status.getCorrelationResult().getDetectedItems()) {
                JLabel itemLabel = new JLabel("  " + item.toString());
                itemLabel.setAlignmentX(LEFT_ALIGNMENT);
                correlationPanel.add(itemLabel);
            }
        } else {
            JLabel noItems = new JLabel("  No extractors detected");
            noItems.setForeground(Color.GRAY);
            noItems.setAlignmentX(LEFT_ALIGNMENT);
            correlationPanel.add(noItems);
        }

        if (status.getManualCorrelationOverride() != null) {
            correlationPanel.add(Box.createVerticalStrut(5));
            JLabel overrideLabel = new JLabel("  [Manual Override: " +
                    status.getManualCorrelationOverride().getLabel() + "]");
            overrideLabel.setForeground(new Color(70, 130, 180));
            overrideLabel.setAlignmentX(LEFT_ALIGNMENT);
            correlationPanel.add(overrideLabel);
        }
    }

    private void populateParameterizationPanel(ElementStatus status) {
        parameterizationPanel.removeAll();

        ConfigurationStatus paramStatus = status.getEffectiveParameterizationStatus();
        JLabel statusLabel = new JLabel(paramStatus.getSymbol() + " " + paramStatus.getLabel());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        parameterizationPanel.add(statusLabel);
        parameterizationPanel.add(Box.createVerticalStrut(5));

        if (status.getParameterizationResult().hasItems()) {
            JLabel sourcesLabel = new JLabel("Detected Sources:");
            sourcesLabel.setAlignmentX(LEFT_ALIGNMENT);
            parameterizationPanel.add(sourcesLabel);
            parameterizationPanel.add(Box.createVerticalStrut(3));

            for (DetectedItem item : status.getParameterizationResult().getDetectedItems()) {
                JLabel itemLabel = new JLabel("  " + item.toString());
                itemLabel.setAlignmentX(LEFT_ALIGNMENT);
                parameterizationPanel.add(itemLabel);
            }
        } else {
            JLabel noItems = new JLabel("  No parameterization detected");
            noItems.setForeground(Color.GRAY);
            noItems.setAlignmentX(LEFT_ALIGNMENT);
            parameterizationPanel.add(noItems);
        }

        if (status.getManualParameterizationOverride() != null) {
            parameterizationPanel.add(Box.createVerticalStrut(5));
            JLabel overrideLabel = new JLabel("  [Manual Override: " +
                    status.getManualParameterizationOverride().getLabel() + "]");
            overrideLabel.setForeground(new Color(70, 130, 180));
            overrideLabel.setAlignmentX(LEFT_ALIGNMENT);
            parameterizationPanel.add(overrideLabel);
        }
    }

    private void populateWarningsPanel(ElementStatus status) {
        warningsPanel.removeAll();
        boolean hasWarnings = false;

        for (String warning : status.getCorrelationResult().getWarnings()) {
            JLabel warnLabel = new JLabel("⚠ " + warning);
            warnLabel.setForeground(new Color(204, 120, 0));
            warnLabel.setAlignmentX(LEFT_ALIGNMENT);
            warningsPanel.add(warnLabel);
            hasWarnings = true;
        }

        for (String warning : status.getParameterizationResult().getWarnings()) {
            JLabel warnLabel = new JLabel("⚠ " + warning);
            warnLabel.setForeground(new Color(204, 120, 0));
            warnLabel.setAlignmentX(LEFT_ALIGNMENT);
            warningsPanel.add(warnLabel);
            hasWarnings = true;
        }

        warningsPanel.setVisible(hasWarnings);
    }

    public void clearDetails() {
        elementNameLabel.setText("Select a sampler to view details");
        correlationPanel.removeAll();
        parameterizationPanel.removeAll();
        warningsPanel.removeAll();
        warningsPanel.setVisible(false);
        revalidate();
        repaint();
    }
}
