package com.performance.jmeter.correlation.ui;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.apache.jmeter.gui.plugin.MenuCreator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;

public class CorrelationParameterizationMenuCreator implements MenuCreator {

    private static JFrame pluginFrame;
    private static JCheckBoxMenuItem inlineToggle;
    private static boolean toolbarButtonAdded = false;
    private static JLabel totalUsersLabel;

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.TOOLS) {
            // Initialize toolbar button only once
            if (!toolbarButtonAdded) {
                SwingUtilities.invokeLater(this::addToolbarButton);
                toolbarButtonAdded = true;
            }

            inlineToggle = new JCheckBoxMenuItem("Show C/P Status in Tree");
            inlineToggle.setSelected(true); // Default: enabled
            inlineToggle.addActionListener(CorrelationParameterizationMenuCreator::toggleInlineMode);

            // Auto-activate plugin by default when JMeter starts
            SwingUtilities.invokeLater(() -> {
                InlineStatusDecorator decorator = InlineStatusDecorator.getInstance();
                if (!decorator.isActive()) {
                    decorator.activate();
                }
            });

            // REMOVED: Rescan menu item
            // JMenuItem rescanItem = new JMenuItem("Rescan Correlation & Parameterization");
            // rescanItem.addActionListener(e -> {
            //     InlineStatusDecorator decorator = InlineStatusDecorator.getInstance();
            //     if (decorator.isActive()) {
            //         decorator.scan();
            //     } else {
            //         decorator.activate();
            //         if (inlineToggle != null) inlineToggle.setSelected(true);
            //     }
            // });

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

            return new JMenuItem[]{inlineToggle, detailsItem, findMenu, clearHighlightsItem};
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

    /**
     * Adds a "Clear Variable Highlights" button to JMeter's toolbar.
     */
    private void addToolbarButton() {
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage == null) return;

            MainFrame mainFrame = guiPackage.getMainFrame();
            if (mainFrame == null) return;

            // Try to find the toolbar using reflection
            JToolBar toolbar = findToolbar(mainFrame);
            if (toolbar == null) return;

            // Create the clear button
            JButton clearButton = new JButton();
            clearButton.setIcon(createClearIcon());
            clearButton.setToolTipText("Clear Variable Highlights");
            clearButton.setFocusable(false);
            clearButton.setPreferredSize(new Dimension(32, 32));
            clearButton.setMaximumSize(new Dimension(32, 32));
            clearButton.setMinimumSize(new Dimension(32, 32));

            clearButton.addActionListener(e -> {
                InlineStatusDecorator decorator = InlineStatusDecorator.getInstance();
                decorator.clearHighlights();
            });

            // Add clear button to toolbar
            toolbar.add(clearButton);

            // Add some spacing
            toolbar.add(Box.createHorizontalStrut(5));

            // Create the refresh button
            JButton refreshButton = new JButton();
            refreshButton.setIcon(createRefreshIcon());
            refreshButton.setToolTipText("Refresh C/P Status");
            refreshButton.setFocusable(false);
            refreshButton.setPreferredSize(new Dimension(32, 32));
            refreshButton.setMaximumSize(new Dimension(32, 32));
            refreshButton.setMinimumSize(new Dimension(32, 32));

            refreshButton.addActionListener(e -> {
                InlineStatusDecorator decorator = InlineStatusDecorator.getInstance();
                if (decorator.isActive()) {
                    decorator.scan();
                }
            });

            // Add refresh button to toolbar
            toolbar.add(refreshButton);

            // Add some spacing
            toolbar.add(Box.createHorizontalStrut(10));

            // Add total users label
            totalUsersLabel = new JLabel("Total Users: 0");
            totalUsersLabel.setFont(totalUsersLabel.getFont().deriveFont(Font.BOLD, 12f));
            totalUsersLabel.setForeground(new Color(0, 102, 204)); // Blue color
            totalUsersLabel.setToolTipText("Total user count across all thread groups");
            toolbar.add(totalUsersLabel);

            toolbar.revalidate();
            toolbar.repaint();

            System.out.println("[Plugin] Clear, Refresh buttons and Total Users label added to toolbar");

        } catch (Exception ex) {
            System.err.println("[Plugin] Could not add toolbar button: " + ex.getMessage());
        }
    }

    /**
     * Finds the JMeter toolbar component using reflection.
     */
    private JToolBar findToolbar(MainFrame mainFrame) {
        try {
            // Try to access toolbar field directly
            Field toolbarField = MainFrame.class.getDeclaredField("toolbar");
            toolbarField.setAccessible(true);
            Object toolbarObj = toolbarField.get(mainFrame);
            if (toolbarObj instanceof JToolBar) {
                return (JToolBar) toolbarObj;
            }
        } catch (Exception e) {
            // Fallback: search recursively
            return findToolbarRecursive(mainFrame.getContentPane());
        }
        return null;
    }

    /**
     * Recursively searches for a JToolBar component.
     */
    private JToolBar findToolbarRecursive(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JToolBar) {
                return (JToolBar) comp;
            }
            if (comp instanceof Container) {
                JToolBar found = findToolbarRecursive((Container) comp);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Creates a custom purple icon with white X for the clear button.
     */
    private Icon createClearIcon() {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw a purple circle background
                g2.setColor(new Color(147, 112, 219, 200)); // Medium purple with transparency
                g2.fillOval(x + 4, y + 4, 16, 16);

                // Draw white "X" on top
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                // X lines
                g2.drawLine(x + 8, y + 8, x + 16, y + 16);
                g2.drawLine(x + 16, y + 8, x + 8, y + 16);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 24;
            }

            @Override
            public int getIconHeight() {
                return 24;
            }
        };
    }

    /**
     * Creates a custom green icon with circular arrow for the refresh button.
     */
    private Icon createRefreshIcon() {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw a green circle background
                g2.setColor(new Color(34, 139, 34, 200)); // Forest green with transparency
                g2.fillOval(x + 4, y + 4, 16, 16);

                // Draw white circular arrow on top
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                // Draw circular arc (270 degrees)
                g2.drawArc(x + 7, y + 7, 10, 10, 45, 270);

                // Draw arrow head
                int[] arrowX = {x + 17, x + 15, x + 17};
                int[] arrowY = {y + 9, y + 7, y + 7};
                g2.fillPolygon(arrowX, arrowY, 3);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 24;
            }

            @Override
            public int getIconHeight() {
                return 24;
            }
        };
    }

    /**
     * Updates the total users label in the toolbar.
     * Call this method after scanning to refresh the user count display.
     */
    public static void updateTotalUsersLabel(String totalUsers) {
        if (totalUsersLabel != null) {
            SwingUtilities.invokeLater(() -> {
                totalUsersLabel.setText("Total Users: " + totalUsers);
            });
        }
    }
}
