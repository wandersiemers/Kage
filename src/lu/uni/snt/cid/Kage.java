package lu.uni.snt.cid;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import lu.uni.snt.cid.api.APIExtractor;
import lu.uni.snt.cid.api.APILife;
import lu.uni.snt.cid.ccg.ConditionalCallGraph;
import lu.uni.snt.cid.dcl.DexHunter;
import lu.uni.snt.cid.utils.MethodSignature;

public class Kage
{
	public static void main(String[] args) 
	{
		Config.apkPath = args[0];
		Config.androidJars = args[1];
		String apkName = Config.apkPath;
		if (apkName.contains("/"))
		{
			Config.apkName = apkName.substring(apkName.lastIndexOf('/')+1);
		}
		
		try
		{
			mine(Config.apkPath, Config.androidJars);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			clean(Config.apkName);
		}
	}

	public static void mine(String apkPath, String androidJars)
	{
		//(1) Unzip Android APK and extract all additonally accessed DEXes
		Set<String> additionalDexes = new DexHunter(apkPath).hunt();
		
		//(2) Extracting the targeted Sdk version
		AndroidManifest manifest = new AndroidManifest(Config.apkName);
		int apiLevel = inferAPILevel(manifest);
		
		//(3) Extracting the leveraged Android APIs (primary and all)
		APIExtractor extractor = new APIExtractor();
		extractor.transform(apkPath, androidJars, apiLevel);
		System.out.println("Found " + additionalDexes.size() + " additional DEX files. Now visiting them one by one.");
		for (String dex : additionalDexes)
		{
			extractor.transform(dex, androidJars, apiLevel);
		}
		
		int minAPILevel = manifest.getMinSdkVersion();
		int maxAPILevel = manifest.getMaxSdkVersion();
		if (-1 == maxAPILevel)
		{
			maxAPILevel = Config.LATEST_API_LEVEL;
		}
		
		//(4) SDK check study (expand constructors)
		//AndroidSDKVersionChecker.scan(apkPath, androidJars);
		ConditionalCallGraph.expandConstructors();
		
		System.out.println("--------------------------------------------------------------------------------------------------------");
		
		System.out.println("Declared Min Sdk version is: " + manifest.getMinSdkVersion());
		System.out.println("Declared Target Sdk version is: " + manifest.getTargetSdkVersion());
		System.out.println("Declared Max Sdk version is: " + manifest.getMaxSdkVersion());
		
		System.out.println("Collected " + extractor.primaryAPIs.size() + " " + "Android APIs in the primary DEX file");
		System.out.println("Collected " + extractor.secondaryAPIs.size() + " " + "Android APIs in the secondary DEX files");
		
		Set<APILife> problematicAPIs_forward = new HashSet<APILife>();
		Set<APILife> protectedAPIs_forward = new HashSet<APILife>();
		Set<APILife> problematicAPIs_backward = new HashSet<APILife>();
		Set<APILife> protectedAPIs_backward = new HashSet<APILife>();
		
		for (String method : extractor.usedAndroidAPIs)
		{
			APILife lifetime = AndroidAPILifeModel.getInstance().getLifetime(method);
			
			if (lifetime.getMinAPILevel() == -1 || lifetime.getMaxAPILevel() == -1)
			{
				if (Config.DEBUG)
				{
					System.out.println("[DEBUG] Wrong Min/Max API Level for " + lifetime.getSignature());
				}
				
				continue;
			}
			
			if (lifetime.getMaxAPILevel() < maxAPILevel)
			{
				if (ConditionalCallGraph.obtainConditions(method).isEmpty())
				{
					
					problematicAPIs_forward.add(lifetime);
				}
				else
				{
					protectedAPIs_forward.add(lifetime);
				}
			}
			
			if (lifetime.getMinAPILevel() > minAPILevel && lifetime.getMinAPILevel() > 1)
			{
				if (ConditionalCallGraph.obtainConditions(method).isEmpty())
				{
					problematicAPIs_backward.add(lifetime);
				}
				else
				{
					protectedAPIs_backward.add(lifetime);
				}
			}
		}
		
		System.out.println("SDK Check:" + Config.containsSDKVersionChecker);
		System.out.println("Found " + protectedAPIs_forward.size() + " Android APIs (for forward compatibility) that are accessed with protection (SDK Check)");
		System.out.println("Found " + problematicAPIs_forward.size() + " Android APIs (for forward compatibility) that are accessed problematically ");
		System.out.println("Found " + protectedAPIs_backward.size() + " Android APIs (for backward compatibility) that are accessed with protection (SDK Check)");
		System.out.println("Found " + problematicAPIs_backward.size() + " Android APIs (for backward compatibility) that are accessed problematically ");
		
		for (APILife lifetime : protectedAPIs_forward)
		{
			System.out.println("\n==>Protected_Forward" + lifetime);
			System.out.println(extractor.api2callers.get(lifetime.getSignature()));
			for (String methodSig : extractor.api2callers.get(lifetime.getSignature()))
			{
				boolean isLibraryMethod = AndroidLibraries.isAndroidLibrary(new MethodSignature(methodSig).getCls());
				if (isLibraryMethod)
				{
					System.out.println("--Library:True-->" + lifetime + "-->" + methodSig);
				}
				else
				{
					System.out.println("--Library:False-->" + lifetime + "-->" + methodSig);
					System.out.println(ConditionalCallGraph.obtainCallStack(methodSig));
				}
			}
		}
		
		for (APILife lifetime : problematicAPIs_forward)
		{
			System.out.println("\n==>Problematic_Forward" + lifetime);
			System.out.println(extractor.api2callers.get(lifetime.getSignature()));
			for (String methodSig : extractor.api2callers.get(lifetime.getSignature()))
			{
				boolean isLibraryMethod = AndroidLibraries.isAndroidLibrary(new MethodSignature(methodSig).getCls());
				if (isLibraryMethod)
				{
					System.out.println("--Library:True-->" + lifetime + "-->" + methodSig);
				}
				else
				{
					System.out.println("--Library:False-->" + lifetime + "-->" + methodSig);
					System.out.println(ConditionalCallGraph.obtainCallStack(methodSig));
				}
			}
		}
		
		
		for (APILife lifetime : protectedAPIs_backward)
		{
			System.out.println("\n==>Protected_Backward" + lifetime);
			System.out.println(extractor.api2callers.get(lifetime.getSignature()));
			for (String methodSig : extractor.api2callers.get(lifetime.getSignature()))
			{
				boolean isLibraryMethod = AndroidLibraries.isAndroidLibrary(new MethodSignature(methodSig).getCls());
				if (isLibraryMethod)
				{
					System.out.println("--Library:True-->" + lifetime + "-->" + methodSig);
				}
				else
				{
					System.out.println("--Library:False-->" + lifetime + "-->" + methodSig);
					System.out.println(ConditionalCallGraph.obtainCallStack(methodSig));
				}
			}
		}
		
		for (APILife lifetime : problematicAPIs_backward)
		{
			System.out.println("\n==>Problematic_Backward" + lifetime);
			System.out.println(extractor.api2callers.get(lifetime.getSignature()));
			for (String method : extractor.api2callers.get(lifetime.getSignature()))
			{
				boolean isLibraryMethod = AndroidLibraries.isAndroidLibrary(new MethodSignature(method).getCls());
				if (isLibraryMethod)
				{
					System.out.println("--Library:True-->" + lifetime + "-->" + method);
				}
				else
				{
					System.out.println("--Library:False-->" + lifetime + "-->" + method);
					System.out.println(ConditionalCallGraph.obtainCallStack(method));
				}	
			}
		}
	}
	
