package ru.beecreeper;

import org.bukkit.plugin.java.JavaPlugin;

public final class BeeCreeperPlugin extends JavaPlugin {

    private BeeCreeperManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new BeeCreeperManager(this);

        getServer().getPluginManager().registerEvents(new ExplosionListener(manager), this);

        BeeCreeperCommand command = new BeeCreeperCommand(this, manager);
        var pluginCommand = getCommand("beecreeper");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        manager.start();
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public void reload() {
        reloadConfig();
        manager.reloadSettings();
    }

    public BeeCreeperManager manager() {
        return manager;
    }
}
