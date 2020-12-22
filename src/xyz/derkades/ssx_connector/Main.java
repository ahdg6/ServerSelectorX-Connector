package xyz.derkades.ssx_connector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xyz.derkades.derkutils.caching.Cache;

public class Main extends JavaPlugin {

	static Main instance;

	/*
	 * Used for measuring the amount of placeholders collected
	 */
	static int placeholdersUncached = 0;
	static int placeholdersCached = 0;
	static int sendAmount = 0;
	
	static boolean cacheEnabled = true;

	final File addonsFolder = new File(this.getDataFolder(), "addons");

	Map<String, Addon> addons = new HashMap<>();

	private BukkitTask pingTask = null;

	@Override
	public void onEnable() {
		instance = this;

		super.saveDefaultConfig();

		this.addonsFolder.mkdir();

		this.loadAddons();

		this.getCommand("ssxc").setExecutor(new ConnectorCommand());

		restartPingTask();

		registerMetrics();
		
		getServer().getScheduler().runTaskTimer(this, () -> {
			Cache.cleanCache();
		}, 60*60*20, 60*60*20);
	}

	void loadAddons() {
		PlaceholderRegistry.clear();
		
		final File addonsFolder = new File(this.getDataFolder() + File.separator + "addons");

		addonsFolder.mkdirs();
		
		final Set<String> newlyLoadedAddons = new HashSet<>();
		for (final File addonFile : addonsFolder.listFiles()) {
			if (addonFile.isDirectory()) {
				this.getLogger().warning("Skipped directory " + addonFile.getPath() + " in addons directory. There should not be any directories in the addon directory.");
				continue;
			}
			
			if (!addonFile.getName().endsWith(".class")) {
				if (!addonFile.getName().endsWith(".yml")) {
					this.getLogger().warning("The file " + addonFile.getAbsolutePath() + " does not belong in the addons folder.");
				}
				continue;
			}
			
			final String addonName = addonFile.getName().replace(".class", "");
			
			Addon addon = this.addons.get(addonName);

			if (addon == null) {
				this.getLogger().info("Loading addon " + addonName);
				try (URLClassLoader loader = new URLClassLoader(new URL[]{addonsFolder.toURI().toURL()}, this.getClassLoader())){
					final Class<?> clazz = loader.loadClass(addonFile.getName().replace(".class", ""));
					addon = (Addon) clazz.getConstructor().newInstance();
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IOException |
						IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
					continue;
				}
			}
			
			if (!addon.getName().equals(addonName)) {
				this.getLogger().severe(String.format("Addon class name (%s) does not match class file name (%s)", addon.getName(), addonName));
				continue;
			}

			addon.reloadConfig();
			addon.onLoad();
			
			this.addons.put(addonName, addon);
			newlyLoadedAddons.add(addonName);
		}
		
		// Check if any addons were removed
		final Stack<String> toRemove = new Stack();
		for (final String addonName : this.addons.keySet()) {
			if (!newlyLoadedAddons.contains(addonName)) {
				this.getLogger().info("Uninstalling addon " + addonName);
				toRemove.add(addonName);
			}
		}
		while (!toRemove.isEmpty()) {
			this.addons.remove(toRemove.pop());
		}
		
		registerCorePlaceholders();
	}

	void registerCorePlaceholders() {
		PlaceholderRegistry.registerPlaceholder(Optional.empty(), "online",
				() -> String.valueOf(Bukkit.getOnlinePlayers().size()));

		PlaceholderRegistry.registerPlaceholder(Optional.empty(), "max",
				() -> String.valueOf(Bukkit.getMaxPlayers()));
	}

	void restartPingTask() {
		if (this.pingTask != null) {
			this.pingTask.cancel();
		}

		final int addresses = getConfig().getStringList("addresses").size();
		final int interval = getConfig().getInt("send-interval");
		final int taskIntervalTicks = (interval * 20) / addresses;
	
		this.pingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new PlaceholderSender(), 40, taskIntervalTicks);
	}
	
	@Override
	public void reloadConfig() {
		super.reloadConfig();
		cacheEnabled = getConfig().getBoolean("enable-caching", true);
	}

	private void registerMetrics() {
		final Metrics metrics = new Metrics(this, 3000);

		metrics.addCustomChart(new Metrics.SimplePie("data_send_interval", () -> this.getConfig().getInt("send-interval", 4) + ""));

		metrics.addCustomChart(new Metrics.SimplePie("hub_servers", () ->
			this.getConfig().getStringList("addresses").size() + ""));

		metrics.addCustomChart(new Metrics.SimplePie("default_password", () ->
			this.getConfig().getString("password").equals("a") + ""));

		metrics.addCustomChart(new Metrics.AdvancedPie("addons", () -> {
			final Map<String, Integer> map = new HashMap<>();
			this.addons.forEach((ign, a) -> map.put(a.getName(), 1));
			return map;
		}));
	}

}
