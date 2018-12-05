package dulinglai.android.ate.resources.resources;

import dulinglai.android.ate.data.values.ResourceValueProvider;
import dulinglai.android.ate.resources.axml.AXmlAttribute;
import dulinglai.android.ate.resources.axml.AXmlHandler;
import dulinglai.android.ate.resources.axml.AXmlNode;
import dulinglai.android.ate.resources.axml.parsers.AXML20Parser;
import dulinglai.android.ate.resources.resources.controls.AndroidLayoutControl;
import dulinglai.android.ate.resources.resources.controls.LayoutControlFactory;

import org.pmw.tinylog.Logger;
import pxb.android.axml.AxmlVisitor;

import soot.*;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Parser for analyzing the layout XML files inside an android application
 *
 * @author Steven Arzt
 *
 */
public class LayoutFileParser extends AbstractResourceParser {

    private final MultiMap<String, AndroidLayoutControl> userControls = new HashMultiMap<>();
    private final MultiMap<String, String> callbackMethods = new HashMultiMap<>();
    private final MultiMap<String, String> includeDependencies = new HashMultiMap<>();
    private final MultiMap<String, SootClass> fragments = new HashMultiMap<>();

    private final String packageName;

    private SootClass scViewGroup = null;
    private SootClass scView = null;
    private SootClass scWebView = null;

    private LayoutControlFactory controlFactory = new LayoutControlFactory();

    public LayoutFileParser(String packageName) {
        this.packageName = packageName;
    }

    private boolean isRealClass(SootClass sc) {
        if (sc == null)
            return false;
        return !(sc.isPhantom() && sc.getMethodCount() == 0 && sc.getFieldCount() == 0);
    }

    private SootClass getLayoutClass(String className) {
        // Cut off some junk returned by the parser
        if (className.startsWith(";"))
            className = className.substring(1);

        if (className.contains("(") || className.contains("<") || className.contains("/")) {
            Logger.warn("Invalid class name %s", className);
            return null;
        }

        SootClass sc = Scene.v().forceResolve(className, SootClass.BODIES);
        if ((sc == null || sc.isPhantom()) && !packageName.isEmpty())
            sc = Scene.v().forceResolve(packageName + "." + className, SootClass.BODIES);
        if (!isRealClass(sc))
            sc = Scene.v().forceResolve("android.view." + className, SootClass.BODIES);
        if (!isRealClass(sc))
            sc = Scene.v().forceResolve("android.widget." + className, SootClass.BODIES);
        if (!isRealClass(sc))
            sc = Scene.v().forceResolve("android.webkit." + className, SootClass.BODIES);
        if (!isRealClass(sc)) {
            Logger.warn(String.format("Could not find layout class %s", className));
            return null;
        }
        return sc;
    }

    /**
     * Checks whether the given class is a layout class
     *
     * @param theClass
     *            The class to check
     * @return True if the given class is a layout class, otherwise false
     */
    private boolean isLayoutClass(SootClass theClass) {
        return theClass != null
                && Scene.v().getOrMakeFastHierarchy().canStoreType(theClass.getType(), scViewGroup.getType());
    }

    /**
     * Checks whether the given class is a view class
     *
     * @param theClass
     *            The class tocheck
     * @return True if the given class is a view class, otherwise false
     */
    private boolean isViewClass(SootClass theClass) {
        if (theClass == null)
            return false;

        // To make sure that nothing all wonky is going on here, we
        // check the hierarchy to find the android view class
        if (Scene.v().getOrMakeFastHierarchy().canStoreType(theClass.getType(), scView.getType()))
            return true;
        if (Scene.v().getOrMakeFastHierarchy().canStoreType(theClass.getType(), scWebView.getType()))
            return true;

        Logger.warn(String.format("Layout class %s is not derived from android.view.View", theClass.getName()));
        return false;
    }

    /**
     * Adds a callback method found in an XML file to the result set
     *
     * @param layoutFile
     *            The XML file in which the callback has been found
     * @param callback
     *            The callback found in the given XML file
     */
    private void addCallbackMethod(String layoutFile, String callback) {
        layoutFile = layoutFile.replace("/layout-large/", "/layout/");
        callbackMethods.put(layoutFile, callback);

        // Recursively process any dependencies we might have collected before
        // we have processed the target
        if (includeDependencies.containsKey(layoutFile))
            for (String target : includeDependencies.get(layoutFile))
                addCallbackMethod(target, callback);
    }

