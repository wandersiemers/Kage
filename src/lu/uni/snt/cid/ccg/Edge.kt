package lu.uni.snt.cid.ccg

class Edge {
    @JvmField
    var sourceSig: String = "" // Sig is short for 'Signature'
    @JvmField
    var targetSig: String = "" // Sig is short for 'Signature'
    @JvmField
    var conditions: Set<String> = HashSet()

    override fun toString(): String {
        return "$conditions:$sourceSig-->$targetSig"
    }
}
