package com.example.darkmode.designer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Predicate;
import javax.swing.table.JTableHeader;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;



/**
 * Robust, listener-driven dark painter:
 * - Uses categorized simple-name lists (ported from your Python) but avoids brittle $inner FQCNs.
 * - Keeps text white while avoiding unwanted opaque backgrounds ("white boxes") by only forcing opacity
 *   for a small safe allowlist. Most text components remain transparent; their parents get the gray bg.
 * - Repaints on WINDOW_OPENED and on dynamic component adds (ContainerListener + invokeLater).
 * - Special handling for JTextPane console (white text on near-black) and popups.
 */
public final class DarkPainter {

    // ===== Colors (tuned for good contrast with white text) =====
    private static final Color GRAY_BG      = new Color(60, 63, 65);    // main background (dark gray)
    private static final Color GRAY_BG_ALT  = new Color(41, 49, 52);    // “almost black” (for console panes / gutters)
    private static final Color DARK_GRAY    = new Color(96, 96, 96);    // accent panels / headers
    private static final Color WHITE        = Color.WHITE;
    private static final Color BLACK        = Color.BLACK;
    private static final Color LIGHT_GRAY   = new Color(180, 180, 180);
    private static final Color LIGHT2_GRAY  = new Color(241, 241, 241);
    private static final Color MENU_ITEM_BG = new Color(
            (int) (DARK_GRAY.getRed() * 0.84),
            (int) (DARK_GRAY.getGreen() * 0.93),
            DARK_GRAY.getBlue()
    );
    // Property Editor table colors
    private static final Color PE_ROW_BG      = new Color(58, 60, 62);   // normal row
    private static final Color PE_ROW_ALT_BG  = new Color(52, 54, 56);   // zebra alt
    private static final Color PE_HOVER_BG    = new Color(70, 73, 76);   // rollover
    private static final Color PE_SELECT_BG   = new Color(75, 110, 175); // selection
    private static final String PE_HOOK       = "dark.pe.hooked";
    private static final String PE_HOVER_ROW  = "dark.pe.hoverRow";

    private static final Color SEL_BLUE     = new Color(72, 169, 230);

    // ===== Public Ancestors we key off (stable FQCNs) =====
    private static final String FQCN_NAV_TREE_PANEL =
            "com.inductiveautomation.ignition.designer.navtree.NavTreePanel";
    private static final String FQCN_BINDING_EDITOR_FRAME =
            "com.inductiveautomation.perspective.designer.workspace.binding.BindingEditorFrame";
    private static final String FQCN_ACTION_COLLECTION_EDITOR =
            "com.inductiveautomation.perspective.designer.workspace.actioneditor.ActionCollectionEditor";
    private static final String FQCN_PROPERTY_EDITOR_FRAME =
            "com.inductiveautomation.perspective.designer.workspace.propertyeditor.PropertyEditorFrame";
    private static final String FQCN_PALETTE_FRAME =
            "com.inductiveautomation.perspective.designer.workspace.palette.PaletteFrame";
    private static final String FQCN_OUTPUT_CONSOLE =
            "com.inductiveautomation.ignition.client.util.gui.OutputConsole";

    // ===== Category model (from your Python lists) =====
    enum Cat { ABW, BW, DGW, LGB, LLGB }
    record Spec(Color bg, Color fg, boolean forceOpaque) {}
    private static final Map<Cat, Spec> DARK = Map.of(
            Cat.ABW, new Spec(GRAY_BG_ALT, WHITE, false),
            Cat.BW,  new Spec(GRAY_BG,    WHITE, false),  // NOTE: using gray instead of pure black to avoid harsh contrast
            Cat.DGW, new Spec(DARK_GRAY,  WHITE, false),
            Cat.LGB, new Spec(LIGHT_GRAY, BLACK, false),
            Cat.LLGB,new Spec(LIGHT2_GRAY,BLACK, false)
    );

