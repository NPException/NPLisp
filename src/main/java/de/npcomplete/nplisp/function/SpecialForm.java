package de.npcomplete.nplisp.function;

import static de.npcomplete.nplisp.corelibrary.CoreLibrary.KW_MACRO;
import static de.npcomplete.nplisp.corelibrary.CoreLibrary.KW_PRIVATE;
import static de.npcomplete.nplisp.corelibrary.CoreLibrary.seq;

import java.util.Iterator;
import java.util.List;

import de.npcomplete.nplisp.Lisp;
import de.npcomplete.nplisp.LispException;
import de.npcomplete.nplisp.core.Environment;
import de.npcomplete.nplisp.core.Var;
import de.npcomplete.nplisp.corelibrary.CoreLibrary;
import de.npcomplete.nplisp.data.Sequence;
import de.npcomplete.nplisp.data.Symbol;
import de.npcomplete.nplisp.function.MultiArityFunction.Builder;
import de.npcomplete.nplisp.util.LispElf;
import de.npcomplete.nplisp.util.LispPrinter;

public interface SpecialForm {

	Object apply(Sequence args, Environment env, boolean allowRecur);

	// BASE IMPLEMENTATIONS //

	/**
	 * Takes one or two arguments, a symbol and an init form:
	 * <code>(def SYMBOL ?INIT)</code><br>
	 * The init form is evaluated and the resulting value
	 * bound to the var in the environment's namespace.
	 * Returns the var.
	 */
	static Var DEF(Sequence args, Environment env, boolean allowRecur) {
		if (!LispElf.matchSize(args, 1, 4)) {
			throw new LispException("'def' can take some optional flags and requires at lest 1 argument:" +
					" (def ?FLAGS NAME ?DOCSTRING ?INIT)");
		}

		boolean isMacro = false;
		boolean isPrivate = false;

		Object arg = args.first();

		// process flags
		if (CoreLibrary.isVector(arg)) {
			arg = Lisp.eval(arg, env, false);
			Sequence flags = seq(arg);
			if (flags != null) {
				for (Object flag : flags) {
					if (KW_PRIVATE.equals(flag)) {
						isPrivate = true;
					} else if (KW_MACRO.equals(flag)) {
						isMacro = true;
					}
				}
			}
			args = args.more();
			arg = args.first();
		}

		if (!LispElf.matchSize(args, 1, 3)) {
			throw new LispException("'def' can take some optional flags and docstring and requires at lest 1 argument:" +
					" (def ?FLAGS NAME ?DOCSTRING ?INIT)");
		}

		if (!(arg instanceof Symbol)) {
			String s = LispPrinter.prStr(arg);
			throw new LispException("'def' binding target is not a symbol: " + s);
		}
		Var var = env.namespace.define((Symbol) arg);
		if (!var.isFixed()) {
			var.setPrivate(isPrivate);
			var.macro(isMacro);
		}

		args = args.next();
		if (args == null) {
			return var;
		}

		arg = args.first();
		if (arg instanceof String) {
			args = args.next();
			if (args != null) {
				// init value is available, so this arg is the docstring
				String docstring = (String) arg;
				if (!docstring.isEmpty()) {
					var.doc(docstring);
				}
				arg = args.first();
			}
		}

		Object value = Lisp.eval(arg, env, false);
		var.bind(value);

		return var;
	}

	/**
	 * Takes any number of arguments. Evaluates the
	 * arguments and returns the result of the last
	 * evaluation.
	 */
	static Object DO(Sequence body, Environment env, boolean allowRecur) {
		Object result = null;
		while (body != null && !body.empty()) {
			Object expr = body.first();
			body = body.next();
			boolean allowTailCall = allowRecur && body == null;
			result = Lisp.eval(expr, env, allowTailCall);
		}
		return result;
	}

	/**
	 * Takes a parameter vector and one or more forms as arguments.
	 * Returns a function that when called, will bind the passed parameters
	 * to the symbols specified by the parameter vector and evaluate
	 * the body forms as if they are in an implicit 'do' block.
	 */
	static LispFunction FN(Sequence args, Environment env, boolean allowRecur) {
		Symbol name = null;

		if (args.first() instanceof Symbol) {
			name = (Symbol) args.first();
			args = args.more();
		}

		if (args.empty()) {
			throw new LispException("'fn' requires at least one argument: (fn name? [params*] exprs*)" +
					" or for multiple arities: (fn name? ([params*] exprs*) +)");
		}

		Object arg1 = args.first();
		if (arg1 instanceof List) {
			List<?> fnArgs = (List<?>) arg1;
			Sequence body = args.next();
			return new SingleArityFunction(name, env, body, fnArgs);
		}

		if (!(arg1 instanceof Sequence)) {
			throw new LispException("'fn' first argument must either be a list or a vector");
		}

		Builder fnBuilder = new MultiArityFunction.Builder(name, env);
		for (Object arg : args) {
			if (!(arg instanceof Sequence)) {
				throw new LispException("Arity variant of 'fn' must be a list");
			}
			fnBuilder.addArity((Sequence) arg);
		}
		return fnBuilder.build();
	}

