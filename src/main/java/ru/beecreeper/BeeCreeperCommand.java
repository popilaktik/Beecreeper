package ru.beecreeper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BeeCreeperCommand implements TabExecutor {

    private final BeeCreeperPlugin plugin;
    private final BeeCreeperManager manager;

    public BeeCreeperCommand(BeeCreeperPlugin plugin, BeeCreeperManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(sender, label, args);
            case "clear" -> {
                int n = manager.activeCount();
                manager.clearAll();
                msg(sender, "Удалено криперов на пчёлах: " + n, NamedTextColor.GREEN);
            }
            case "reload" -> {
                if (!sender.hasPermission("beecreeper.reload")) {
                    msg(sender, "Нет прав.", NamedTextColor.RED);
                    return true;
                }
                plugin.reload();
                msg(sender, "Конфиг перезагружен.", NamedTextColor.GREEN);
            }
            default -> usage(sender, label);
        }
        return true;
    }

    private void spawn(CommandSender sender, String label, String[] args) {
        // /bc spawn [игрок] [кол-во] [none|all|config]
        Player target = null;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg(sender, "Игрок не найден: " + args[1], NamedTextColor.RED);
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            msg(sender, "Укажи игрока: /" + label + " spawn <игрок> [кол-во] [none|all|config]", NamedTextColor.RED);
            return;
        }

        int count = 1;
        if (args.length >= 3) {
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                msg(sender, "Количество должно быть числом.", NamedTextColor.RED);
                return;
            }
        }
        count = Math.max(1, Math.min(count, manager.settings().maxPerCommand()));

        BlockMode override = null; // из конфига
        if (args.length >= 4) {
            switch (args[3].toLowerCase(Locale.ROOT)) {
                case "none" -> override = BlockMode.NONE;
                case "all" -> override = BlockMode.ALL;
                case "config" -> override = null;
                default -> {
                    msg(sender, "Режим блоков: none | all | config", NamedTextColor.RED);
                    return;
                }
            }
        }

        int ok = 0;
        for (int i = 0; i < count; i++) {
            if (manager.spawn(target, override)) ok++;
        }
        msg(sender, "Заспавнено: " + ok + "/" + count + " для " + target.getName(),
                ok > 0 ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private void usage(CommandSender sender, String label) {
        msg(sender, "/" + label + " spawn [игрок] [кол-во] [none|all|config]", NamedTextColor.YELLOW);
        msg(sender, "/" + label + " clear", NamedTextColor.YELLOW);
        msg(sender, "/" + label + " reload", NamedTextColor.YELLOW);
    }

    private void msg(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("spawn", "clear", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            out.addAll(List.of("1", "3", "5"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("spawn")) {
            out.addAll(List.of("none", "all", "config"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