    // ===== Lists (simple-name / endsWith match) =====
    private static final Set<String> ABW = setOf(
            "JTableHeader","FPMIApp",
            "ActionCollectionEditor","BindingEditor$PreviewPanel","ConfigHeader","CustomMethodEditor",
            "DataPanelEditor","DesignPanel$LayerParent","DocumentShapeConfigFactory$DocumentShapeConfigPanel",
            "GroupSearchProvider$SelectionPanel","GroupSearchProvider$SelectionPanel$SelectionRadio",
            "IndirectTagBindingConfigurator$RefTable","MessageHandlerEditor","NamedQueryPathSelector",
            "NamedQuerySearchProvider$SelectionPanel","NamedQuerySearchProvider$SelectionPanel$SelectionRadio",
            "OverviewPanel","OverviewPanel$NextScheduledRunLabel","OverviewPanel$RunReportLabel",
            "PagesConfigPanel","ParameterEditorPanel","RecentlyModified","SortableTable",
            "SyntheticaSafeGroupList","TagBindingDesignDelegate$ConfigPanel$DirectTagConfigPanel",
            "TagSearchProvider$SelectionUI","TagSearchProvider$SelectionUI$ProviderCheckBox",
            "ViewSearchProvider$SelectionPanel","ViewSearchProvider$SelectionPanel$SelectionRadio",
            "VisionSearchProvider$SelectionPanel","VisionSearchProvider$SelectionPanel$SelectionRadio"
    );
    private static final Set<String> BW = setOf(
            "HeaderLabel","JDialog","JToolBar","JideTable","JTable","JideToolbarButton","JideToggleButton",
            "JLayeredPane","JSVGCanvas","JTextPane",
            "AboutDialog$1","ActionConfigPanel$DocumentationPanel$2","ActionConfigPanel$DocumentationPanel$3",
            "Box","BorderChooser","Box$Filler","ComponentScopeEditor$BindingCompatibleNodeEditor$BindingControl",
            "CollapsiblePanePalette$2","CollapsiblePanePalette$GroupView$View","CommandBar$a",
            "ComponentPaletteFilterField","ContentContainer","CustomFunctionEditor","DefaultToolBar","ErrorStrip",
            "ExpressionStructureBindingDesignDelegate$ConfigPanel$1$1","FilterablePalette$2","FindReplaceToolbar",
            "FormatTransformDesignDelegate$ConfigPanel","FormatTransformDesignDelegate$ConfigPanel$NumericModePanel",
            "FrameContainer","Gutter","Header1","HierarchialTranslationTable","HolderPanel","IconButton",
            "LayoutConstraintsPanel","LineNumberList","LocalizedLabel","MapTransformDesignDelegate$ConfigPanel",
            "MapTransformDesignDelegate$ConfigPanel$MappingTable","MenuEditor","MenuEditor$MenuNodeEditor",
            "NodeEditor","ParameterTable","PlaceholderPanel","PropertyEditorFrame$1","PropertyPane$a",
            "RecentlyModified$RecentViewsTiles$Tile","ReportingPalette$CategoryView$ShapePaletteItem",
            "ResizableDialog$1","RowNumberMargin","ScriptTab","SecurityTable","SimpleTreeTable$TreeHeader",
            "TabbedPanePalette","TagToolbar","TemplateCanvasCustomizer$ParamsPanel","ThumbnailGallery",
            "TranslationManager$LanguagePanel","VerticalToolbar"
    );
    private static final Set<String> DGW = setOf(
            "ActionQualPanel","ComponentBorderedPanel","DockableBarContainer","JTabbedPane","JToggleButton",
            "JPanel","JScrollBar","JScrollPane$ScrollBar","RecentlyModifiedTilePanel","ResourceBuilderPanel",
            "ScriptEditorBuilder","ScrollablePanel","VerticalToolbar",
            "AbstractSlidingConfigPanel$TitlePanel","ActionConfigPanel$DocumentationPanel","ActionEditPanel",
            "AlarmJournalQueryConfigPanel","AutoHideContainer","BannerPanel$3",
            "BindingEditor$TransformsPanel$TransformWrapperPanel",
            "BorderChooser$BevelBorderOption","BorderChooser$ButtonBorderOption","BorderChooser$EtchedBorderOption",
            "BorderChooser$EtchedTitledBorderOption","BorderChooser$FieldBorderOption","BorderChooser$LineBorderOption",
            "BorderChooser$LineTitledBorderOption","BorderChooser$MatteBorderOption","BorderChooser$PanelTitledBorderOption",
            "BottomButtonPanel","ButtonPanel","CalculationsPanel","ClientGeneralPropsPanel",
            "ClientLaunchPropsPanel","ClientLoginPropsPanel","ClientPollingPropsPanel","ClientUIPropsPanel",
            "ConfigurationExplorer$createBottomButtonPanel$1","ConfigurationView","DesignerGeneralPropsPanel",
            "DesignerWindowEditPropsPanel","ExpandCollapsePanel","ExtensionFunctionEditor","ExtensionFunctionPanel",
            "HttpBindingDesignDelegate$ConfigPanel","JideTable$ab","JRadioButtonChoice","LearnMoreLabel",
            "NamedQueryChoicePanel","NamedQueryPathPanel","PermissionsConfigurator",
            "PerspectiveIdleTimeoutPropsPanel","PermissionsPropsPanel","PerspectivePropsPanel",
            "PieChartConfigFactory$PieChartConfigPanel","PollingOptionPanel","ProjectExporter$1",
            "ProjectExporter$ExportPanel","ProjectGlobalPropsPanel","QueryBindingDesignDelegate$ConfigPanel$1",
            "QuickTableFilterField","RateOptionsPanel","RecentlyModifiedTablePanel","SearchReplaceDialog$7",
            "SearchReplaceDialog$TargetSettings$CategoryBox","ScriptTransformDesignDelegate$ScriptTransformEditor",
            "SimpleBoundTagConfigurator$TagBindingOptionsPanel","SortableTableHeader","StatusBar",
            "TagDropConfigPropsPanel","TagDropConfigPropsPanel$BindingsTablePanel",
            "TagDropConfigPropsPanel$DataTypeTablePanel","TagHistoryBindingConfigPanel",
            "TagHistoryBindingConfigPanel$QueryModePanel","TagHistoryBindingConfigPanel$TimeRangePanel",
            "TagSelectionPanel","TagSelectionPanel$DirectSelectionPanel"
    );
    private static final Set<String> LGB = setOf(
            "JButton","JComboBox","JList","JMenu","JMenuBar","JideTabbedPane","JViewport","JYTextField","WindowWorkspace",
            "AbstractBrowsableGalleryPanel$SearchField","BorderChooser$BorderOption$2","CategoryDetailPanel",
            "CategoryListPanel","CheckBoxTree","CodeEditorPainter","CodeEditor$DisplayTrackingSyntaxTextArea",
            "DBBrowserConfigurator","DBBrowserPanel","DBBrowserPanel$KeyPanel","ExpressionConfigurator",
            "ExtensionFunctionPanel$ParametersPanel","HierarchicalTable",
            "ImageBrowser$ThumbnailPanel","IndirectTagBindingConfigurator","KeyboardEditor$LayoutList",
            "NamedQueryConfigurator","PageAndDockEditor","Ruler$XAxis","Ruler$YAxis","ScheduledParametersPanel",
            "ScheduledParametersPanel$ParameterDetailPanel","SecurityLevelTree","SetToConstantConfigurator",
            "SimpleBoundColorConfigurator","SimpleBoundTagConfigurator","SimpleBoundPropertyConfigurator",
            "SlideOverPane","SQLConfigurator","StandardBannerPanel"
    );
    private static final Set<String> LLGB = setOf(
            "JTextArea","JTextField","JFormattedTextField","JSpinner$NumberEditor","OverlayTextField"
    );

