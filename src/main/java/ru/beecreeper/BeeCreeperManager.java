package ru.beecreeper;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class BeeCreeperManager {

    /** Как часто (в тиках) обновляется логика преследования. */
    private static final int STEP = 2;

    private final BeeCreeperPlugin plugin;
    private final NamespacedKey markKey;
    private final NamespacedKey modeKey;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Random random = new Random();

    private Settings settings;
    private BukkitTask task;

    private static final class Active {
        final Bee bee;
        final Creeper creeper;
        final UUID target;
        final BlockMode modeOverride; // null = брать из конфига
        int age;
        boolean primedSoundPlayed;
        Location lastPos;
        int stuckTicks;

        Active(Bee bee, Creeper creeper, UUID target, BlockMode modeOverride) {
            this.bee = bee;
            this.creeper = creeper;
            this.target = target;
            this.modeOverride = modeOverride;
        }
    }

    public BeeCreeperManager(BeeCreeperPlugin plugin) {
        this.plugin = plugin;
        this.markKey = new NamespacedKey(plugin, "beecreeper");
        this.modeKey = new NamespacedKey(plugin, "mode");
        this.settings = Settings.load(plugin.getConfig());
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, STEP, STEP);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        clearAll();
    }

    public void reloadSettings() {
        this.settings = Settings.load(plugin.getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public int activeCount() {
        return active.size();
    }

    // ------------------------------------------------------------------ spawn

    /**
     * Спавнит крипера на пчеле, который полетит к игроку.
     *
     * @param modeOverride режим блоков для этого конкретного взрыва или null (из конфига)
     * @return true, если получилось заспавнить
     */
    public boolean spawn(Player target, BlockMode modeOverride) {
        Location loc = findSpawnLocation(target);
        World world = loc.getWorld();

        Bee bee = world.spawn(loc, Bee.class, b -> {
            b.setPersistent(false);
            b.setRemoveWhenFarAway(true);
            b.setAnger(0);
            b.setHasNectar(false);
            b.setCannotEnterHiveTicks(Integer.MAX_VALUE / 2);
            b.setGlowing(settings.glowing());
            b.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);

            AttributeInstance hp = b.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(settings.beeHealth());
            b.setHealth(Math.min(settings.beeHealth(), b.getHealth() > 0 ? settings.beeHealth() : 1));

            AttributeInstance fly = b.getAttribute(Attribute.FLYING_SPEED);
            if (fly != null) fly.setBaseValue(settings.flyingSpeed());

            // Убираем ванильные цели (поиск цветов, улей, атака) - летаем только по нашей логике
            Bukkit.getMobGoals().removeAllGoals(b);
        });
        if (!bee.isValid()) return false; // спавн отменил другой плагин

        Creeper creeper = world.spawn(loc, Creeper.class, c -> {
            c.setPersistent(false);
            c.setAI(false);
            c.setPowered(settings.creeperPowered());
            c.setInvulnerable(settings.creeperInvulnerable());
            c.setGlowing(settings.glowing());
            c.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);
            if (modeOverride != null) {
                c.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, modeOverride.name());
            }
        });
        if (!creeper.isValid()) {
            bee.remove();
            return false;
        }

        bee.addPassenger(creeper);
        active.put(bee.getUniqueId(), new Active(bee, creeper, target.getUniqueId(), modeOverride));
        return true;
    }

    private Location findSpawnLocation(Player target) {
        Location base = target.getLocation();
        World world = base.getWorld();
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = settings.spawnDistance() * (0.7 + random.nextDouble() * 0.3);
            Location l = base.clone().add(Math.cos(angle) * dist, settings.spawnHeight() + random.nextInt(3), Math.sin(angle) * dist);
            if (l.getY() >= world.getMaxHeight() - 2) l.setY(world.getMaxHeight() - 3);
            if (!l.isChunkLoaded()) continue;
            if (l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable()) {
                return l;
            }
        }
        // не нашли свободное место - над головой игрока
        return base.clone().add(0, Math.max(2.5, settings.spawnHeight()), 0);
    }

    // ------------------------------------------------------------------- tick

    private void tickAll() {
        if (active.isEmpty()) return;
        for (Active a : new ArrayList<>(active.values())) {
            if (!active.containsKey(a.bee.getUniqueId())) continue; // уже обработан

            if (!a.bee.isValid()) { // чанк выгрузился / пчелу удалили
                discard(a);
                continue;
            }
            if (!a.creeper.isValid()) {
                discard(a);
                continue;
            }
            // на всякий случай: если крипер слез с пчелы
            if (!a.bee.getPassengers().contains(a.creeper)) {
                a.bee.addPassenger(a.creeper);
            }

            Player p = Bukkit.getPlayer(a.target);
            if (p == null || !p.isOnline() || p.isDead() || !p.getWorld().equals(a.bee.getWorld())) {
                discard(a);
                continue;
            }

            a.age += STEP;
            if (a.age >= settings.lifetimeTicks()) {
                if (settings.explodeOnTimeout()) detonate(a);
                else discard(a);
                continue;
            }

            Location goal = p.getLocation().add(0, p.getHeight() * 0.6, 0);
            Location beeLoc = a.bee.getLocation();
            double dist = beeLoc.distance(goal);

            if (dist <= settings.triggerDistance()) {
                detonate(a);
                continue;
            }
            if (dist <= settings.triggerDistance() + 4 && !a.primedSoundPlayed) {
                a.primedSoundPlayed = true;
                beeLoc.getWorld().playSound(beeLoc, Sound.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.5f, 1.0f);
            }
            chase(a, goal, dist);
        }
    }

    private void chase(Active a, Location goal, double dist) {
        Bee bee = a.bee;
        Pathfinder pf = bee.getPathfinder();
        boolean ok = pf.moveTo(goal, settings.speedMultiplier());

        // Детект застревания: почти не сдвинулись за ~10 тиков
        Location now = bee.getLocation();
        if (a.lastPos != null && a.lastPos.getWorld().equals(now.getWorld()) && a.lastPos.distanceSquared(now) < 0.01) {
            a.stuckTicks += STEP;
        } else {
            a.stuckTicks = 0;
        }
        a.lastPos = now;

        // Запасной вариант: путь не построился или пчела застряла - толкаем напрямую
        if (!ok || a.stuckTicks >= 10) {
            Vector dir = goal.toVector().subtract(now.toVector());
            if (dir.lengthSquared() > 1.0E-4) {
                dir.normalize().multiply(Math.min(0.45, 0.15 * settings.speedMultiplier()));
                bee.setVelocity(dir);
            }
        }
    }

    // ----------------------------------------------------------------- finish

    private void detonate(Active a) {
        if (active.remove(a.bee.getUniqueId()) == null) return;

        Location loc = a.creeper.isValid() ? a.creeper.getLocation() : a.bee.getLocation();
        boolean breakBlocks = effectiveMode(a.modeOverride) != BlockMode.NONE;

        // Источник - крипер: сработает EntityExplodeEvent, где ExplosionListener отфильтрует блоки
        loc.getWorld().createExplosion(loc, settings.power(), settings.setFire(), breakBlocks, a.creeper);

        a.creeper.remove();
        a.bee.remove();
    }

    private void discard(Active a) {
        active.remove(a.bee.getUniqueId());
        a.creeper.remove();
        a.bee.remove();
    }

    /** Вызывается, когда пчелу убили (игрок, урон и т.д.). */
    public void onBeeDeath(Bee bee) {
        Active a = active.get(bee.getUniqueId());
        if (a == null) return;
        if (settings.explodeOnBeeDeath()) {
            // крипер ещё жив - взорвём, пока не удалили
            if (a.creeper.isValid()) {
                active.remove(bee.getUniqueId());
                Location loc = a.creeper.getLocation();
                boolean breakBlocks = effectiveMode(a.modeOverride) != BlockMode.NONE;
                loc.getWorld().createExplosion(loc, settings.power(), settings.setFire(), breakBlocks, a.creeper);
                a.creeper.remove();
                return;
            }
        }
        if (settings.creeperDiesWithBee()) {
            discard(a);
        } else {
            // крипер остаётся обычным (вернём ему ИИ)
            active.remove(bee.getUniqueId());
            if (a.creeper.isValid()) {
                a.creeper.setAI(true);
                a.creeper.setInvulnerable(false);
                a.creeper.getPersistentDataContainer().remove(markKey);
            }
        }
    }

    public void clearAll() {
        for (Active a : new ArrayList<>(active.values())) discard(a);
        active.clear();
    }

    // -------------------------------------------------------------- utilities

    public boolean isOurs(Entity e) {
        return e.getPersistentDataContainer().has(markKey, PersistentDataType.BYTE);
    }

    /** Режим блоков для конкретного крипера (с учётом переопределения из команды). */
    public BlockMode modeOf(Entity creeper) {
        String raw = creeper.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        return effectiveMode(raw == null ? null : BlockMode.parse(raw, null));
    }

    private BlockMode effectiveMode(BlockMode override) {
        return override != null ? override : settings.blockMode();
    }
}
