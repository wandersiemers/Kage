package lu.uni.snt.cid.ccg

class Edge {
    @JvmField
    var srcSig: String = ""

    @JvmField
    var tgtSig: String = ""

    @JvmField
    var conditions: Set<String> = HashSet()

    override fun toString(): String {
        return "$conditions:$srcSig-->$tgtSig"
    }
}
