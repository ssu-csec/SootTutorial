package dev.navids.soottutorial.android;

import dev.navids.soottutorial.visual.AndroidCallGraphFilter;
import dev.navids.soottutorial.visual.Visualizer;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.io.File;
import java.util.*;

public class AndroidCallgraph {
    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";	// [Edit] Path of android platform
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = androidDemoPath + File.separator + "/st_demo.apk";			// [Edit] Path of APK
    static String childMethodSignature = "<dev.navids.multicomp1.ClassChild: void childMethod()>";
    static String childBaseMethodSignature = "<dev.navids.multicomp1.ClassChild: void baseMethod()>";
    static String parentMethodSignature = "<dev.navids.multicomp1.ClassParent: void baseMethod()>";
    static String unreachableMethodSignature = "<dev.navids.multicomp1.ClassParent: void unreachableMethod()>";
    static String mainActivityEntryPointSignature = "<dummyMainClass: dev.navids.multicomp1.MainActivity dummyMainMethod_dev_navids_multicomp1_MainActivity(android.content.Intent)>";
    static String mainActivityClassName = "dev.navids.multicomp1.MainActivity";

    public static void main(String[] args){

        if(System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        // Parse arguments
        InfoflowConfiguration.CallgraphAlgorithm cgAlgorithm = InfoflowConfiguration.CallgraphAlgorithm.SPARK;
        if (args.length > 0 && args[0].equals("CHA"))
            cgAlgorithm = InfoflowConfiguration.CallgraphAlgorithm.CHA;
        boolean drawGraph = false;
        if (args.length > 1 && args[1].equals("draw"))
            drawGraph = true;
        // Setup FlowDroid
        final InfoflowAndroidConfiguration config = AndroidUtil.getFlowDroidConfig(apkPath, androidJar, cgAlgorithm);
        SetupApplication app = new SetupApplication(config);
        // Create the Callgraph without executing taint analysis
        app.constructCallgraph();
        CallGraph callGraph = Scene.v().getCallGraph();									// [Hint] Process of generating callgraph
        int classIndex = 0;
        // Print some general information of the generated callgraph. Note that although usually the nodes in callgraph
        // are assumed to be methods, the edges in Soot's callgraph is from Unit to SootMethod.
        AndroidCallGraphFilter androidCallGraphFilter = new AndroidCallGraphFilter(AndroidUtil.getPackageName(apkPath));
        for(SootClass sootClass: androidCallGraphFilter.getValidClasses()){
            System.out.println(String.format("Class %d: %s", ++classIndex, sootClass.getName()));
            for(SootMethod sootMethod : sootClass.getMethods()){
                int incomingEdge = 0;
                for(Iterator<Edge> it = callGraph.edgesInto(sootMethod); it.hasNext();incomingEdge++,it.next());
                int outgoingEdge = 0;
                for(Iterator<Edge> it = callGraph.edgesOutOf(sootMethod); it.hasNext();outgoingEdge++,it.next());
                System.out.println(String.format("\tMethod %s, #IncomeEdges: %d, #OutgoingEdges: %d", sootMethod.getName(), incomingEdge, outgoingEdge));
            }
        }
        System.out.println("-----------");
		/* Never mind
        SootMethod childMethod = Scene.v().getMethod(childMethodSignature);
        SootMethod parentMethod = Scene.v().getMethod(parentMethodSignature);
        SootMethod unreachableMehthod = Scene.v().getMethod(unreachableMethodSignature);
        SootMethod mainActivityEntryMethod = Scene.v().getMethod(mainActivityEntryPointSignature);
        for(SootMethod sootMethod : app.getDummyMainMethod().getDeclaringClass().getMethods()) {
            if (sootMethod.getReturnType().toString().equals(mainActivityClassName)) {
                System.out.println("MainActivity's entrypoint is " + sootMethod.getName()
                        + " and it's equal to mainActivityEntryMethod: " + sootMethod.equals(mainActivityEntryMethod));
            }
        }
        Map<SootMethod, SootMethod> reachableParentMapFromEntryPoint = getAllReachableMethods(app.getDummyMainMethod());
        if(reachableParentMapFromEntryPoint.containsKey(unreachableMehthod))
            System.out.println("unreachableMehthod is reachable, a possible path from the entry point: " + getPossiblePath(reachableParentMapFromEntryPoint, unreachableMehthod));
        else
            System.out.println("unreachableMehthod is not reachable from the entrypoint.");
        Map<SootMethod, SootMethod> reachableParentMapFromMainActivity = getAllReachableMethods(mainActivityEntryMethod);
        if(reachableParentMapFromMainActivity.containsKey(childMethod))
            System.out.println("childMethod is reachable from MainActivity, a possible path: " + getPossiblePath(reachableParentMapFromMainActivity, childMethod));
        else
            System.out.println("childMethod is not reachable from MainActivity.");
        if(reachableParentMapFromMainActivity.containsKey(parentMethod))
            System.out.println("parentMethod is reachable from MainActivity, a possible path: " + getPossiblePath(reachableParentMapFromMainActivity, parentMethod));
        else
            System.out.println("parentMethod is not reachable from MainActivity.");
		*/

        // Draw a subset of call graph
        if (drawGraph) {
            Visualizer.v().addCallGraph(callGraph,
                    androidCallGraphFilter,
                    new Visualizer.AndroidNodeAttributeConfig(true));
            Visualizer.v().draw();
        }
    }

    // A Breadth-First Search algorithm to get all reachable methods from initialMethod in the callgraph
    // The output is a map from reachable methods to their parents
    public static Map<SootMethod, SootMethod> getAllReachableMethods(SootMethod initialMethod){
        CallGraph callgraph = Scene.v().getCallGraph();
        List<SootMethod> queue = new ArrayList<>();
        queue.add(initialMethod);
        Map<SootMethod, SootMethod> parentMap = new HashMap<>();
        parentMap.put(initialMethod, null);
        for(int i=0; i< queue.size(); i++){
            SootMethod method = queue.get(i);
            for (Iterator<Edge> it = callgraph.edgesOutOf(method); it.hasNext(); ) {
                Edge edge = it.next();
                SootMethod childMethod = edge.tgt();
                if(parentMap.containsKey(childMethod))
                    continue;
                parentMap.put(childMethod, method);
                queue.add(childMethod);
            }
        }
        return parentMap;
    }

    public static String getPossiblePath(Map<SootMethod, SootMethod> reachableParentMap, SootMethod it) {
        String possiblePath = null;
        while(it != null){
            String itName = it.getDeclaringClass().getShortName()+"."+it.getName();
            if(possiblePath == null)
                possiblePath = itName;
            else
                possiblePath = itName + " -> " + possiblePath;
            it = reachableParentMap.get(it);
        } return possiblePath;
    }

}
