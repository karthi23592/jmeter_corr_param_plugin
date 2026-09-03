package com.performance.jmeter.correlation.ui;

import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.ElementStatus;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class StatusTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final Color GREEN = new Color(34, 139, 34);
    private static final Color ORANGE = new Color(204, 120, 0);
    private static final Color GRAY = new Color(128, 128, 128);
    private static final Color BLUE = new Color(70, 130, 180);

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                  boolean expanded, boolean leaf, int row, boolean hasFocus) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setOpaque(false);

        JLabel nameLabel = new JLabel();
        nameLabel.setFont(tree.getFont());

        if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof ElementStatus) {
                ElementStatus status = (ElementStatus) userObject;
                nameLabel.setText(getIconForType(status.getElementType()) + " " + status.getElementName());

                JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                statusPanel.setOpaque(false);

                JLabel corrLabel = createStatusLabel("C:", status.getEffectiveCorrelationStatus());
                JLabel paramLabel = createStatusLabel("P:", status.getEffectiveParameterizationStatus());

                statusPanel.add(corrLabel);
                statusPanel.add(paramLabel);

                panel.add(nameLabel, BorderLayout.WEST);
                panel.add(statusPanel, BorderLayout.EAST);

                String tooltip = buildTooltip(status);
                panel.setToolTipText(tooltip);
            } else {
                nameLabel.setText(value.toString());
                panel.add(nameLabel, BorderLayout.WEST);
            }
        } else {
            nameLabel.setText(value.toString());
            panel.add(nameLabel, BorderLayout.WEST);
        }

        if (sel) {
            panel.setOpaque(true);
            panel.setBackground(getBackgroundSelectionColor());
        }

        return panel;
    }

    private String getIconForType(String elementType) {
        switch (elementType) {
            case "TestPlan":
                return "📋";
            case "ThreadGroup":
                return "👥";
            case "HTTPSampler":
            case "Sampler":
                return "➡";
            case "Controller":
                return "🔀";
            default:
                return "●";
        }
    }

    private JLabel createStatusLabel(String prefix, ConfigurationStatus status) {
        JLabel label = new JLabel(prefix + " " + status.getSymbol());
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setForeground(getColorForStatus(status));
        return label;
    }

    private Color getColorForStatus(ConfigurationStatus status) {
        switch (status) {
            case CONFIGURED:
                return GREEN;
            case PARTIAL:
                return ORANGE;
            case NOT_CONFIGURED:
                return GRAY;
            case NOT_APPLICABLE:
                return new Color(160, 160, 160);
            case UNKNOWN:
                return BLUE;
            default:
                return GRAY;
        }
    }

    private String buildTooltip(ElementStatus status) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>").append(status.getElementName()).append("</b><br/>");
        sb.append("Correlation: ").append(status.getEffectiveCorrelationStatus()).append("<br/>");
        sb.append("Parameterization: ").append(status.getEffectiveParameterizationStatus());

        if (status.getCorrelationResult().hasItems()) {
            sb.append("<br/><br/><b>Extractors:</b><br/>");
            status.getCorrelationResult().getDetectedItems().forEach(item ->
                    sb.append("- ").append(item.toString()).append("<br/>"));
        }

        if (status.getParameterizationResult().hasItems()) {
            sb.append("<br/><b>Parameterization:</b><br/>");
            status.getParameterizationResult().getDetectedItems().forEach(item ->
                    sb.append("- ").append(item.toString()).append("<br/>"));
        }

        sb.append("</html>");
        return sb.toString();
    }
}