    /**
     * Adds a fragment found in an XML file to the result set
     *
     * @param layoutFile
     *            The XML file in which the fragment has been found
     * @param fragment
     *            The fragment found in the given XML file
     */
    private void addFragment(String layoutFile, SootClass fragment) {
        // Do not add null fragments
        if (fragment == null)
            return;

        layoutFile = layoutFile.replace("/layout-large/", "/layout/");
        fragments.put(layoutFile, fragment);

        // Recursively process any dependencies we might have collected before
        // we have processed the target
        if (includeDependencies.containsKey(layoutFile))
            for (String target : includeDependencies.get(layoutFile))
                addFragment(target, fragment);
    }

    /**
     * Parses all layout XML files in the given APK file and loads the IDs of the
     * user controls in it. This method only registers a Soot phase that is run when
     * the Soot packs are next run
     *
     * @param fileName
     *            The APK file in which to look for user controls
     */
    public void parseLayoutFile(final String fileName) {
        Transform transform = new Transform("wjtp.lfp", new SceneTransformer() {
            protected void internalTransform(String phaseName, @SuppressWarnings("rawtypes") Map options) {
                parseLayoutFileDirect(fileName);
            }

        });
        PackManager.v().getPack("wjtp").add(transform);
    }

    /**
     * Parses all layout XML files in the given APK file and loads the IDs of the
     * user controls in it. This method directly executes the analysis without
     * registering any Soot phases.
     *
     * @param fileName
     *            The APK file in which to look for user controls
     */
    public void parseLayoutFileDirect(final String fileName) {
        handleAndroidResourceFiles(fileName, /* classes, */ null, (layoutFileName, fileNameFilter, stream) -> {
            // We only process valid layout XML files
            if (!layoutFileName.startsWith("res/layout"))
                return;
            if (!layoutFileName.endsWith(".xml")) {
                Logger.warn("Skipping file %s in layout folder...", layoutFileName);
                return;
            }

            // Initialize the Soot classes
            scViewGroup = Scene.v().getSootClassUnsafe("android.view.ViewGroup");
            scView = Scene.v().getSootClassUnsafe("android.view.View");
            scWebView = Scene.v().getSootClassUnsafe("android.webkit.WebView");

            // Get the fully-qualified class name
            String entryClass = layoutFileName.substring(0, layoutFileName.lastIndexOf("."));
            if (!packageName.isEmpty())
                entryClass = packageName + "." + entryClass;

            // We are dealing with resource files
            if (fileNameFilter != null) {
                boolean found = false;
                for (String s : fileNameFilter)
                    if (s.equalsIgnoreCase(entryClass)) {
                        found = true;
                        break;
                    }
                if (!found)
                    return;
            }

            try {
                AXmlHandler handler = new AXmlHandler(stream, new AXML20Parser());
                parseLayoutNode(layoutFileName, handler.getDocument().getRootNode());
            } catch (Exception ex) {
                Logger.error("Could not read binary XML file: " + ex.getMessage(), ex);
            }
        });
    }

    /**
     * Parses the layout file with the given root node
     *
     * @param layoutFile
     *            The full path and file name of the file being parsed
     * @param rootNode
     *            The root node from where to start parsing
     */
    private void parseLayoutNode(String layoutFile, AXmlNode rootNode) {
        if (rootNode.getTag() == null || rootNode.getTag().isEmpty()) {
            Logger.warn("Encountered a null or empty node name in file %s, skipping node...", layoutFile);
            return;
        }

        String tname = rootNode.getTag().trim();
        if (tname.equals("dummy")) {
            // dummy root node, ignore it
        }
        // Check for inclusions
        else if (tname.equals("include")) {
            parseIncludeAttributes(layoutFile, rootNode);
        }
        // The "merge" tag merges the next hierarchy level into the current
        // one for flattening hierarchies.
        else if (tname.equals("merge")) {
            // do not consider any attributes of this elements, just
            // continue with the children
        } else if (tname.equals("fragment")) {
            final AXmlAttribute<?> attr = rootNode.getAttribute("name");
            // final AXmlAttribute<?> attrID = rootNode.getAttribute("id");
            if (attr == null)
                Logger.warn("Fragment without class name or id detected");
            else {
                addFragment(layoutFile, getLayoutClass(attr.getValue().toString()));
                if (attr.getType() != AxmlVisitor.TYPE_STRING)
                    Logger.warn("Invalid target resource " + attr.getValue() + "for fragment class value");
                getLayoutClass(attr.getValue().toString());
            }
        } else {
            final SootClass childClass = getLayoutClass(tname);
            if (childClass != null && (isLayoutClass(childClass) || isViewClass(childClass)))
                parseLayoutAttributes(layoutFile, childClass, rootNode);
        }

        // Parse the child nodes
        for (AXmlNode childNode : rootNode.getChildren())
            parseLayoutNode(layoutFile, childNode);
    }

