package ru.beecreeper;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Bee;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public final class ExplosionListener implements Listener {

    private final BeeCreeperManager manager;

    public ExplosionListener(BeeCreeperManager manager) {
        this.manager = manager;
    }

    /**
     * Главное место контроля над разрушением блоков.
     * Приоритет HIGH: после обычных плагинов, но до защиты на HIGHEST/MONITOR.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!manager.isOurs(event.getEntity())) return;

        Settings s = manager.settings();
        BlockMode mode = manager.modeOf(event.getEntity());
        List<Block> blocks = event.blockList();

        if (mode == BlockMode.NONE) {
            blocks.clear();
            return;
        }

        switch (mode) {
            case WHITELIST -> blocks.removeIf(b -> !s.materials().contains(b.getType()));
            case BLACKLIST -> blocks.removeIf(b -> s.materials().contains(b.getType()));
            default -> { /* ALL - ничего не фильтруем */ }
        }

        if (s.protectContainers()) {
            blocks.removeIf(b -> b.getState(false) instanceof Container);
        }

        if (s.maxBlocks() > 0 && blocks.size() > s.maxBlocks()) {
            Location center = event.getLocation();
            blocks.sort((x, y) -> Double.compare(
                    x.getLocation().distanceSquared(center),
                    y.getLocation().distanceSquared(center)));
            blocks.subList(s.maxBlocks(), blocks.size()).clear();
        }

        event.setYield(s.dropChance());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Bee bee && manager.isOurs(bee)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            manager.onBeeDeath(bee);
        }
    }
}
