package ru.beecreeper;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;

/** Неизменяемый снимок настроек из config.yml. */
public record Settings(
        double beeHealth,
        double flyingSpeed,
        double speedMultiplier,
        boolean creeperPowered,
        boolean creeperInvulnerable,
        boolean glowing,
        boolean creeperDiesWithBee,
        boolean explodeOnBeeDeath,
        double triggerDistance,
        int lifetimeTicks,
        boolean explodeOnTimeout,
        double spawnDistance,
        double spawnHeight,
        int maxPerCommand,
        float power,
        boolean setFire,
        BlockMode blockMode,
        Set<Material> materials,
        int maxBlocks,
        boolean protectContainers,
        float dropChance
) {
    public static Settings load(FileConfiguration c) {
        Set<Material> mats = EnumSet.noneOf(Material.class);
        for (String name : c.getStringList("explosion.blocks.materials")) {
            Material m = Material.matchMaterial(name);
            if (m != null && m.isBlock()) mats.add(m);
        }
        return new Settings(
                c.getDouble("creature.bee-health", 8.0),
                c.getDouble("creature.flying-speed", 0.35),
                c.getDouble("creature.speed-multiplier", 1.6),
                c.getBoolean("creature.creeper-powered", false),
                c.getBoolean("creature.creeper-invulnerable", true),
                c.getBoolean("creature.glowing", false),
                c.getBoolean("creature.creeper-dies-with-bee", true),
                c.getBoolean("creature.explode-on-bee-death", false),
                Math.max(0.5, c.getDouble("behavior.trigger-distance", 2.0)),
                Math.max(20, c.getInt("behavior.lifetime-seconds", 30) * 20),
                "EXPLODE".equalsIgnoreCase(c.getString("behavior.on-timeout", "REMOVE")),
                Math.max(4.0, c.getDouble("behavior.spawn-distance", 16.0)),
                c.getDouble("behavior.spawn-height", 4.0),
                Math.max(1, c.getInt("behavior.max-per-command", 20)),
                (float) c.getDouble("explosion.power", 3.0),
                c.getBoolean("explosion.set-fire", false),
                BlockMode.parse(c.getString("explosion.blocks.mode"), BlockMode.NONE),
                mats,
                Math.max(0, c.getInt("explosion.blocks.max-blocks", 0)),
                c.getBoolean("explosion.blocks.protect-containers", true),
                (float) Math.max(0.0, Math.min(1.0, c.getDouble("explosion.blocks.drop-chance", 1.0)))
        );
    }
}