    // Only these we force opaque (to avoid “white boxes” elsewhere)
    private static final Set<String> OPAQUE_ALLOW = setOf(
            "JToolBar","JTable","JTabbedPane","JTextPane", // safe basics
            "ScrollablePanel","VerticalToolbar","IconButton","JideToolbarButton","JideToggleButton"
    );

    // Targets for “always paint even if hidden”
    private static final Set<String> TARGET_WINDOWS = setOf(
            "IgnitionDesigner","ActionEditorFrame","BindingEditorFrame","ComponentScriptEditor",
            "PropertyEditorFrame","PaletteFrame","QueryBrowser","SearchReplaceDialog",
            "TagEditorDialog","TranslationManager","InspectorFrame"
    );

    private static volatile DarkPainter INSTANCE;
    private volatile boolean darkMode;

    private DarkPainter() {}

    public static DarkPainter install() {
        if (INSTANCE != null) return INSTANCE;
        synchronized (DarkPainter.class) {
            if (INSTANCE == null) {
                INSTANCE = new DarkPainter();
                INSTANCE.hookGlobal();
            }
        }
        return INSTANCE;
    }

    public void uninstall() {
        // (Optional) You could remove listeners here if you need a full teardown.
    }

    public void setDarkMode(boolean enabled) {
        this.darkMode = enabled;
        repaintAll();
        // pass 2 after EDT settles (mitigates racey LAF/layout flips)
        SwingUtilities.invokeLater(this::repaintAll);
    }

    // ===== Global hooks =====
    private void hookGlobal() {
        Toolkit.getDefaultToolkit().addAWTEventListener(windowOpenedListener,
                AWTEvent.WINDOW_EVENT_MASK | AWTEvent.WINDOW_STATE_EVENT_MASK);
    }

    private final AWTEventListener windowOpenedListener = e -> {
        if (e instanceof java.awt.event.WindowEvent we &&
                we.getID() == java.awt.event.WindowEvent.WINDOW_OPENED) {
            Window w = we.getWindow();
            if (w != null) paintWindow(w);
        }
    };

    // ===== Painting entry points =====
    private void repaintAll() {
        for (Window w : Window.getWindows()) {
            if (w.isDisplayable() || TARGET_WINDOWS.contains(w.getClass().getSimpleName())) {
                paintWindow(w);
            }
        }
    }

    private void paintWindow(Window w) {
        attachContainerListenerDeep(w);
        paintDeep(w);
        w.repaint();
    }

    private void attachContainerListenerDeep(Component c) {
        if (c instanceof Container ct) {
            if (!hasContainerListener(ct, DynListener.class)) {
                ct.addContainerListener(new DynListener());
            }
            for (Component ch : ct.getComponents()) attachContainerListenerDeep(ch);
        }
    }

    private boolean hasContainerListener(Container c, Class<?> type) {
        for (var l : c.getContainerListeners()) if (type.isInstance(l)) return true;
        return false;
    }

    private final class DynListener extends ContainerAdapter {
        @Override public void componentAdded(ContainerEvent e) {
            paintDeep(e.getChild());
            SwingUtilities.invokeLater(() -> paintDeep(e.getChild()));
        }
    }

    private void removePropertyEditorHoverTrackers(Component c) {
        // Only act inside the Property Editor area
        if (!isUnder(c, FQCN_PROPERTY_EDITOR_FRAME)) return;

        // Common hover tracker class name used in this pane (string match so we don't need the OEM class)
        final String HOVER_TRACKER_TOKEN = "HoverTracker$ComponentTracker";

        // Strip from the component itself
        if (c instanceof JComponent jc) {
            for (var ml : jc.getMouseListeners()) {
                if (ml != null && ml.getClass().getName().contains(HOVER_TRACKER_TOKEN)) {
                    jc.removeMouseListener(ml);
                }
            }
            for (var mml : jc.getMouseMotionListeners()) {
                if (mml != null && mml.getClass().getName().contains("Hover")) { // be a bit broader for motion
                    jc.removeMouseMotionListener(mml);
                }
            }
        }

        // Also strip from the immediate parents that receive the same trackers
        Container p = c.getParent();
        for (int i = 0; i < 3 && p != null; i++, p = p.getParent()) {
            if (p instanceof JComponent pj) {
                for (var ml : pj.getMouseListeners()) {
                    if (ml != null && ml.getClass().getName().contains(HOVER_TRACKER_TOKEN)) {
                        pj.removeMouseListener(ml);
                    }
                }
                for (var mml : pj.getMouseMotionListeners()) {
                    if (mml != null && mml.getClass().getName().contains("Hover")) {
                        pj.removeMouseMotionListener(mml);
                    }
                }
            }
        }
    }


