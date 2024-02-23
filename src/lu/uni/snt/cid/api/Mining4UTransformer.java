package lu.uni.snt.cid.api;

import lu.uni.snt.cid.AndroidAPILifeModel;
import lu.uni.snt.cid.Config;
import lu.uni.snt.cid.ccg.AndroidSDKVersionChecker;
import lu.uni.snt.cid.utils.CommonUtils;
import soot.*;
import soot.jimple.Stmt;
import soot.util.Chain;

import java.util.*;

public class Mining4UTransformer extends SceneTransformer {
	public Set<String> accessedAndroidAPIs = new HashSet<String>();
	public Map<String, Set<String>> api2callers = new HashMap<String, Set<String>>();

	private void extract(Body b) {
		String callerMethodSig = b.getMethod().getSignature();

		if (b.toString().contains(Config.FIELD_VERSION_SDK_INT) || b.toString().contains(Config.FIELD_VERSION_SDK)) {
			Config.containsSDKVersionChecker = true;

			if (b.toString().contains(Config.FIELD_VERSION_SDK)) {
				if (Config.DEBUG)
					System.out.println("[DEBUG] SDK Field (deprecated in API level 4) is still used!");
			}
		}

		PatchingChain<Unit> units = b.getUnits();

		for (Iterator<Unit> unitIter = units.snapshotIterator(); unitIter.hasNext(); ) {
			Stmt stmt = (Stmt) unitIter.next();

			if (stmt.containsInvokeExpr()) {
				SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
				String methodSig = sootMethod.getSignature();

				if (AndroidAPILifeModel.getInstance().isAndroidAPI(methodSig)) {
					methodSig = methodSig.replace("$", ".");

					accessedAndroidAPIs.add(methodSig);
					CommonUtils.put(api2callers, methodSig, callerMethodSig);
				} else if (AndroidAPILifeModel.getInstance().isInheritedAndroidAPI(methodSig)) {
					methodSig = AndroidAPILifeModel.getInstance().method2inheritedAPIs.get(methodSig);

					methodSig = methodSig.replace("$", ".");

					accessedAndroidAPIs.add(methodSig);
					CommonUtils.put(api2callers, methodSig, callerMethodSig);
				}
			}
		}
	}

	protected void internalBodyTransform(Body b) {
		extract(b);

		AndroidSDKVersionChecker.scan(b);
	}

	@Override
	protected void internalTransform(String arg0, Map<String, String> arg1) {
		Chain<SootClass> sootClasses = Scene.v().getApplicationClasses();
		for (Iterator<SootClass> iter = sootClasses.snapshotIterator(); iter.hasNext(); ) {
			SootClass sc = iter.next();

			if (sc.getName().startsWith("android.support.")) {
				continue;
			}

			List<SootMethod> methods = sc.getMethods();

			for (int i = 0; i < methods.size(); i++) {
				SootMethod sm = methods.get(i);
				Body body = null;
				try {
					body = sm.retrieveActiveBody();
				} catch (Exception ex) {
					if (Config.DEBUG)
						System.out.println("[DEBUG] No body for method " + sm.getSignature());
				}

				if (null != body)
					internalBodyTransform(body);
			}
		}

	}
}