	public static String constraint(int min1, int max1, int min2, int max2)
	{
		int min = min1 > min2 ? min1 : min2;
		int max = max1 > max2 ? max2 : max1;
		
		return min + "," + max;
	}
	
	public static String constraint(int min1, int max1, String minMax)
	{
		int min2 = Integer.parseInt(minMax.split(",")[0]);
		int max2 = Integer.parseInt(minMax.split(",")[1]);
		
		return constraint(min1, max1, min2, max2);
	}
	
	public static int inferAPILevel(AndroidManifest manifest)
	{
		int apiLevel = -1;
		if (-1 != manifest.getTargetSdkVersion())
		{
			apiLevel = manifest.getTargetSdkVersion();
		}
		else if (-1 != manifest.getMaxSdkVersion())
		{
			apiLevel = manifest.getMaxSdkVersion();
		}
		else
		{
			apiLevel = Config.DEFAULT_API_LEVEL;
		}
		
		return apiLevel;
	}
	
	public static void clean(String apkName)
	{
		try 
		{
			FileUtils.deleteDirectory(new File(apkName + ".unzip"));
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
}



//(4) Extracting the SDK version checks
//Map<String, APISDKCheck> method2SDKChecks = AndroidSDKVersionChecker.scan(apkPath, androidJars);

/*
String constraint4Primary = Integer.MIN_VALUE + "," + Integer.MAX_VALUE;
String constraint4Primary2 = Integer.MIN_VALUE + "," + Integer.MAX_VALUE;
StringBuilder content = new StringBuilder();
for (String method : extractor.primaryAPIs)
{
	content.append(method + "\n");
	
	APILife lifetime = AndroidAPILifeModel.getInstance().getLifetime(method);
	constraint4Primary = constraint(lifetime.getMinAPILevel(), lifetime.getMaxAPILevel(), constraint4Primary);
	
	APILife lifetime2 = AndroidAPILifeModel.getInstance().getLifetime(method, method2SDKChecks);
	constraint4Primary2 = constraint(lifetime2.getMinAPILevel(), lifetime2.getMaxAPILevel(), constraint4Primary2);
}
CommonUtils.writeResultToFile("mining4u_primary_apis_" + apkName + ".txt", content.toString());

Set<String> allAPIs = new HashSet<String>();
allAPIs.addAll(extractor.primaryAPIs);
allAPIs.addAll(extractor.secondaryAPIs);

String constraint4Secondary = Integer.MIN_VALUE + "," + Integer.MAX_VALUE;
String constraint4Secondary2 = Integer.MIN_VALUE + "," + Integer.MAX_VALUE;

content = new StringBuilder();
for (String method : allAPIs)
{
	content.append(method + "\n");
	
	APILife lifetime = AndroidAPILifeModel.getInstance().getLifetime(method);
	constraint4Secondary = constraint(lifetime.getMinAPILevel(), lifetime.getMaxAPILevel(), constraint4Secondary);
	
	APILife lifetime2 = AndroidAPILifeModel.getInstance().getLifetime(method, method2SDKChecks);
	constraint4Secondary2 = constraint(lifetime2.getMinAPILevel(), lifetime2.getMaxAPILevel(), constraint4Secondary2);
	
	System.out.println("==>" + method + ":[" + lifetime2.getMinAPILevel() + "," + lifetime2.getMaxAPILevel() + "]");
}
CommonUtils.writeResultToFile("mining4u_secondary_apis_" + apkName + ".txt", content.toString());

System.out.println("Declared Min Sdk version is: " + manifest.getMinSdkVersion());
System.out.println("Declared Target Sdk version is: " + manifest.getTargetSdkVersion());
System.out.println("Declared Max Sdk version is: " + manifest.getMaxSdkVersion());

System.out.println("Expected Sdk versions are (without DCL): " + constraint4Primary);
System.out.println("Expected Sdk versions are (without DCL, With SDK Check): " + constraint4Primary2);
System.out.println("Expected Sdk versions are (with DCL): " + constraint4Secondary);
System.out.println("Expected Sdk versions are (with DCL, With SDK Check): " + constraint4Secondary2);
*/