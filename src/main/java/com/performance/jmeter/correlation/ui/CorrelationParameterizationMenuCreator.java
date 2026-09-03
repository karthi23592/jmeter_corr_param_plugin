package com.performance.jmeter.correlation.ui;

import org.apache.jmeter.gui.plugin.MenuCreator;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class CorrelationParameterizationMenuCreator implements MenuCreator {

    private static JFrame pluginFrame;
    private static JCheckBoxMenuItem inlineToggle;

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.TOOLS) {
            inlineToggle = new JCheckBoxMenuItem("Show C/P Status in Tree");
            inlineToggle.setSelected(false);
            inlineToggle.addActionListener(CorrelationParameterizationMenuCreator::toggleInlineMode);

            JMenuItem rescanItem = new JMenuItem("Rescan Correlation & Parameterization");
            rescanItem.addActionListener(e -> {
                InlineStatusDecorator decorator = InlineStatusDecorator.getInstance();
                if (decorator.isActive()) {
                    decorator.scan();
                } else {
                    decorator.activate();
                    if (inlineToggle != null) inlineToggle.setSelected(true);
                }
            });

            JMenuItem detailsItem = new JMenuItem("C/P Status - Detailed View");
            detailsItem.addActionListener(CorrelationParameterizationMenuCreator::showPlugin);

            JMenu findMenu = new JMenu("Find Elements");

            JMenuItem findJSR223Post = new JMenuItem("JSR223 PostProcessors");
            findJSR223Post.addActionListener(e -> InlineStatusDecorator.getInstance().showFindJSR223Dialog(true));

            JMenuItem findJSR223Pre = new JMenuItem("JSR223 PreProcessors");
            findJSR223Pre.addActionListener(e -> InlineStatusDecorator.getInstance().showFindJSR223Dialog(false));

            JMenuItem findAllExtractors = new JMenuItem("All Extractors");
            findAllExtractors.addActionListener(e -> InlineStatusDecorator.getInstance().showFindExtractorsDialog());

            findMenu.add(findJSR223Post);
            findMenu.add(findJSR223Pre);
            findMenu.addSeparator();
            findMenu.add(findAllExtractors);

            // Clear Variable Highlights button
            JMenuItem clearHighlightsItem = new JMenuItem("Clear Variable Highlights");
            clearHighlightsItem.setToolTipText("Clear purple highlights from variable usage tracing");
            clearHighlightsItem.addActionListener(e -> {
                InlineStatusDecorator decorator = InlineStatusDecorator.getInstance();
                decorator.clearHighlights();
            });

            return new JMenuItem[]{inlineToggle, rescanItem, detailsItem, findMenu, clearHighlightsItem};
        }
        return new JMenuItem[0];
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    @Override
    public void localeChanged() {
    }

    private static void toggleInlineMode(ActionEvent e) {
        InlineStatusDecorator decorator = InlineStatusDecorator.getInstance();
        if (inlineToggle.isSelected()) {
            decorator.activate();
        } else {
            decorator.deactivate();
        }
    }

    private static void showPlugin(ActionEvent e) {
        if (pluginFrame == null || !pluginFrame.isVisible()) {
            pluginFrame = new JFrame("Correlation & Parameterization Status - Detailed View");
            pluginFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            pluginFrame.setSize(1200, 700);
            pluginFrame.setLocationRelativeTo(null);

            CorrelationParameterizationPanel panel = new CorrelationParameterizationPanel();
            pluginFrame.add(panel);
        }
        pluginFrame.setVisible(true);
        pluginFrame.toFront();
    }
}
