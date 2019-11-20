
package de.fraunhofer.aisec.analysis.markevaluation;

import de.fraunhofer.aisec.analysis.scp.ConstantValue;
import de.fraunhofer.aisec.analysis.structures.Pair;
import de.fraunhofer.aisec.analysis.structures.ResultWithContext;
import de.fraunhofer.aisec.crymlin.CrymlinQueryWrapper;
import de.fraunhofer.aisec.mark.markDsl.*;
import de.fraunhofer.aisec.mark.markDsl.impl.AlternativeExpressionImpl;
import de.fraunhofer.aisec.markmodel.Mark;
import de.fraunhofer.aisec.markmodel.fsm.FSM;
import de.fraunhofer.aisec.markmodel.fsm.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.python.antlr.base.expr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.stream.Collectors;

/** Static helper methods for evaluating MARK expressions. */
public class ExpressionHelper {

	private static final Logger log = LoggerFactory.getLogger(ExpressionHelper.class);

	public static String exprToString(Expression expr) {
		if (expr == null) {
			return " null ";
		}

		if (expr instanceof LogicalOrExpression) {
			return exprToString(((LogicalOrExpression) expr).getLeft())
					+ " || "
					+ exprToString(((LogicalOrExpression) expr).getRight());
		} else if (expr instanceof LogicalAndExpression) {
			return exprToString(((LogicalAndExpression) expr).getLeft())
					+ " && "
					+ exprToString(((LogicalAndExpression) expr).getRight());
		} else if (expr instanceof ComparisonExpression) {
			ComparisonExpression compExpr = (ComparisonExpression) expr;
			return exprToString(compExpr.getLeft())
					+ " "
					+ compExpr.getOp()
					+ " "
					+ exprToString(compExpr.getRight());
		} else if (expr instanceof FunctionCallExpression) {
			FunctionCallExpression fExpr = (FunctionCallExpression) expr;
			String name = fExpr.getName();
			return name
					+ "("
					+ fExpr.getArgs().stream().map(ExpressionHelper::argToString).collect(Collectors.joining(", "))
					+ ")";
		} else if (expr instanceof LiteralListExpression) {
			return "[ "
					+ ((LiteralListExpression) expr).getValues().stream().map(Literal::getValue).collect(Collectors.joining(", "))
					+ " ]";
		} else if (expr instanceof RepetitionExpression) {
			RepetitionExpression inner = (RepetitionExpression) expr;
			// todo @FW do we want this optimization () can be omitted if inner is no sequence
			if (inner.getExpr() instanceof SequenceExpression) {
				return "(" + exprToString(inner.getExpr()) + ")" + inner.getOp();
			} else {
				return exprToString(inner.getExpr()) + inner.getOp();
			}
		} else if (expr instanceof Operand) {
			return ((Operand) expr).getOperand();
		} else if (expr instanceof Literal) {
			return ((Literal) expr).getValue();
		} else if (expr instanceof SequenceExpression) {
			SequenceExpression seq = ((SequenceExpression) expr);
			return exprToString(seq.getLeft()) + seq.getOp() + " " + exprToString(seq.getRight());
		} else if (expr instanceof Terminal) {
			Terminal inner = (Terminal) expr;
			return inner.getEntity() + "." + inner.getOp() + "()";
		} else if (expr instanceof OrderExpression) {
			OrderExpression order = (OrderExpression) expr;
			SequenceExpression seq = (SequenceExpression) order.getExp();
			return "order " + exprToString(seq);
		}
		return "UNKNOWN EXPRESSION TYPE: " + expr.getClass();
	}

	public static String argToString(Argument arg) {
		return exprToString((Expression) arg); // Every Argument is also an Expression
	}

	@Nullable
	public static String asString(ResultWithContext opt) {
		if (opt == null) {
			return null;
		}

		return asString(opt.get());
	}