    // ===== Core painter =====
    private void paintDeep(Component c) {
        if (c == null) return;

        // 1) Special roles first
        if (isConsolePane(c)) {
            if (darkMode) styleConsole((JTextPane) c);
            else revertConsole((JTextPane) c);
        }

        // === TARGETED AREAS YOU ASKED FOR (run before category rules) ===
        if (darkMode) {
            // Project Browser (left tree + its containers)
            if (isProjectBrowserTree(c)) {
                applyAreaDark(c, DARK_GRAY);          // JTree itself
            } else if (isProjectBrowserContainer(c) && isUnder(c, FQCN_NAV_TREE_PANEL)) {
                applyAreaDark(c, GRAY_BG);            // JScrollPane/JViewport/JPanel around the tree
            }

            // Tag Browser (bottom-left)
            if (isTagBrowserArea(c)) {
                applyAreaDark(c, GRAY_BG);
            }

            // Perspective Property Editor (right), includes Session props
            if (isPropertyEditorArea(c)) {
                applyAreaDark(c, DARK_GRAY);
            }
        }
        // Ensure Property Editor tables get the dark renderer/editors
        if (darkMode && isPropertyEditorArea(c) && c instanceof JTable) {
            ensurePropertyEditorTableHooks((JTable) c);
        }

        // Brutal-but-precise fix for white tiles/editors in the Property Editor
        if (darkMode && isUnder(c, FQCN_PROPERTY_EDITOR_FRAME)) {
            fixPropertyEditorWhites(c);
        }

        // Neutralize OEM light hover overlay inside the Property Editor
        if (darkMode && isUnder(c, FQCN_PROPERTY_EDITOR_FRAME)) {
            removePropertyEditorHoverTrackers(c);
        }

        // 2) Category pass (first match wins)
        if (darkMode) {
            boolean matched =
                    applyIf(c, ABW, Cat.ABW) ||
                            applyIf(c, BW,  Cat.BW)  ||
                            applyIf(c, DGW, Cat.DGW) ||
                            applyIf(c, LGB, Cat.LGB) ||
                            applyIf(c, LLGB,Cat.LLGB) ||
                            // Context/role (replaces $Inner hard refs):
                            applyPred(c, this::isNavTreeFilter, Cat.ABW) ||
                            applyPred(c, this::isPropertyEditorSearch, Cat.ABW) ||
                            applyPred(c, this::isPaletteFilter, Cat.ABW) ||
                            applyPred(c, this::isBindingEditorButtons, Cat.DGW) ||
                            applyPred(c, comp -> isUnder(comp, FQCN_ACTION_COLLECTION_EDITOR) && comp instanceof JPanel, Cat.DGW);

            // Fonts white + no white boxes: adjust per type
            if (matched) shapeForText(c);

            // Popups and menus
            if (c instanceof JPopupMenu pm) {
                stylePopup(pm);
            } else if (c instanceof JComponent jc && jc.getComponentPopupMenu() != null) {
                stylePopup(jc.getComponentPopupMenu());
            }

        } else {
            revertLight(c);
        }

        // 3) Recurse
        if (c instanceof Container ct) {
            for (Component child : ct.getComponents()) paintDeep(child);
        }

        // 4) Titled border contrast
        if (c instanceof JComponent jc && jc.getBorder() instanceof javax.swing.border.TitledBorder tb) {
            tb.setTitleColor(WHITE);
        }

        c.repaint();
    }


    // ===== Category helpers =====
    private boolean applyIf(Component c, Set<String> names, Cat cat) {
        if (matches(c, names)) {
            applySpec(c, DARK.get(cat));
            return true;
        }
        return false;
    }

    private boolean applyPred(Component c, Predicate<Component> p, Cat cat) {
        if (p.test(c)) {
            applySpec(c, DARK.get(cat));
            return true;
        }
        return false;
    }

    private void applySpec(Component c, Spec s) {
        // Only change backgrounds for containers and known-safe components.
        if (isContainerish(c) || shouldForceOpaque(c)) {
            c.setBackground(s.bg());
        }
        // Always set foreground for text-bearing components:
        if (isTextBearing(c)) {
            c.setForeground(s.fg());
        }
        if (c instanceof JComponent jc && shouldForceOpaque(c)) {
            jc.setOpaque(true);
        }
    }
    private void applyAreaDark(Component c, Color bg) {
        // container-ish get bg; text stays white; avoid forcing opaque unless needed
        if (isContainerish(c) || shouldForceOpaque(c)) {
            c.setBackground(bg);
        }
        if (isTextBearing(c)) {
            c.setForeground(WHITE);
            if (c instanceof JTextComponent tc) {
                if (!shouldForceOpaque(c)) tc.setOpaque(false);
                tc.setCaretColor(WHITE);
                tc.setSelectionColor(new Color(96, 125, 139));
                tc.setSelectedTextColor(WHITE);
            }
        }
        // Tables: full dark rendering
        if (c instanceof JTable t) styleTableDark(t);
        // Trees: ensure selection/renderer contrast
        if (c instanceof JTree tree) styleTreeDark(tree, bg);
    }

