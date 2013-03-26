package jkind.solvers.yices;

import java.math.BigInteger;
import java.util.List;

import jkind.solvers.BoolValue;
import jkind.solvers.Label;
import jkind.solvers.Model;
import jkind.solvers.NumericValue;
import jkind.solvers.Result;
import jkind.solvers.SatResult;
import jkind.solvers.UnsatResult;
import jkind.solvers.Value;
import jkind.solvers.yices.YicesParser.AliasContext;
import jkind.solvers.yices.YicesParser.FunctionContext;
import jkind.solvers.yices.YicesParser.SatResultContext;
import jkind.solvers.yices.YicesParser.UnsatCoreContext;
import jkind.solvers.yices.YicesParser.UnsatResultContext;
import jkind.solvers.yices.YicesParser.ValueContext;
import jkind.solvers.yices.YicesParser.VariableContext;

import org.antlr.v4.runtime.tree.TerminalNode;

public class ResultExtractorListener extends YicesBaseListener {
	private Result result;
	private Model model;
	
	public Result getResult() {
		return result;
	}

	@Override
	public void enterSatResult(SatResultContext ctx) {
		SatResult sat = new SatResult();
		model = sat.getModel();
		result = sat;
	}
	
	@Override
	public void enterUnsatResult(UnsatResultContext ctx) {
		result = new UnsatResult();
	}
	
	@Override
	public void enterUnsatCore(UnsatCoreContext ctx) {
		List<Label> unsatCore = ((UnsatResult) result).getUnsatCore();
		for (TerminalNode node : ctx.INT()) {
			unsatCore.add(new IntLabel(Integer.parseInt(node.getText())));
		}
	}

	@Override
	public void enterAlias(AliasContext ctx) {
		model.addAlias(ctx.ID(0).getText(), ctx.ID(1).getText());
	}

	@Override
	public void enterVariable(VariableContext ctx) {
		model.addValue(ctx.ID().getText(), value(ctx.value()));
	}

	@Override
	public void enterFunction(FunctionContext ctx) {
		String fn = ctx.ID().getText();
		BigInteger arg = new BigInteger(ctx.integer().getText());
		Value value = value(ctx.value());
		model.addFunctionValue(fn, arg, value);
	}

	private Value value(ValueContext ctx) {
		if (ctx.BOOL() != null) {
			return ctx.BOOL().getText().equals("true") ? BoolValue.TRUE : BoolValue.FALSE;
		} else {
			return new NumericValue(ctx.numeric().getText());
		}
	}
}
