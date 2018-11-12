package de.npecomplete.mc.testproject.lisp.function;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import de.npecomplete.mc.testproject.lisp.LispException;
import de.npecomplete.mc.testproject.lisp.data.Keyword;

@SuppressWarnings("unchecked")
public interface LispFunction {
	default Object apply() {
		throw new LispException("Wrong arity: 0");
	}

	default Object apply(Object par1) {
		throw new LispException("Wrong arity: 1");
	}

	default Object apply(Object par1, Object par2) {
		throw new LispException("Wrong arity: 2");
	}

	default Object apply(Object par1, Object par2, Object par3) {
		throw new LispException("Wrong arity: 3");
	}

	default Object apply(Object par1, Object par2, Object par3, Object par4, Object... more) {
		throw new LispException("Wrong arity: " + (4 + more.length));
	}

	/**
	 * Attempts to create an {@link LispFunction} from the given object.
	 */
	static LispFunction from(Object o) {
		if (o instanceof LispFunction) {
			return (LispFunction) o;
		}
		if (o instanceof Keyword) {
			return new LispFunction() {
				@Override
				public Object apply(Object par) {
					return par instanceof Map ? ((Map) par).get(o) : null;
				}
			};
		}
		if (o instanceof Set) {
			return new LispFunction() {
				@Override
				public Object apply(Object par) {
					return ((Set) o).contains(par) ? par : null;
				}
			};
		}
		if (o instanceof Map) {
			return new LispFunction() {
				@Override
				public Object apply(Object par) {
					return ((Map) o).get(par);
				}
			};
		}
		if (o instanceof Function) {
			return new LispFunction() {
				@Override
				public Object apply(Object par) {
					return ((Function) o).apply(par);
				}
			};
		}
		if (o instanceof Supplier) {
			return new LispFunction() {
				@Override
				public Object apply() {
					return ((Supplier) o).get();
				}
			};
		}
		if (o instanceof Consumer) {
			return new LispFunction() {
				@Override
				public Object apply(Object par) {
					((Consumer) o).accept(par);
					return null;
				}
			};
		}
		if (o instanceof Callable) {
			return new LispFunction() {
				@Override
				public Object apply() {
					try {
						return ((Callable) o).call();
					} catch (Exception e) {
						throw new LispException("Failed to call Callable", e);
					}
				}
			};
		}
		return null;
	}
}
