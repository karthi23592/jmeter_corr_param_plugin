package com.performance.jmeter.correlation.ui;

import com.performance.jmeter.correlation.model.ElementStatus;
import com.performance.jmeter.correlation.scanner.StatusAggregator;
import com.performance.jmeter.correlation.scanner.StatusAggregator.ScanSummary;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class SummaryPanel extends JPanel {

    private final JLabel threadGroupCountLabel;
    private final JLabel samplerCountLabel;
    private final JLabel corrConfiguredLabel;
    private final JLabel corrPartialLabel;
    private final JLabel corrNotConfiguredLabel;
    private final JLabel paramConfiguredLabel;
    private final JLabel paramPartialLabel;
    private final JLabel paramNotConfiguredLabel;

    public SummaryPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel overviewPanel = new JPanel(new GridLayout(2, 2, 10, 5));
        overviewPanel.setBorder(new TitledBorder("Overview"));
        overviewPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        threadGroupCountLabel = new JLabel("Thread Groups: 0");
        samplerCountLabel = new JLabel("HTTP Samplers: 0");
        overviewPanel.add(threadGroupCountLabel);
        overviewPanel.add(samplerCountLabel);

        add(overviewPanel);
        add(Box.createVerticalStrut(10));

        JPanel corrPanel = new JPanel(new GridLayout(1, 1, 5, 3));
        corrPanel.setBorder(new TitledBorder("Correlation"));
        corrPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        corrConfiguredLabel = new JLabel("Configured: 0");
        corrPartialLabel = new JLabel("Partial / Unused: 0");
        corrNotConfiguredLabel = new JLabel("Not Configured: 0");

        corrPanel.add(corrConfiguredLabel);

        add(corrPanel);
        add(Box.createVerticalStrut(10));

        JPanel paramPanel = new JPanel(new GridLayout(1, 1, 5, 3));
        paramPanel.setBorder(new TitledBorder("Parameterization"));
        paramPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        paramConfiguredLabel = new JLabel("Configured: 0");
        paramPartialLabel = new JLabel("Partial: 0");
        paramNotConfiguredLabel = new JLabel("Not Configured: 0");

        paramPanel.add(paramConfiguredLabel);

        add(paramPanel);
        add(Box.createVerticalGlue());
    }

    public void updateSummary(ElementStatus root) {
        ScanSummary summary = StatusAggregator.computeSummary(root);

        threadGroupCountLabel.setText("Thread Groups: " + summary.totalThreadGroups);
        samplerCountLabel.setText("HTTP Samplers: " + summary.totalSamplers +
                (summary.notApplicable > 0 ? " (+" + summary.notApplicable + " N/A)" : ""));

        corrConfiguredLabel.setText("Configured: " + summary.correlationConfigured);

        paramConfiguredLabel.setText("Configured: " + summary.parameterizationConfigured);

        revalidate();
        repaint();
    }
}
