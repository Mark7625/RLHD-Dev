package rs117.hd.dev.sceneloader.standalone;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.HdPluginConfig;
import rs117.hd.dev.sceneloader.standalone.client.Fake;

@Slf4j
public final class HdSettings {
	private final Map<String, String> values = new ConcurrentHashMap<>();
	private final HdPluginConfig config;

	@Nullable
	private volatile Consumer<String> listener;

	public HdSettings() {
		config = (HdPluginConfig) Proxy.newProxyInstance(
			HdPluginConfig.class.getClassLoader(),
			new Class<?>[] { HdPluginConfig.class },
			this::answer
		);
	}

	public HdPluginConfig asConfig() {
		return config;
	}

	public void whenChanged(Consumer<String> listener) {
		this.listener = listener;
	}

	public void set(String name, String value) {
		values.put(name, value);
	}

	public void change(String name, String key, String value) {
		values.put(name, value);
		var told = listener;
		if (told != null)
			told.accept(key);
	}

	private Object answer(Object proxy, Method method, Object[] args) throws Throwable {
		String asked = values.get(method.getName());
		if (asked != null && method.getParameterCount() == 0) {
			Object value = read(method.getReturnType(), asked);
			if (value != null)
				return value;
			log.warn("Could not read {} as a {}", asked, method.getReturnType().getSimpleName());
		}

		if (method.isDefault())
			return Fake.callDefault(proxy, method, args);

		switch (method.getName()) {
			case "toString":
				if (method.getParameterCount() == 0)
					return "HdPluginConfig(defaults)";
				break;
			case "equals":
				if (method.getParameterCount() == 1)
					return proxy == args[0];
				break;
			case "hashCode":
				if (method.getParameterCount() == 0)
					return System.identityHashCode(proxy);
				break;
			default:
				break;
		}

		return Fake.blank(method.getReturnType());
	}

	@Nullable
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Object read(Class<?> wanted, String value) {
		try {
			if (wanted.isEnum())
				return Enum.valueOf((Class<Enum>) wanted, value.toUpperCase());
			if (wanted == boolean.class || wanted == Boolean.class)
				return Boolean.parseBoolean(value);
			if (wanted == int.class || wanted == Integer.class)
				return Integer.parseInt(value);
			if (wanted == long.class || wanted == Long.class)
				return Long.parseLong(value);
			if (wanted == float.class || wanted == Float.class)
				return Float.parseFloat(value);
			if (wanted == double.class || wanted == Double.class)
				return Double.parseDouble(value);
			if (wanted == String.class)
				return value;
		} catch (IllegalArgumentException ex) {
			return null;
		}
		return null;
	}
}