    private void styleTreeDark(JTree tree, Color bg) {
        tree.setBackground(bg);
        tree.setForeground(WHITE);
        tree.setOpaque(true);

        // Ensure selection colors via the renderer
        if (tree.getCellRenderer() instanceof DefaultTreeCellRenderer r) {
            r.setBackgroundNonSelectionColor(bg);
            r.setTextNonSelectionColor(WHITE);
            r.setBackgroundSelectionColor(new Color(75, 110, 175));
            r.setTextSelectionColor(WHITE);
        } else {
            // Wrap any custom renderer so we can enforce colors
            tree.setCellRenderer(new DefaultTreeCellRenderer() {
                @Override
                public Component getTreeCellRendererComponent(
                        JTree t, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                    Component c = super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
                    setBackgroundNonSelectionColor(bg);
                    setTextNonSelectionColor(WHITE);
                    setBackgroundSelectionColor(new Color(75, 110, 175));
                    setTextSelectionColor(WHITE);
                    return c;
                }
            });
        }
    }



    private void revertLight(Component c) {
        // Return to light-ish defaults without overpainting
        if (isContainerish(c) || shouldForceOpaque(c)) c.setBackground(Color.WHITE);
        if (isTextBearing(c)) c.setForeground(Color.BLACK);
        if (c instanceof JComponent jc && shouldForceOpaque(c)) jc.setOpaque(false);
        if (c instanceof JTable t) revertTableLight(t);
        if (c instanceof JPopupMenu pm) revertPopupLight(pm);
    }

    private static boolean isNearWhite(Color c) {
        if (c == null) return false;
        // perceptual brightness (0..255). Treat > 235 as “white-ish”.
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
        int lum = (int)(0.2126*r + 0.7152*g + 0.0722*b);
        return lum >= 235;
    }

    private static final String PE_FIXED = "dark.pe.fixed";

    private void fixPropertyEditorWhites(Component c) {
        if (!(c instanceof JComponent jc)) return;
        if (Boolean.TRUE.equals(jc.getClientProperty(PE_FIXED))) return;

        // Containers around the grid (scrollpane/viewport/panels)
        if (c instanceof JScrollPane || c instanceof JViewport || c instanceof JPanel) {
            if (isNearWhite(jc.getBackground())) {
                jc.setBackground(DARK_GRAY);
                jc.setOpaque(true);
            }
            jc.putClientProperty(PE_FIXED, Boolean.TRUE);
            return;
        }

        // Labels should be white text, transparent bg (no tiles)
        if (c instanceof JLabel lbl) {
            lbl.setForeground(WHITE);
            lbl.setOpaque(false);
            jc.putClientProperty(PE_FIXED, Boolean.TRUE);
            return;
        }

        // Editors: text inputs / combos / spinners → solid dark, readable caret
        if (c instanceof JTextComponent tc) {
            tc.setForeground(WHITE);
            if (isNearWhite(tc.getBackground()) || !tc.isOpaque()) {
                tc.setBackground(PE_ROW_BG);
            }
            tc.setOpaque(true);
            tc.setCaretColor(WHITE);
            tc.setSelectionColor(PE_SELECT_BG);
            tc.setSelectedTextColor(WHITE);
            attachFocusTint(tc); // subtle focus tint, no sticky hover
            jc.putClientProperty(PE_FIXED, Boolean.TRUE);
            return;
        }

        if (c instanceof JComboBox<?> cb) {
            ((JComponent) cb).setOpaque(true);
            cb.setForeground(WHITE);
            if (isNearWhite(cb.getBackground())) {
                cb.setBackground(PE_ROW_BG);
            }
            attachFocusTint((JComponent) cb);
            jc.putClientProperty(PE_FIXED, Boolean.TRUE);
            return;
        }

        if (c instanceof JSpinner sp) {
            jc.setOpaque(true);
            jc.setForeground(WHITE);
            if (isNearWhite(jc.getBackground())) jc.setBackground(PE_ROW_BG);
            // also darken spinner’s editor
            JComponent ed = sp.getEditor();
            if (ed != null) {
                paintDeep(ed);
                ed.setOpaque(true);
                ed.setForeground(WHITE);
                if (isNearWhite(ed.getBackground())) ed.setBackground(PE_ROW_BG);
            }
            attachFocusTint(jc);
            jc.putClientProperty(PE_FIXED, Boolean.TRUE);
            return;
        }

        // Checkboxes (booleans not in table renderers)
        if (c instanceof JCheckBox cbx) {
            cbx.setOpaque(true);
            cbx.setForeground(WHITE);
            if (isNearWhite(cbx.getBackground())) cbx.setBackground(PE_ROW_BG);
            jc.putClientProperty(PE_FIXED, Boolean.TRUE);
            return;
        }

        // Generic catch: any other opaque white-ish widget under Property Editor → darken
        if (jc.isOpaque() && isNearWhite(jc.getBackground())) {
            jc.setBackground(PE_ROW_BG);
            jc.putClientProperty(PE_FIXED, Boolean.TRUE);
        }
    }

