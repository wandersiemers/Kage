package lu.uni.snt.cid.ccg

class Edge {
    @JvmField
    var sourceSig: String = "" // Sig is short for 'Signature'
    @JvmField
    var targetSig: String = "" // Sig is short for 'Signature'
    @JvmField
    var conditions = HashSet<String>()

    override fun toString(): String {
        return "$conditions:$sourceSig-->$targetSig"
    }
}
