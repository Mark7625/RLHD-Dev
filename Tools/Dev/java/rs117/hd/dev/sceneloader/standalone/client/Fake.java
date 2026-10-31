package rs117.hd.dev.sceneloader.standalone.client;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class Fake {
	private Fake() {}

	@SuppressWarnings("unchecked")
	public static <T> T of(Class<T> wanted, Object answers) {
		var byName = new HashMap<String, Method>();
		for (Method method : answers.getClass().getMethods())
			if (method.getDeclaringClass() != Object.class)
				byName.putIfAbsent(key(method), method);

		return (T) Proxy.newProxyInstance(
			wanted.getClassLoader(),
			new Class<?>[] { wanted },
			new Answers(wanted, answers, byName)
		);
	}

	private static String key(Method method) {
		return method.getName() + "/" + method.getParameterCount();
	}

	private static final class Answers implements InvocationHandler {
		private final Class<?> wanted;
		private final Object answers;
		private final Map<String, Method> byName;

		Answers(Class<?> wanted, Object answers, Map<String, Method> byName) {
			this.wanted = wanted;
			this.answers = answers;
			this.byName = byName;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			switch (method.getName()) {
				case "equals":
					if (method.getParameterCount() == 1)
						return proxy == args[0];
					break;
				case "hashCode":
					if (method.getParameterCount() == 0)
						return System.identityHashCode(proxy);
					break;
				case "toString":
					if (method.getParameterCount() == 0)
						return "Fake" + wanted.getSimpleName();
					break;
				default:
					break;
			}

			Method mine = byName.get(key(method));
			if (mine != null) {
				try {
					return asKindOf(method.getReturnType(), mine.invoke(answers, args));
				} catch (InvocationTargetException ex) {
					throw ex.getCause();
				}
			}

			if (method.isDefault())
				return callDefault(proxy, method, args);

			return blank(method.getReturnType());
		}
	}

	public static Object callDefault(Object proxy, Method method, Object[] args) throws Throwable {
		Class<?> owner = method.getDeclaringClass();
		return MethodHandles
			.privateLookupIn(owner, MethodHandles.lookup())
			.unreflectSpecial(method, owner)
			.bindTo(proxy)
			.invokeWithArguments(args == null ? new Object[0] : args);
	}

	private static Object asKindOf(Class<?> wanted, Object answer) {
		if (!(answer instanceof Number) || !wanted.isPrimitive())
			return answer;

		Number number = (Number) answer;
		if (wanted == float.class)
			return number.floatValue();
		if (wanted == double.class)
			return number.doubleValue();
		if (wanted == long.class)
			return number.longValue();
		if (wanted == int.class)
			return number.intValue();
		if (wanted == short.class)
			return number.shortValue();
		if (wanted == byte.class)
			return number.byteValue();
		return answer;
	}

	public static Object blank(Class<?> type) {
		if (!type.isPrimitive())
			return null;
		if (type == boolean.class)
			return false;
		if (type == void.class)
			return null;
		if (type == char.class)
			return (char) 0;
		if (type == byte.class)
			return (byte) 0;
		if (type == short.class)
			return (short) 0;
		if (type == int.class)
			return 0;
		if (type == long.class)
			return 0L;
		if (type == float.class)
			return 0f;
		return 0d;
	}
}
