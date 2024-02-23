package lu.uni.snt.cid.ccg

import lu.uni.snt.cid.Config
import lu.uni.snt.cid.ccg.ConditionalCallGraph.addEdge
import lu.uni.snt.cid.ccg.ConditionalCallGraph.getEdge
import lu.uni.snt.cid.utils.SootUtils
import soot.Body
import soot.Unit
import soot.Value
import soot.jimple.*
import soot.toolkits.graph.ExceptionalUnitGraph

object AndroidSDKVersionChecker {
    @JvmStatic
    fun scan(body: Body) {
        if (!body.method.declaringClass.name.startsWith("android.support")) {
            if (body.toString().contains(Config.FIELD_VERSION_SDK_INT)) {
                Config.containsSDKVersionChecker = true
            }

            val graph = ExceptionalUnitGraph(body)
            for (unit in graph.heads) {
                traverse(body, graph, unit)
            }
        }
    }

    private fun traverse(
        body: Body, graph: ExceptionalUnitGraph, unit: Unit, sdkIntValues: MutableSet<Value?> = HashSet(),
        visitedUnits: MutableSet<Unit?> =
            HashSet(), conditions: Set<String?> = HashSet(), animationChecked: Boolean = false
    ) {
        if (visitedUnits.contains(unit)) {
            return
        } else {
            visitedUnits.add(unit)
        }

        var succUnits: MutableList<Unit>
        var stmt = unit as Stmt
        var isSDKCheckIfStmt = false
        var isAnimationEnabledIfStmt = false

        while (true) {
            if (stmt is AssignStmt) {
                handleAssignStmt(stmt, sdkIntValues)
                handleAnimationAssignStmt(stmt, HashSet())
            }

            if (stmt.containsInvokeExpr()) {
                if (handleInvokeExpr(body, stmt, conditions)) continue
                if (handleInvokeAnimatorsEnabledExpr(body, stmt, conditions)) continue
            }

            if (stmt is IfStmt) {
                isAnimationEnabledIfStmt = handleIfStmtAnimations(stmt)
                isSDKCheckIfStmt = handleIfStmt(stmt, sdkIntValues, isSDKCheckIfStmt)
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
                // It's a return statement
                return
            } else {
                break
            }
        }

        if (isSDKCheckIfStmt) {
            if (stmt is IfStmt) {
                traverse(
                    body,
                    graph,
                    stmt.target,
                    sdkIntValues,
                    visitedUnits,
                    conditions.plus(stmt.condition.toString()),
                    animationChecked
                )

                succUnits.remove(stmt.target)
                val negativeConditions = HashSet(conditions)
                negativeConditions.add("-${stmt.condition}")
                for (u in succUnits) {
                    traverse(body, graph, u, sdkIntValues, visitedUnits, negativeConditions, animationChecked)
                }
            }
        } else {
            for (u in succUnits) {
                traverse(body, graph, u, sdkIntValues, visitedUnits, conditions, animationChecked)
            }
        }

        if (isAnimationEnabledIfStmt) {
            if (stmt is IfStmt) {
                traverse(
                    body,
                    graph,
                    stmt.target,
                    sdkIntValues,
                    visitedUnits,
                    conditions.plus(stmt.condition.toString()),
                    true
                )

                succUnits.remove(stmt.target)
                val negativeConditions = HashSet(conditions)
                negativeConditions.add("-${stmt.condition}")
                for (u in succUnits) {
                    traverse(body, graph, u, sdkIntValues, visitedUnits, negativeConditions, true)
                }
            }
        }
    }

    private fun handleAssignStmt(stmt: AssignStmt, sdkIntValues: MutableSet<Value?>) {
        val leftOp = stmt.leftOp

        if (stmt.toString().contains(Config.FIELD_VERSION_SDK_INT)) {
            sdkIntValues.add(leftOp)
        } else {
            // Remove killed references
            sdkIntValues.remove(leftOp)
        }
    }

    private fun handleAnimationAssignStmt(stmt: AssignStmt, animationValues: MutableSet<Value?>) {
        val leftOp = stmt.leftOp

        if (stmt.toString().contains("areAnimatorsEnabled")) {
            println("found animators enabled check in full statement $stmt")
            animationValues.add(leftOp)
        } else {
            // Remove killed references
            animationValues.remove(leftOp)
        }
    }

    private fun handleIfStmt(
        stmt: IfStmt,
        sdkIntValues: MutableSet<Value?>,
        sdkChecker: Boolean
    ): Boolean {
        var sdkChecker1 = sdkChecker
        for (vb in stmt.condition.useBoxes) {
            if (sdkIntValues.contains(vb.value)) {
                sdkChecker1 = true
                break
            }
        }
        return sdkChecker1
    }

    private fun handleIfStmtAnimations(
        stmt: IfStmt,
    ): Boolean {
        if (stmt.toString().contains("areAnimatorsEnabled", true)) {
            println("found animators enabled check in if stmt  ${stmt.condition}")
            return true
        }
        return false
    }

    private fun handleInvokeExpr(
        body: Body,
        stmt: Stmt,
        conditions: Set<String?>
    ): Boolean {
        val edge = getEdge(
            body.method.signature,
            stmt.invokeExpr.method.signature
        )
        edge.conditions.add(conditions.toString())

        addEdge(edge)

        if (stmt.invokeExpr is InterfaceInvokeExpr) {
            val sootMethod = stmt.invokeExpr.method

            if (sootMethod.declaration.contains("private")) {
                // If the method is declared as private, then it cannot be extended by the subclasses.
                return true
            }

            val sootClass = sootMethod.declaringClass
            val subClasses = SootUtils.getAllSubClasses(sootClass)

            for (subClass in subClasses) {
                val subclassEdge = getEdge(
                    edge.sourceSig,
                    edge.targetSig.replace(sootClass.name + ":", subClass.name + ":")
                )

                subclassEdge.conditions.addAll(edge.conditions)
                addEdge(subclassEdge)
            }
        }

        return false
    }

    private fun handleInvokeAnimatorsEnabledExpr(body: Body, stmt: Stmt, conditions: Set<String?>): Boolean {
        val edge = getEdge(
            body.method.signature,
            stmt.invokeExpr.method.signature
        )
        edge.conditions.add(conditions.toString())

        addEdge(edge)

        if (stmt.invokeExpr.method.signature.contains("encrypted", true)) {
            println("invoked encrypted check, sig ${stmt.invokeExpr.method.signature}")
        }

        if (stmt.invokeExpr is InterfaceInvokeExpr) {
            val sootMethod = stmt.invokeExpr.method

            if (sootMethod.declaration.contains("private")) {
                // If the method is declared as private, then it cannot be extended by the subclasses.
                println("private method ${sootMethod.declaration}")
                return true
            }

            val sootClass = sootMethod.declaringClass
            val subClasses = SootUtils.getAllSubClasses(sootClass)

            for (subClass in subClasses) {
                val subclassEdge = getEdge(
                    edge.sourceSig,
                    edge.targetSig.replace(sootClass.name + ":", subClass.name + ":")
                )

                subclassEdge.conditions.addAll(edge.conditions)
                addEdge(subclassEdge)
            }
        }

        return false
    }
}