    private static final String FOCUS_TINT = "dark.focus.tinted";
    private void attachFocusTint(JComponent jc) {
        if (Boolean.TRUE.equals(jc.getClientProperty(FOCUS_TINT))) return;
        jc.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                jc.setBackground(PE_HOVER_BG);
                jc.repaint();
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                jc.setBackground(PE_ROW_BG);
                jc.repaint();
            }
        });
        jc.putClientProperty(FOCUS_TINT, Boolean.TRUE);
    }


    // ===== Text + background shaping (avoid “white boxes”) =====
    private void shapeForText(Component c) {
        // For text components, prefer transparent backgrounds with white fg,
        // unless they’re in our safe opaque allow list.
        if (c instanceof JTextComponent tc) {
            tc.setForeground(WHITE);
            if (!shouldForceOpaque(c)) {
                // keep transparent; parent provides gray bg
                tc.setOpaque(false);
            } else {
                tc.setOpaque(true);
                tc.setBackground(GRAY_BG);
            }
            // nicer caret/selection on dark
            tc.setCaretColor(WHITE);
            if (tc instanceof JTextArea || tc instanceof JTextPane || tc instanceof JEditorPane) {
                tc.setSelectionColor(new Color(96, 125, 139));
                tc.setSelectedTextColor(WHITE);
            }
        } else if (c instanceof JLabel lbl) {
            lbl.setForeground(WHITE);
            if (!shouldForceOpaque(c)) lbl.setOpaque(false);
        } else if (c instanceof JTable t) {
            styleTableDark(t);
        } else if (c instanceof JList<?> list) {
            list.setForeground(WHITE);
            list.setSelectionBackground(new Color(75, 110, 175));
            list.setSelectionForeground(WHITE);
            if (!shouldForceOpaque(c)) list.setOpaque(false);
        } else if (c instanceof JComboBox<?> cb) {
            cb.setForeground(WHITE);
            if (!shouldForceOpaque(c)) ((JComponent) cb).setOpaque(false);
        }
    }

    private boolean isTextBearing(Component c) {
        return (c instanceof JLabel) || (c instanceof AbstractButton) || (c instanceof JTextComponent)
                || (c instanceof JTable) || (c instanceof JTree) || (c instanceof JList<?>);
    }

    private boolean isContainerish(Component c) {
        return (c instanceof JPanel) || (c instanceof JToolBar) || (c instanceof JScrollPane)
                || (c instanceof JTabbedPane) || (c instanceof JLayeredPane) || (c instanceof JViewport);
    }

    private boolean shouldForceOpaque(Component c) {
        String sn = c.getClass().getSimpleName();
        return OPAQUE_ALLOW.contains(sn);
    }

    // ===== Special roles and contexts =====
    private boolean isConsolePane(Component c) {
        return (c instanceof JTextPane) && isUnder(c, FQCN_OUTPUT_CONSOLE);
    }

    private void styleConsole(JTextPane pane) {
        pane.setBackground(GRAY_BG_ALT);
        pane.setForeground(WHITE);
        pane.setOpaque(true);

        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, WHITE);
        doc.setCharacterAttributes(0, doc.getLength(), set, false);

        // Keep text white on future inserts
        if (!Boolean.TRUE.equals(pane.getClientProperty("dark.consoleWrapped"))) {
            doc.addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        int len = doc.getLength();
                        if (len > 0) doc.setCharacterAttributes(0, len, set, false);
                    });
                }
                @Override public void removeUpdate(DocumentEvent e) {}
                @Override public void changedUpdate(DocumentEvent e) {}
            });
            pane.putClientProperty("dark.consoleWrapped", Boolean.TRUE);
        }
    }

    private void revertConsole(JTextPane pane) {
        pane.setBackground(Color.WHITE);
        pane.setForeground(Color.BLACK);
        pane.setOpaque(false);
    }

    private boolean isNavTreeFilter(Component c) {
        return (c instanceof JTextField) && isUnder(c, FQCN_NAV_TREE_PANEL);
    }

    private boolean isPropertyEditorSearch(Component c) {
        return (c instanceof JTextField) && isUnder(c, FQCN_PROPERTY_EDITOR_FRAME);
    }

    private boolean isPaletteFilter(Component c) {
        return (c instanceof JTextField) && isUnder(c, FQCN_PALETTE_FRAME);
    }
    // Project Browser (left)
    private boolean isProjectBrowserTree(Component c) {
        return (c instanceof JTree) && isUnder(c, FQCN_NAV_TREE_PANEL);
    }
    private boolean isProjectBrowserContainer(Component c) {
        // darken the container surfaces around the tree and its scroll viewport
        return (c instanceof JScrollPane) || (c instanceof JViewport) || (c instanceof JPanel);
    }

    // Tag Browser (bottom-left) – safe heuristics
    private boolean isTagBrowserArea(Component c) {
        // Many Tag Browser classes include "tags.browser" or simple names with "TagBrowser"
        String fq = c.getClass().getName();
        String sn = c.getClass().getSimpleName();
        boolean looksLikeTagBrowser =
                fq.contains(".tags.") && fq.toLowerCase().contains("browser")
                        || sn.toLowerCase().contains("tagbrowser");
        if (!looksLikeTagBrowser) return false;

        // We only act on common types so we don't overpaint
        return (c instanceof JTree) || (c instanceof JTable)
                || (c instanceof JScrollPane) || (c instanceof JViewport)
                || (c instanceof JPanel) || (c instanceof JLabel)
                || (c instanceof JTextComponent);
    }

    // Perspective Property Editor (right pane) – includes Session props
    private boolean isPropertyEditorArea(Component c) {
        // Anything living under the PropertyEditorFrame (container, grids, fields)
        if (!isUnder(c, FQCN_PROPERTY_EDITOR_FRAME)) return false;
        return (c instanceof JTable) || (c instanceof JTree)
                || (c instanceof JScrollPane) || (c instanceof JViewport)
                || (c instanceof JPanel) || (c instanceof JLabel)
                || (c instanceof JTextComponent) || (c instanceof JComboBox);
    }

    private boolean isBindingEditorButtons(Component c) {
        if (!(c instanceof JPanel p)) return false;
        if (!isUnder(c, FQCN_BINDING_EDITOR_FRAME)) return false;
        long buttons = Arrays.stream(p.getComponents()).filter(b -> b instanceof JButton).count();
        return buttons >= 2;
    }

    private boolean isUnder(Component c, String ancestorFqcn) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p.getClass().getName().equals(ancestorFqcn)) return true;
        }
        return false;
    }

    // ===== Tables =====
    private void styleTableDark(JTable t) {
        t.setForeground(WHITE);
        t.setBackground(GRAY_BG);
        t.setSelectionBackground(new Color(75, 110, 175));
        t.setSelectionForeground(WHITE);
        t.setGridColor(new Color(100, 100, 100));
        t.setOpaque(true);

        JTableHeader hdr = t.getTableHeader();
        if (hdr != null) {
            hdr.setForeground(WHITE);
            hdr.setBackground(DARK_GRAY);
            hdr.setOpaque(true);
        }

        // Wrap default renderers so they don’t restore light fg/bg
        for (int i = 0; i < t.getColumnModel().getColumnCount(); i++) {
            var col = t.getColumnModel().getColumn(i);
            var base = col.getCellRenderer();
            if (!(base instanceof DefaultTableCellRenderer)) {
                base = new DefaultTableCellRenderer();
            }
            col.setCellRenderer(new DarkTableCellRenderer((DefaultTableCellRenderer) base));
        }
    }

    private void revertTableLight(JTable t) {
        t.setForeground(Color.BLACK);
        t.setBackground(Color.WHITE);
        t.setSelectionBackground(new Color(184, 207, 229));
        t.setSelectionForeground(Color.BLACK);
        t.setGridColor(new Color(200, 200, 200));
        t.setOpaque(false);

        JTableHeader hdr = t.getTableHeader();
        if (hdr != null) {
            hdr.setForeground(Color.BLACK);
            hdr.setBackground(Color.WHITE);
            hdr.setOpaque(false);
        }
    }

    private static final class DarkTableCellRenderer extends DefaultTableCellRenderer {
        private final DefaultTableCellRenderer base;
        DarkTableCellRenderer(DefaultTableCellRenderer base) { this.base = base; }
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = base.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (c instanceof JComponent jc) {
                if (isSelected) {
                    jc.setBackground(new Color(75, 110, 175));
                    jc.setForeground(WHITE);
                    jc.setOpaque(true);
                } else {
                    jc.setForeground(WHITE);
                    jc.setOpaque(false); // avoid “boxed” cells; table bg shows through
                }
            }
            return c;
        }
    }

    private static final class PEBooleanRenderer extends JCheckBox implements javax.swing.table.TableCellRenderer {
        PEBooleanRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorderPainted(false);
            setFocusPainted(false);
        }
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            setSelected(value instanceof Boolean && (Boolean) value);

            int modelRow = table.convertRowIndexToModel(row);
            Color bg = (modelRow % 2 == 0) ? PE_ROW_BG : PE_ROW_ALT_BG;
            Object hoverObj = table.getClientProperty(PE_HOVER_ROW);
            int hover = (hoverObj instanceof Integer) ? (Integer) hoverObj : -1;
            if (row == hover && !isSelected) bg = PE_HOVER_BG;

            if (isSelected) {
                setBackground(PE_SELECT_BG);
                setForeground(WHITE);
            } else {
                setBackground(bg);
                setForeground(WHITE);
            }
            setOpaque(true);
            return this;
        }
    }


    private static final class PropertyEditorTableRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            Color bg = (modelRow % 2 == 0) ? PE_ROW_BG : PE_ROW_ALT_BG;

            Object hoverObj = table.getClientProperty(PE_HOVER_ROW);
            int hover = (hoverObj instanceof Integer) ? (Integer) hoverObj : -1;
            if (row == hover && !isSelected) {
                bg = PE_HOVER_BG;
            }

            setForeground(WHITE);
            setBackground(isSelected ? PE_SELECT_BG : bg);
            setOpaque(true); // <- paint the cell ourselves; no white tiles
            return this;
        }
    }


    private void ensurePropertyEditorTableHooks(JTable t) {
        if (Boolean.TRUE.equals(t.getClientProperty(PE_HOOK))) return;
        t.putClientProperty(PE_HOOK, Boolean.TRUE);

        // Base table look
        t.setForeground(WHITE);
        t.setBackground(PE_ROW_BG);
        t.setOpaque(true);
        t.setGridColor(new Color(95, 95, 95));
        t.setSelectionBackground(PE_SELECT_BG);
        t.setSelectionForeground(WHITE);

        // Header
        JTableHeader hdr = t.getTableHeader();
        if (hdr != null) {
            hdr.setForeground(WHITE);
            hdr.setBackground(DARK_GRAY);
            hdr.setOpaque(true);
        }

        // Replace renderers for ALL columns (overrides any custom Property-Editor renderers)
        int cc = t.getColumnModel().getColumnCount();
        for (int i = 0; i < cc; i++) {
            TableColumn col = t.getColumnModel().getColumn(i);
            Class<?> colClass = t.getColumnClass(i);

            TableCellRenderer r = Boolean.class.isAssignableFrom(colClass)
                    ? new PEBooleanRenderer()
                    : new PropertyEditorTableRenderer();

            col.setCellRenderer(r);
        }


        // Also set defaults as a safety net (covers any runtime column class changes)
        t.setDefaultRenderer(Object.class,  new PropertyEditorTableRenderer());
        t.setDefaultRenderer(String.class,  new PropertyEditorTableRenderer());
        t.setDefaultRenderer(Boolean.class, new PEBooleanRenderer());

        // Hover tracking (repaint full row to avoid sticky highlight)
        t.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            int last = -1;
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                int row = t.rowAtPoint(e.getPoint());
                if (row != last) {
                    t.putClientProperty(PE_HOVER_ROW, row);
                    t.repaint(); // repaint whole table to avoid any stale tiles
                    last = row;
                }
            }
        });
        t.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                Object prev = t.getClientProperty(PE_HOVER_ROW);
                if (prev instanceof Integer p && p >= 0) {
                    t.putClientProperty(PE_HOVER_ROW, -1);
                    t.repaint(); // clear hover everywhere
                }
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                // also clear hover as soon as you start interacting
                t.putClientProperty(PE_HOVER_ROW, -1);
                t.repaint();
            }
        });


        // Darken editor components while editing
        t.addPropertyChangeListener(evt -> {
            if ("tableCellEditor".equals(evt.getPropertyName()) || "editing".equals(evt.getPropertyName())) {
                Component ed = t.getEditorComponent();
                if (ed != null) {
                    paintDeep(ed); // reuse painter for nested bits
                    if (ed instanceof JTextComponent tc) {
                        tc.setForeground(WHITE);
                        tc.setBackground(PE_HOVER_BG);
                        tc.setCaretColor(WHITE);
                        tc.setSelectionColor(PE_SELECT_BG);
                        tc.setSelectedTextColor(WHITE);
                        tc.setOpaque(true);
                    } else if (ed instanceof JComboBox<?> cb) {
                        ((JComponent) cb).setOpaque(true);
                        cb.setForeground(WHITE);
                        cb.setBackground(PE_HOVER_BG);
                    } else if (ed instanceof JComponent jc) {
                        jc.setOpaque(true);
                        jc.setForeground(WHITE);
                        jc.setBackground(PE_HOVER_BG);
                    }
                }
            }
        });
    }




    // ===== Popups =====
    private void stylePopup(JPopupMenu pm) {
        if (pm == null) return;
        pm.setBackground(GRAY_BG);
        pm.setForeground(WHITE);
        for (MenuElement me : pm.getSubElements()) styleMenuElement(me);
    }

    private void styleMenuElement(MenuElement me) {
        Component c = me.getComponent();
        if (c instanceof JComponent jc) {
            jc.setBackground(MENU_ITEM_BG);
            jc.setForeground((jc.isEnabled() && !(jc instanceof JLabel)) ? WHITE : LIGHT_GRAY);
            jc.setOpaque(true);
        }
        for (MenuElement sub : me.getSubElements()) styleMenuElement(sub);
    }

    private void revertPopupLight(JPopupMenu pm) {
        pm.setBackground(Color.WHITE);
        pm.setForeground(Color.BLACK);
        for (MenuElement me : pm.getSubElements()) revertMenuElement(me);
    }

    private void revertMenuElement(MenuElement me) {
        Component c = me.getComponent();
        if (c instanceof JComponent jc) {
            jc.setBackground(Color.WHITE);
            jc.setForeground(jc.isEnabled() ? Color.BLACK : Color.GRAY);
            jc.setOpaque(true);
        }
        for (MenuElement sub : me.getSubElements()) revertMenuElement(sub);
    }

    // ===== Match helpers =====
    private static Set<String> setOf(String... s) { return new LinkedHashSet<>(Arrays.asList(s)); }

    private boolean matches(Component c, Set<String> tokens) {
        String simple = c.getClass().getSimpleName();
        String name   = c.getClass().getName();
        for (String t : tokens) {
            if (t.contains("$")) {
                // inner pattern: simple can be last token; or FQCN may endWith token
                String inner = t.substring(t.lastIndexOf('$') + 1);
                if (simple.equals(inner) || name.endsWith(t)) return true;
            } else {
                if (simple.equals(t) || name.endsWith("." + t)) return true;
            }
        }
        return false;
    }

    // ===== Optional: icon brightness swap (disabled vs enabled) =====
    @SuppressWarnings("unused")
    private void flipIcons(AbstractButton b, boolean dark) {
        Icon icon = b.getIcon();
        Icon dis  = b.getDisabledSelectedIcon();
        if (icon == null || dis == null) return;
        double bi = bright(icon, b), bd = bright(dis, b);
        Icon bright = (bi >= bd) ? icon : dis;
        Icon dim    = (bright == icon) ? dis : icon;
        if (dark) {
            b.setIcon(bright);
            b.setDisabledIcon(dim);
            b.setDisabledSelectedIcon(dim);
        } else {
            b.setIcon(dim);
            b.setDisabledIcon(bright);
            b.setDisabledSelectedIcon(bright);
        }
    }

    private double bright(Icon icon, Component ref) {
        int w = Math.max(1, icon.getIconWidth()), h = Math.max(1, icon.getIconHeight());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        icon.paintIcon(ref, g, 0, 0); g.dispose();
        long sum = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int rgb = img.getRGB(x, y);
            int r = (rgb >>> 16) & 0xff, gr = (rgb >>> 8) & 0xff, b = rgb & 0xff;
            sum += (r + gr + b) / 3;
        }
        return sum / (double) (w * h);
    }
}