	/**
	 * Takes 2-3 arguments: TEST, THEN, [ELSE].
	 * Evaluates TEST first. If TEST results in a truthy
	 * value, THEN is evaluated, otherwise ELSE is evaluated.
	 */
	static Object IF(Sequence args, Environment env, boolean allowRecur) {
		if (!LispElf.matchSize(args, 2, 3)) {
			throw new LispException("'if' requires 2 or 3 arguments: (if TEST THEN *ELSE*)");
		}
		Object test = Lisp.eval(args.first(), env, false);
		return LispElf.truthy(test)
				? Lisp.eval(args.next().first(), env, allowRecur)
				: Lisp.eval(args.next().more().first(), env, allowRecur);
	}

	/**
	 * Takes a binding form (a {@link List}) with symbol-value
	 * pairs, and a body (one or more forms).
	 * Binds the values to the symbols for code executed
	 * within the body.
	 */
	static Object LET(Sequence args, Environment env, boolean allowRecur) {
		if (args.empty()) {
			throw new LispException("'let' requires at least one argument: (let BINDINGS *&FORMS*)");
		}
		Object arg1 = args.first();
		if (!(arg1 instanceof List)) {
			throw new LispException("'let' first argument is not a List");
		}
		List<?> bindings = (List<?>) arg1;
		if (bindings.size() % 2 != 0) {
			throw new LispException("'let' bindings List doesn't have an even number of elements");
		}

		Environment localEnv = new Environment(env);

		// process bindings
		Iterator<?> it = bindings.iterator();
		while (it.hasNext()) {
			Object sym = it.next();
			if (!(sym instanceof Symbol)) {
				String s = LispPrinter.prStr(sym);
				throw new LispException("'let' binding target is not a symbol: " + s);
			}
			Object value = Lisp.eval(it.next(), localEnv, false);
			localEnv.bind((Symbol) sym, value);
		}

		// evaluate body
		return DO(args.next(), localEnv, allowRecur);
	}

	/**
	 * Just like 'let', but calls to 'recur' repeat the body with the values passed to recur
	 * bound to the symbols.
	 */
	static Object LOOP(Sequence args, Environment env, boolean allowRecur) {
		if (args.empty()) {
			throw new LispException("'loop' requires at least one argument: (let BINDINGS *&FORMS*)");
		}
		Object arg1 = args.first();
		if (!(arg1 instanceof List)) {
			throw new LispException("'loop' first argument is not a List");
		}
		List<?> bindings = (List<?>) arg1;
		if (bindings.size() % 2 != 0) {
			throw new LispException("'loop' bindings List doesn't have an even number of elements");
		}

		Environment localEnv = new Environment(env);
		Symbol[] paramSymbols = new Symbol[bindings.size() / 2];

		// process bindings
		for (int i = 0, size = paramSymbols.length; i < size; i++) {
			int symIndex = i * 2;
			int valIndex = i * 2 + 1;
			Object o = bindings.get(symIndex);
			if (!(o instanceof Symbol)) {
				String s = LispPrinter.prStr(o);
				throw new LispException("'let' binding target is not a symbol: " + s);
			}
			Symbol sym = (Symbol) o;
			paramSymbols[i] = sym;
			Object value = Lisp.eval(bindings.get(valIndex), localEnv, false);
			localEnv.bind(sym, value);
		}

		// evaluate body
		return SingleArityFunction.call(args.next(), paramSymbols, localEnv);
	}

	/**
	 * Takes a single symbol as an argument. Returns the var designated by that symbol,
	 * not its value.
	 */
	static Var VAR(Sequence args, Environment env, boolean allowRecur) {
		Object o = args.first();
		if (!(o instanceof Symbol)) {
			throw new LispException("Argument to 'var' must be a symbol");
		}
		return env.namespace.lookupVar((Symbol) o, true, false);
	}

	/**
	 * Takes a single form as an argument. Returns that argument unevaluated.
	 */
	static Object QUOTE(Sequence args, Environment env, boolean allowRecur) {
		if (!LispElf.matchSize(args, 1, 1)) {
			throw new LispException("'quote' requires exactly 1 argument: (quote FORM)");
		}
		return args.first();
	}
}
