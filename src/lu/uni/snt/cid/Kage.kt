package lu.uni.snt.cid

import lu.uni.snt.cid.api.APIExtractor
import lu.uni.snt.cid.api.APILife
import lu.uni.snt.cid.ccg.ConditionalCallGraph
import lu.uni.snt.cid.dcl.DexHunter
import lu.uni.snt.cid.utils.MethodSignature
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException

object Kage {
    @JvmStatic
    fun main(args: Array<String>) {
        Config.apkPath = args[0]
        Config.androidJars = args[1]
        val apkName = Config.apkPath
        if (apkName.contains("/")) {
            Config.apkName = apkName.substring(apkName.lastIndexOf('/') + 1)
        }

        try {
            mine(Config.apkPath, Config.androidJars)
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            clean(Config.apkName)
        }
    }

    private fun mine(apkPath: String?, androidJars: String?) {
        //(1) Unzip Android APK and extract all additionally accessed DEXes
        val additionalDexes = DexHunter(apkPath).hunt()

        //(2) Extracting the targeted Sdk version
        val manifest = AndroidManifest(Config.apkName)
        val apiLevel = inferAPILevel(manifest)

        //(3) Extracting the leveraged Android APIs (primary and all)
        val extractor = APIExtractor()
        extractor.transform(apkPath, androidJars, apiLevel)
        println("Found " + additionalDexes.size + " additional DEX files. Now visiting them one by one.")
        for (dex in additionalDexes) {
            extractor.transform(dex, androidJars, apiLevel)
        }

        val minAPILevel = manifest.minSdkVersion
        var maxAPILevel = manifest.maxSdkVersion
        if (-1 == maxAPILevel) {
            maxAPILevel = Config.LATEST_API_LEVEL
        }

        //(4) SDK check study (expand constructors)
        ConditionalCallGraph.expandConstructors()

        println("--------------------------------------------------------------------------------------------------------")

        println("Declared Min Sdk version is: " + manifest.minSdkVersion)
        println("Declared Target Sdk version is: " + manifest.targetSdkVersion)
        println("Declared Max Sdk version is: " + manifest.maxSdkVersion)

        println("Collected " + extractor.primaryAPIs.size + " " + "Android APIs in the primary DEX file")
        println("Collected " + extractor.secondaryAPIs.size + " " + "Android APIs in the secondary DEX files")

        val problematicAPIsForward: MutableSet<APILife> = HashSet()
        val protectedAPIsForward: MutableSet<APILife> = HashSet()
        val problematicAPIsBackward: MutableSet<APILife> = HashSet()
        val protectedAPIsBackward: MutableSet<APILife> = HashSet()

        for (method in extractor.usedAndroidAPIs) {
            val lifetime = AndroidAPILifeModel.getInstance().getLifetime(method)

            if (lifetime.minAPILevel == -1 || lifetime.maxAPILevel == -1) {
                if (Config.DEBUG) {
                    println("[DEBUG] Wrong Min/Max API Level for " + lifetime.signature)
                }

                continue
            }

            if (lifetime.maxAPILevel < maxAPILevel) {
                if (ConditionalCallGraph.obtainConditions(method).isEmpty()) {
                    problematicAPIsForward.add(lifetime)
                } else {
                    protectedAPIsForward.add(lifetime)
                }
            }

            if (lifetime.minAPILevel > minAPILevel && lifetime.minAPILevel > 1) {
                if (ConditionalCallGraph.obtainConditions(method).isEmpty()) {
                    problematicAPIsBackward.add(lifetime)
                } else {
                    protectedAPIsBackward.add(lifetime)
                }
            }
        }

        println("SDK Check:" + Config.containsSDKVersionChecker)
        println("Found " + protectedAPIsForward.size + " Android APIs (for forward compatibility) that are accessed with protection (SDK Check)")
        println("Found " + problematicAPIsForward.size + " Android APIs (for forward compatibility) that are accessed problematically ")
        println("Found " + protectedAPIsBackward.size + " Android APIs (for backward compatibility) that are accessed with protection (SDK Check)")
        println("Found " + problematicAPIsBackward.size + " Android APIs (for backward compatibility) that are accessed problematically ")

        for (lifetime in protectedAPIsForward) {
            println("\n==>Protected_Forward$lifetime")
            printMethod(extractor, lifetime)
        }

        for (lifetime in problematicAPIsForward) {
            println("\n==>Problematic_Forward$lifetime")
            printMethod(extractor, lifetime)
        }


        for (lifetime in protectedAPIsBackward) {
            println("\n==>Protected_Backward$lifetime")
            printMethod(extractor, lifetime)
        }

        for (lifetime in problematicAPIsBackward) {
            println("\n==>Problematic_Backward$lifetime")
            printMethod(extractor, lifetime)
        }
    }

    private fun printMethod(extractor: APIExtractor, lifetime: APILife) {
        println(extractor.api2callers[lifetime.signature])
        for (methodSig in extractor.api2callers[lifetime.signature]!!) {
            val isLibraryMethod = AndroidLibraries.isAndroidLibrary(MethodSignature(methodSig).cls)
            if (isLibraryMethod) {
                println("--Library:True-->$lifetime-->$methodSig")
            } else {
                println("--Library:False-->$lifetime-->$methodSig")
                println(ConditionalCallGraph.obtainCallStack(methodSig))
            }
        }
    }

    private fun inferAPILevel(manifest: AndroidManifest): Int {
        val apiLevel = if (-1 != manifest.targetSdkVersion) {
            manifest.targetSdkVersion
        } else if (-1 != manifest.maxSdkVersion) {
            manifest.maxSdkVersion
        } else {
            Config.DEFAULT_API_LEVEL
        }

        return apiLevel
    }

    private fun clean(apkName: String) {
        try {
            FileUtils.deleteDirectory(File("$apkName.unzip"))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
