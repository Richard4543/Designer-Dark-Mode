package com.example.darkmode.designer;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * Inserts a single "Dark Mode" toggle in View menu and wires it to a robust painter.
 * Persists user preference in the Designer (per user)..
 */
public class DesignerDarkModeDesignerHook extends AbstractDesignerModuleHook {

    private static final String PREF_KEY = "darkModeEnabled";
    private volatile boolean darkEnabled;
    private JCheckBoxMenuItem toggle;
    private DarkPainter painter;

    @Override
    public void startup(DesignerContext context, LicenseState activationState) throws Exception {
        // Load last setting
        darkEnabled = Preferences.userNodeForPackage(getClass()).getBoolean(PREF_KEY, false);
        painter = DarkPainter.install(); // idempotent install (won't double-hook)

        SwingUtilities.invokeLater(() -> {
            JFrame main = findDesignerMain();
            if (main != null) {
                JMenu view = findViewMenu(main);
                if (view != null) {
                    ensureToggle(view);
                }
            }
            // Sweep twice to catch late layout churn
            painter.setDarkMode(darkEnabled);
            SwingUtilities.invokeLater(() -> painter.setDarkMode(darkEnabled));
        });
    }

    @Override
    public void shutdown() {
        // You can keep dark mode as user left it; no uninstall needed.
        // If you want to remove listeners, call painter.uninstall();
    }

    private void ensureToggle(JMenu view) {
        final String MARKER = "dark.toggle.marker";
        // Find existing
        for (int i = 0; i < view.getItemCount(); i++) {
            JMenuItem it = view.getItem(i);
            if (it instanceof JCheckBoxMenuItem cb && Boolean.TRUE.equals(cb.getClientProperty(MARKER))) {
                toggle = cb;
                toggle.setSelected(darkEnabled);
                return;
            }
        }
        // Insert once
        toggle = new JCheckBoxMenuItem("Dark Mode", darkEnabled);
        toggle.putClientProperty(MARKER, Boolean.TRUE);
        toggle.addActionListener(e -> {
            boolean on = toggle.isSelected();
            Preferences.userNodeForPackage(getClass()).putBoolean(PREF_KEY, on);
            painter.setDarkMode(on);
        });
        view.insert(toggle, 0);
    }

    private static JFrame findDesignerMain() {
        for (Window w : Window.getWindows()) {
            if (w instanceof JFrame f && w.isDisplayable()) {
                String sn = w.getClass().getSimpleName();
                String fq = w.getClass().getName();
                if ("IgnitionDesigner".equals(sn) || fq.endsWith(".IgnitionDesigner")) {
                    return f;
                }
            }
        }
        return null;
    }

    private static JMenu findViewMenu(JFrame frame) {
        JMenuBar mb = frame.getJMenuBar();
        if (mb == null) return null;
        for (int i = 0; i < mb.getMenuCount(); i++) {
            JMenu m = mb.getMenu(i);
            if (m != null && "View".equals(m.getText())) return m;
        }
        // deep fallback (rare)
        return findViewMenuDeep(mb);
    }

    private static JMenu findViewMenuDeep(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JMenu jm && "View".equals(jm.getText())) return jm;
            if (comp instanceof Container ct) {
                JMenu found = findViewMenuDeep(ct);
                if (found != null) return found;
            }
        }
        return null;
    }
}
