package rs117.hd.gui;

import com.google.common.reflect.ClassPath;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComponent;
import lombok.extern.slf4j.Slf4j;

/** Scans the classpath for {@link HdPanel}-annotated classes and registers them automatically. */
@Slf4j
@Singleton
public class HdPanelRegistry
{
	private static final String SCAN_PACKAGE = "rs117.hd.gui";

	@Inject
	private Injector injector;

	@Inject
	private HdDevFrameGroup sidebarGroup;

	private final List<Runnable> teardowns = new ArrayList<>();

	public void registerAll()
	{
		for (Class<?> type : findAnnotatedClasses())
		{
			HdPanel meta = type.getAnnotation(HdPanel.class);
			if (!meta.autoRegister())
				continue;

			if (!HdDevPanel.class.isAssignableFrom(type))
			{
				log.warn("{} is annotated with @HdPanel but doesn't implement HdDevPanel - skipping", type.getName());
				continue;
			}

			HdDevPanel panel = (HdDevPanel) injector.getInstance(type);
			register(meta, panel);
		}
	}

	public void unregisterAll()
	{
		for (Runnable teardown : teardowns)
			teardown.run();
		teardowns.clear();
		sidebarGroup.unregister();
	}

	private void register(HdPanel meta, HdDevPanel panel)
	{
		if (meta.value() == DockType.SIDEBAR)
		{
			JComponent content = panel.buildContent();
			sidebarGroup.addTab(meta.name(), content);
			panel.onRegister();
			teardowns.add(() -> {
				panel.onUnregister();
				sidebarGroup.removeTab(content);
			});
			return;
		}

		HdPanelFrame frame = new HdPanelFrame(meta, panel);
		injector.injectMembers(frame);
		frame.register();
		if (meta.value() == DockType.WINDOW)
			frame.open();
		teardowns.add(frame::unregister);
	}

	private List<Class<?>> findAnnotatedClasses()
	{
		List<Class<?>> found = new ArrayList<>();
		try
		{
			ClassPath classPath = ClassPath.from(getClass().getClassLoader());
			for (ClassPath.ClassInfo info : classPath.getTopLevelClassesRecursive(SCAN_PACKAGE))
			{
				Class<?> type;
				try
				{
					type = info.load();
				}
				catch (LinkageError e)
				{
					continue;
				}
				if (type.isAnnotationPresent(HdPanel.class))
					found.add(type);
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to scan for @HdPanel-annotated dev-tools panels", e);
		}
		return found;
	}
}
