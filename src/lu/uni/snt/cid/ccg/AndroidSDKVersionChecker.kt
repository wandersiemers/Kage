package lu.uni.snt.cid.ccg

import lu.uni.snt.cid.Config
import lu.uni.snt.cid.ccg.ConditionalCallGraph.addEdge
import lu.uni.snt.cid.ccg.ConditionalCallGraph.getEdge
import lu.uni.snt.cid.utils.SootUtils
import soot.*
import soot.Unit
import soot.jimple.*
import soot.toolkits.graph.ExceptionalUnitGraph

object AndroidSDKVersionChecker {
    @JvmStatic
	fun scan(b: Body) {
        if (!b.method.declaringClass.name.startsWith("android.support")) {
            if (b.toString().contains(Config.FIELD_VERSION_SDK_INT)) {
                Config.containsSDKVersionChecker = true
            }

            val graph = ExceptionalUnitGraph(b)

            for (unit in graph.heads) {
                traverse(b, graph, unit, HashSet(), HashSet(), HashSet())
            }
        }
    }

    private fun traverse(
        b: Body, graph: ExceptionalUnitGraph, unit: Unit, sdkIntValues: MutableSet<Value?>,
        conditions: Set<String?>, visitedUnits: MutableSet<Unit?>
    ) {
        if (visitedUnits.contains(unit)) {
            return
        } else {
            visitedUnits.add(unit)
        }

        var succUnits: MutableList<Unit>
        var stmt = unit as Stmt
        var sdkChecker = false

        while (true) {
            if (stmt is AssignStmt) {
                val leftOp = stmt.leftOp

                if (stmt.toString().contains(Config.FIELD_VERSION_SDK_INT)) {
                    sdkIntValues.add(leftOp)
                } else {
                    //Remove killed references
                    sdkIntValues.remove(leftOp)
                }
            }

            if (stmt.containsInvokeExpr()) {
                val edge = getEdge(
                    b.method.signature,
                    stmt.invokeExpr.method.signature
                )
                edge!!.conditions.add(conditions.toString())

                addEdge(edge)

                if (stmt.invokeExpr is InterfaceInvokeExpr) {
                    val sootMethod = stmt.invokeExpr.method

                    if (sootMethod.declaration.contains("private")) {
                        // If the method is declared as private, then it cannot be extended by the subclasses.
                        continue
                    }

                    val sootClass = sootMethod.declaringClass
                    val subClasses = SootUtils.getAllSubClasses(sootClass)

                    for (subClass in subClasses) {
                        val e = getEdge(
                            edge.sourceSig,
                            edge.targetSig.replace(sootClass.name + ":", subClass.name + ":")
                        )

                        e!!.conditions.addAll(edge.conditions)
                        addEdge(e)
                    }
                }
            }

            if (stmt is IfStmt) {
                for (vb in stmt.condition.useBoxes) {
                    if (sdkIntValues.contains(vb.value)) {
                        sdkChecker = true
                        break
                    }
                }
            }

            succUnits = graph.getSuccsOf(stmt)
            if (succUnits.size == 1) {
                stmt = succUnits[0] as Stmt

                if (stmt is ReturnStmt) {
                    return
                }

                if (visitedUnits.contains(stmt)) {
                    return
                } else {
                    visitedUnits.add(stmt)
                }
            } else if (succUnits.isEmpty()) {
                //It's a return statement
                return
            } else {
                break
            }
        }

        if (sdkChecker) {
            if (stmt is IfStmt) {
                val ifStmt = stmt
                val targetStmt = ifStmt.target

                val positiveConditions: MutableSet<String?> = HashSet(conditions)
                positiveConditions.add(ifStmt.condition.toString())
                traverse(b, graph, targetStmt, sdkIntValues, positiveConditions, visitedUnits)

                succUnits.remove(targetStmt)
                val negativeConditions: MutableSet<String?> = HashSet(conditions)
                negativeConditions.add("-" + ifStmt.condition.toString())
                for (u in succUnits) {
                    traverse(b, graph, u, sdkIntValues, negativeConditions, visitedUnits)
                }
            }
        } else {
            for (u in succUnits) {
                traverse(b, graph, u, sdkIntValues, conditions, visitedUnits)
            }
        }
    }
}