	@Nullable
	public static String asString(Object opt) {
		if (opt == null) {
			return null;
		}

		if (opt instanceof String) {
			return (String) opt;
		}

		if (opt instanceof ConstantValue && ((ConstantValue) opt).isString()) {
			return (String) ((ConstantValue) opt).getValue();
		}

		return null;
	}

	@Nullable
	public static Number asNumber(ResultWithContext opt) {
		if (opt == null) {
			return null;
		}
		return asNumber(opt.get());
	}

	public static Number asNumber(Object opt) {
		if (opt == null) {
			return null;
		}
		if (opt instanceof Integer) {
			return (Integer) opt;
		}
		if (opt instanceof ConstantValue && ((ConstantValue) opt).isNumeric()) {
			return (Number) ((ConstantValue) opt).getValue();
		}

		return null;
	}

	@Nullable
	public static Boolean asBoolean(ResultWithContext opt) {
		if (opt == null) {
			return null;
		}

		return asBoolean(opt.get());
	}

	@Nullable
	public static Boolean asBoolean(Object opt) {
		if (opt == null) {
			return null;
		}

		if (opt instanceof Boolean) {
			return (Boolean) opt;
		}

		if (opt instanceof ConstantValue && ((ConstantValue) opt).isBoolean()) {
			return (Boolean) ((ConstantValue) opt).getValue();
		}

		return null;

	}

	public static void collectVars(Expression expr, HashSet<String> vars) {
		if (expr instanceof OrderExpression) {
			// will not contain vars
		} else if (expr instanceof LogicalOrExpression) {
			collectVars(((LogicalOrExpression) expr).getLeft(), vars);
			collectVars(((LogicalOrExpression) expr).getRight(), vars);
		} else if (expr instanceof LogicalAndExpression) {
			collectVars(((LogicalAndExpression) expr).getLeft(), vars);
			collectVars(((LogicalAndExpression) expr).getRight(), vars);
		} else if (expr instanceof ComparisonExpression) {
			collectVars(((ComparisonExpression) expr).getLeft(), vars);
			collectVars(((ComparisonExpression) expr).getRight(), vars);
		} else if (expr instanceof MultiplicationExpression) {
			collectVars(((MultiplicationExpression) expr).getLeft(), vars);
			collectVars(((MultiplicationExpression) expr).getRight(), vars);
		} else if (expr instanceof UnaryExpression) {
			collectVars(((UnaryExpression) expr).getExp(), vars);
		} else if (expr instanceof Literal) {
			// not a var
		} else if (expr instanceof Operand) {
			vars.add(((Operand) expr).getOperand());
		} else if (expr instanceof FunctionCallExpression) {
			for (Argument ex : ((FunctionCallExpression) expr).getArgs()) {
				if (ex instanceof Expression) {
					collectVars((Expression) ex, vars);
				} else {
					log.error("This should not happen in MARK. Not an expression: " + ex.getClass());
				}
			}
		} else if (expr instanceof LiteralListExpression) {
			// does not contain vars
		}
	}

	public static void collectInstanceAndOps(OrderExpression expr, HashSet<Pair<String, String>> instance2op) {
		if (expr instanceof Terminal) {
			Terminal inner = (Terminal) expr;
			Pair<String, String> p = new Pair<>(inner.getEntity(), inner.getOp());
			instance2op.add(p);
		} else if (expr instanceof SequenceExpression) {
			SequenceExpression inner = (SequenceExpression) expr;
			collectInstanceAndOps(inner.getLeft(), instance2op);
			collectInstanceAndOps(inner.getRight(), instance2op);
		} else if (expr instanceof RepetitionExpression) {
			RepetitionExpression inner = (RepetitionExpression) expr;
			collectInstanceAndOps(inner.getExpr(), instance2op);
		} else if (expr instanceof AlternativeExpressionImpl) {
			AlternativeExpression inner = (AlternativeExpression) expr;
			collectInstanceAndOps(inner.getLeft(), instance2op);
			collectInstanceAndOps(inner.getRight(), instance2op);
		}
		return;

	}

}