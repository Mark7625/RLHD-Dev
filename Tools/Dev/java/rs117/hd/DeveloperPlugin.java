package rs117.hd;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import rs117.hd.gui.HdPanelRegistry;

@Slf4j
@Singleton
@PluginDescriptor(
	name = "117 HD Developer",
	description = "development and profiling tools for 117 HD",
	tags = {"117", "hd", "developer", "development", "profiler"}
)
@PluginDependency(HdPlugin.class)
public class DeveloperPlugin extends Plugin {

	@Inject
	private HdPanelRegistry panelRegistry;

	@Provides
	DeveloperConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(DeveloperConfig.class);
	}

	@Override
	protected void startUp() {
		panelRegistry.registerAll();
	}

	@Override
	protected void shutDown() {
		panelRegistry.unregisterAll();
	}
}
