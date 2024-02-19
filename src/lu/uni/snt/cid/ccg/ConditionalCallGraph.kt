package lu.uni.snt.cid.ccg

import lu.uni.snt.cid.utils.MethodSignature

object ConditionalCallGraph {
    private var tgtMethod2edges: MutableMap<String, MutableSet<Edge>?> = HashMap()
    private var cls2methods: MutableMap<String, MutableSet<String>> = HashMap()
    private var existingEdges: MutableMap<String, Edge> = HashMap()
    private var visitedCalls: MutableSet<String>? = null

    @JvmStatic
    fun addEdge(edge: Edge) {
        if (edge.sourceSig.isEmpty() || edge.targetSig.isEmpty()) {
            return
        }

        if (edge.sourceSig == edge.targetSig) {
            return
        }

        addEdgeToEdges(edge)

        if (!edge.sourceSig.contains("<init>")) {
            extracted(edge, edge.sourceSig)
        }

        if (!edge.targetSig.contains("<init>")) {
            extracted(edge, edge.targetSig)
        }
    }

    private fun extracted(edge: Edge, sig: String) {
        val cls = MethodSignature(sig).cls
        var methods: MutableSet<String>?
        if (cls2methods.containsKey(cls)) {
            methods = cls2methods[cls]
            if (null == methods) {
                methods = HashSet()
            }
        } else {
            methods = HashSet()
        }
        methods.add(edge.sourceSig)
        cls2methods[cls] = methods
    }

    private fun addEdgeToEdges(edge: Edge) {
        val tgtEdges = if (tgtMethod2edges.containsKey(edge.targetSig)) {
            tgtMethod2edges[edge.targetSig]
        } else {
            HashSet()
        }
        tgtEdges!!.add(edge)
        tgtMethod2edges[edge.targetSig] = tgtEdges
    }

    @JvmStatic
    fun getEdge(srcSig: String, tgtSig: String): Edge? {
        val key = "$srcSig/$tgtSig"
        if (existingEdges.containsKey(key)) {
            return existingEdges[key]
        } else {
            val edge = Edge()
            edge.sourceSig = srcSig
            edge.targetSig = tgtSig

            existingEdges[key] = edge

            return edge
        }
    }

    fun expandConstructors() {
        val initMethods: MutableSet<String> = HashSet()

        for (method in tgtMethod2edges.keys) {
            if (method.contains("<init>")) {
                initMethods.add(method)
            }
        }

        for (method in initMethods) {
            val cls = MethodSignature(method).cls

            if (cls2methods.containsKey(cls)) {
                val methods: Set<String> = cls2methods[cls]!!
                for (m in methods) {
                    val edge = Edge()
                    edge.sourceSig = method
                    edge.targetSig = m

                    addEdgeToEdges(edge)
                }
            }
        }
    }

    fun obtainConditions(methodSig: String): List<String> {
        val conditions: MutableList<String> = ArrayList()

        if (!tgtMethod2edges.containsKey(methodSig)) {
            return conditions
        }

        var edges: Set<Edge>? = tgtMethod2edges[methodSig]

        val workList: MutableList<Edge> = ArrayList(edges)
        val visitedEdges: MutableSet<Edge> = HashSet()

        while (workList.isNotEmpty()) {
            val e = workList.removeAt(0)
            visitedEdges.add(e)

            val cond = e.conditions.toString().replace("\\[".toRegex(), "").replace("]".toRegex(), "")

            if (cond.isNotEmpty()) {
                conditions.add(e.conditions.toString())
            }

            edges = tgtMethod2edges[e.sourceSig]

            if (null != edges) {
                for (edge in edges) {
                    if (!visitedEdges.contains(edge)) {
                        workList.add(edge)
                    }
                }
            }
        }

        return conditions
    }

    fun obtainCallStack(methodSig: String): List<String> {
        val callStack: MutableList<String> = ArrayList()
        callStack.add(methodSig + "\n")

        val arrow = "> "

        visitedCalls = hashSetOf(methodSig)

        if (null != tgtMethod2edges[methodSig]) {
            for (e in tgtMethod2edges[methodSig]!!) {
                obtainCallStack(callStack, "--$arrow", e)
            }
        }

        return callStack
    }

    private fun obtainCallStack(callStack: MutableList<String>, arrow: String, edge: Edge) {
        // Circle found, Stop here
        if (visitedCalls!!.contains(edge.sourceSig)) {
            return
        } else {
            visitedCalls!!.add(edge.sourceSig)
        }

        callStack.add("|" + arrow + edge.sourceSig + " " + edge.conditions + "\n")

        if (null != tgtMethod2edges[edge.sourceSig]) {
            for (e in tgtMethod2edges[edge.sourceSig]!!) {
                obtainCallStack(callStack, "--$arrow", e)
            }
        }
    }
}
