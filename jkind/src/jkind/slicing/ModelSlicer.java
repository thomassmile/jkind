package jkind.slicing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jkind.analysis.evaluation.Evaluator;
import jkind.lustre.BinaryExpr;
import jkind.lustre.BinaryOp;
import jkind.lustre.Equation;
import jkind.lustre.Expr;
import jkind.lustre.FunctionCallExpr;
import jkind.lustre.IdExpr;
import jkind.lustre.Node;
import jkind.lustre.UnaryExpr;
import jkind.lustre.UnaryOp;
import jkind.lustre.values.Value;
import jkind.solvers.Model;
import jkind.solvers.SimpleFunction;
import jkind.solvers.SimpleModel;
import jkind.util.SexpUtil;
import jkind.util.StreamIndex;

public class ModelSlicer extends Evaluator {
	public static SimpleModel slice(Model original, Node node, DependencyMap dependencyMap,
			String property, int k) {
		return new ModelSlicer(original, node, dependencyMap).slice(property, k);
	}

	private final Model original;
	private final Map<String, Expr> equations = new HashMap<>();
	private final List<Expr> assertions;
	private final DependencyMap dependencyMap;

	private int k;
	private final SimpleModel sliced = new SimpleModel();

	private ModelSlicer(Model original, Node node, DependencyMap dependencyMap) {
		this.original = original;
		for (Equation eq : node.equations) {
			equations.put(eq.lhs.get(0).id, eq.expr);
		}
		this.assertions = node.assertions;
		this.dependencyMap = dependencyMap;
	}

	public SimpleModel slice(String property, int k) {
		sliceVariables(property);
		sliceFunctions(property, k);
		return sliced;
	}

	private void sliceVariables(String property) {
		DependencySet keep = dependencyMap.get(Dependency.variable(property));
		for (String var : original.getVariableNames()) {
			StreamIndex si = StreamIndex.decode(var);
			if (si != null && keep.contains(Dependency.variable(si.getStream()))) {
				sliced.addValue(si, original.getValue(var));
			}
		}
	}

	private void sliceFunctions(String property, int k) {
		eval(new IdExpr(property), k - 1);

		for (int i = 0; i < k; i++) {
			for (Expr assertion : assertions) {
				if (assertionRelevant(assertion, property)) {
					eval(assertion, i);
				}
			}
		}
	}

	private void eval(Expr expr, int k) {
		this.k = k;
		expr.accept(this);
	}

	private boolean assertionRelevant(Expr assertion, String property) {
		DependencySet propertyDependencies = dependencyMap.get(Dependency.variable(property));
		DependencySet assertionDependencies = DependencyVisitor.get(assertion);
		return !assertionDependencies.isEmpty()
				&& propertyDependencies.contains(assertionDependencies.iterator().next());
	}

	@Override
	public Value visit(BinaryExpr e) {
		if (e.op == BinaryOp.ARROW) {
			if (k == 0) {
				return e.left.accept(this);
			} else {
				return e.right.accept(this);
			}
		}

		return super.visit(e);
	}

	@Override
	public Value visit(FunctionCallExpr e) {
		List<Value> args = visitExprs(e.args);
		String enc = SexpUtil.encodeFunction(e.name).str;
		Value value = original.getFunction(enc).getValue(args);
		SimpleFunction fn = sliced.getFunction(enc);
		if (fn == null) {
			fn = new SimpleFunction();
			sliced.addFunction(enc, fn);
		}
		fn.addValue(args, value);
		return value;
	}

	@Override
	public Value visit(IdExpr e) {
		return sliced.getValue(new StreamIndex(e.id, k));
	}

	@Override
	public Value visit(UnaryExpr e) {
		if (e.op == UnaryOp.PRE) {
			k--;
			Value value = e.expr.accept(this);
			k++;
			return value;
		} else {
			return super.visit(e);
		}
	}
}