    /**
     * Parses the attributes required for a layout file inclusion
     *
     * @param layoutFile
     *            The full path and file name of the file being parsed
     * @param rootNode
     *            The AXml node containing the attributes
     */
    private void parseIncludeAttributes(String layoutFile, AXmlNode rootNode) {
        for (Entry<String, AXmlAttribute<?>> entry : rootNode.getAttributes().entrySet()) {
            String attrName = entry.getKey().trim();
            AXmlAttribute<?> attr = entry.getValue();

            if (attrName.equals("layout")) {
                if ((attr.getType() == AxmlVisitor.TYPE_REFERENCE || attr.getType() == AxmlVisitor.TYPE_INT_HEX)
                        && attr.getValue() instanceof Integer) {
                    // We need to get the target XML file from the binary manifest
                    String targetRes = ResourceValueProvider.v().getLayoutResourceString((Integer) attr.getValue());
                    if (targetRes == null) {
                        Logger.warn("Target resource " + attr.getValue() + " for layout include not found");
                        return;
                    }

                    // If we have already processed the target file, we can
                    // simply copy the callbacks we have found there
                    if (callbackMethods.containsKey(targetRes))
                        for (String callback : callbackMethods.get(targetRes))
                            addCallbackMethod(layoutFile, callback);
                    else {
                        // We need to record a dependency to resolve later
                        includeDependencies.put(targetRes, layoutFile);
                    }
                }
            }
        }
    }

    /**
     * Parses the layout attributes in the given AXml node
     *
     * @param layoutFile
     *            The full path and file name of the file being parsed
     * @param layoutClass
     *            The class for the attributes are parsed
     * @param rootNode
     *            The AXml node containing the attributes
     */
    private void parseLayoutAttributes(String layoutFile, SootClass layoutClass, AXmlNode rootNode) {
        // Create the new user control
        AndroidLayoutControl lc = controlFactory.createLayoutControl(layoutFile, layoutClass, rootNode);

        // Check for a button click listener
        if (lc.getClickListener() != null)
            addCallbackMethod(layoutFile, lc.getClickListener());

        // Register the user control
        this.userControls.put(layoutFile, lc);
    }

    /**
     * Gets the user controls found in the layout XML file. The result is a mapping
     * from the id to the respective layout control.
     *
     * @return The layout controls found in the XML file.
     */
    public Map<Integer, AndroidLayoutControl> getUserControlsByID() {
        Map<Integer, AndroidLayoutControl> res = new HashMap<>();
        for (AndroidLayoutControl lc : this.userControls.values()) {
            if (lc.getID() != -1)
                res.put(lc.getID(), lc);
        }
        return res;
    }

    /**
     * Gets the user controls found in the layout XML file. The result is a mapping
     * from the file name in which the control was found to the respective layout
     * control.
     *
     * @return The layout controls found in the XML file.
     */
    public MultiMap<String, AndroidLayoutControl> getUserControls() {
        return this.userControls;
    }

    /**
     * Gets the user control by resource id
     * @param resId The resource id of the user control
     * @return The user control
     */
    public AndroidLayoutControl findUserControlById(int resId) {
        for (String key : userControls.keySet()) {
            Set<AndroidLayoutControl> layoutControlSet = userControls.get(key);
            for (AndroidLayoutControl layoutControl : layoutControlSet) {
                if (layoutControl.getID() == resId)
                    return layoutControl;
            }
        }
        Logger.warn("[WARN] Cannot find user control by resource id: {}", resId);
        return null;
    }

    /**
     * Gets the callback methods found in the layout XML file. The result is a
     * mapping from the file name to the set of found callback methods.
     *
     * @return The callback methods found in the XML file.
     */
    public MultiMap<String, String> getCallbackMethods() {
        return this.callbackMethods;
    }

    /**
     * Gets the fragments found in the layout XML file. The result is a mapping from
     * the activity class to the set of found fragments ids.
     *
     * @return The fragments found in the XML file.
     */
    public MultiMap<String, SootClass> getFragments() {
        return this.fragments;
    }

    /**
     * Sets the layout control factory to use for creating new layout controls
     *
     * @param controlFactory
     *            The layout control factory
     */
    public void setControlFactory(LayoutControlFactory controlFactory) {
        this.controlFactory = controlFactory;
    }

}
