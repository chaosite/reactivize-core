package il.ac.technion.cs.reactivize

import boomerang.scene.jimple.BoomerangPretransformer
import soot.*
import soot.baf.BafASMBackend
import soot.options.Options
import java.io.FileOutputStream

val REQUIRED_CLASS_NAMES = listOf("io.reactivex.rxjava3.subjects.BehaviorSubject")

class ReactivizePostCompiler {
    fun execute(spec: ReactivizeCompileSpec) {
        initSoot(spec)
        runSoot(spec)
        val transformer = ReactivizeTransformer()
        transformer.transform().forEach {
            emit(it, spec)
        }
    }

    private fun emit(c: SootClass, spec: ReactivizeCompileSpec) {
        val javaVersion = Options.v().java_version()
        val fileName = SourceLocator.v().getFileNameFor(c, Options.output_format_class)
        val os = FileOutputStream(fileName)
        val backend = BafASMBackend(c, javaVersion)
        backend.generateClassFile(os)
        os.close()
    }

    private fun initSoot(spec: ReactivizeCompileSpec) {
        G.v().resetSpark()
        Options.v().set_whole_program(true)
        Options.v().setPhaseOption("jb", "use-original-names:true")
        Options.v().setPhaseOption("cg.spark", "on")

        val javaHome = System.getProperty("java.home")
        val sootCp =
            "VIRTUAL_FS_FOR_JDK:${spec.compileClasspath.joinToString(separator = ":") { it.canonicalPath }}:${Scene.defaultJavaClassPath()}:${javaHome}"
        println(sootCp)
        Options.v().set_soot_classpath(sootCp)
        Options.v().set_process_dir(listOf(spec.workingDir.canonicalPath))
        Options.v().set_output_dir(spec.destinationDir.canonicalPath)

        Options.v().set_prepend_classpath(true)
        Options.v().set_no_bodies_for_excluded(true)
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_whole_program(true)

        Options.v().setPhaseOption("cg.spark", "on")
        Options.v().setPhaseOption("jb", "use-original-names:true")
        Options.v().set_output_format(Options.output_format_jimple)

        REQUIRED_CLASS_NAMES.forEach(Scene.v()::addBasicClass)

        for (className in spec.applicationClassNames) {
            Scene.v().addBasicClass(className, SootClass.BODIES)
            val c = Scene.v().forceResolve(className, SootClass.BODIES)
            c.setApplicationClass()
        }
        for (c in Scene.v().classes) {
            // TODO: Replace with Trie, to avoid O(n*m)
            for (p in spec.applicationClassPackagePrefixes) {
                if (c.javaStyleName.startsWith(p)) {
                    c.setApplicationClass()
                }
            }
        }
        Scene.v().loadNecessaryClasses()
    }

    private fun runSoot(spec: ReactivizeCompileSpec) {
        // TODO: Make the Reactivization run as part of the regular Soot run, instead of afterwards?
        PackManager.v().getPack("cg").apply()
        BoomerangPretransformer.v().apply()
        PackManager.v().getPack("wjtp").apply()
        PackManager.v().runPacks()
    }
}