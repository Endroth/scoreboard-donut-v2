package br.com.scoreboard.donut;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.*;

public final class DonutStyleScoreboard extends JavaPlugin implements Listener {
    private final Map<UUID, Map<String, Boolean>> linePrefs = new HashMap<>();
    private final Map<UUID, Map<String, Boolean>> labelPrefs = new HashMap<>();
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private Object economy;
    private Method balanceMethod;
    private final List<String> lineIds = List.of("kills", "deaths", "playtime", "balance", "online");

    @Override public void onEnable() {
        saveDefaultConfig();
        loadPlayerData();
        setupEconomyReflectively();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) createBoard(p);
        long interval = Math.max(20L, getConfig().getLong("update-interval-ticks", 40L));
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!disabledPlayers.contains(p.getUniqueId())) updateBoard(p);
            }
        }, 1L, interval);
        getLogger().info("DonutStyleScoreboard ativado.");
    }

    @Override public void onDisable() { savePlayerData(); }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!disabledPlayers.contains(e.getPlayer().getUniqueId())) createBoard(e.getPlayer());
        }, 2L);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { }

    private void setupEconomyReflectively() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (rsp != null) {
                economy = rsp.getProvider();
                balanceMethod = economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            }
        } catch (Throwable ignored) {
            // Vault é opcional; o plugin continua funcionando sem economia.
            economy = null;
        }
    }

    private void createBoard(Player p) {
        if (disabledPlayers.contains(p.getUniqueId())) return;
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("donutstyle", "dummy", color(getConfig().getString("title", "&6&lSERVER")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        p.setScoreboard(board);
        updateBoard(p);
    }

    private void updateBoard(Player p) {
        if (disabledPlayers.contains(p.getUniqueId())) return;
        Scoreboard board = p.getScoreboard();
        Objective obj = board.getObjective("donutstyle");
        if (obj == null) { createBoard(p); return; }

        // Remove apenas as equipes/entradas deste plugin para evitar duplicações.
        for (String id : lineIds) {
            Team team = board.getTeam("dsb_" + id);
            if (team != null) team.unregister();
        }
        int score = lineIds.size();
        for (String id : lineIds) {
            if (!isLineEnabled(p, id)) continue;
            String value = valueFor(p, id);
            String rendered = renderLine(p, id, value);
            if (rendered.isBlank()) { score--; continue; }
            String entry = ChatColor.values()[Math.min(score, ChatColor.values().length - 1)].toString() + ChatColor.RESET;
            Team team = board.registerNewTeam("dsb_" + id);
            team.addEntry(entry);
            team.setPrefix(limit(rendered, 64));
            obj.getScore(entry).setScore(score--);
        }
    }

    private String renderLine(Player p, String id, String value) {
        String icon = getConfig().getString("lines." + id + ".icon", "");
        String label = getConfig().getString("lines." + id + ".label", "");
        boolean showLabel = labelPrefs.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(id, true);
        String format = getConfig().getString("lines." + id + ".format", "%value%");
        if (!showLabel) {
            // Mantém somente o ícone e o valor, sem a palavra/label.
            return color(icon + " " + value);
        }
        return color(format.replace("%value%", value).replace("%icon%", icon).replace("%label%", label));
    }

    private String valueFor(Player p, String id) {
        return switch (id) {
            case "kills" -> Integer.toString(p.getStatistic(Statistic.PLAYER_KILLS));
            case "deaths" -> Integer.toString(p.getStatistic(Statistic.DEATHS));
            case "playtime" -> formatTicks(p.getStatistic(Statistic.PLAY_ONE_MINUTE));
            case "balance" -> getBalance(p);
            case "online" -> Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers();
            default -> "";
        };
    }

    private String getBalance(Player p) {
        if (economy != null && balanceMethod != null) {
            try { return new DecimalFormat("#,##0.##").format(((Number) balanceMethod.invoke(economy, p)).doubleValue()); }
            catch (Exception ignored) { }
        }
        return "N/D";
    }

    private String formatTicks(int ticks) {
        long seconds = ticks / 20L;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    private boolean isLineEnabled(Player p, String id) {
        boolean global = getConfig().getBoolean("lines." + id + ".enabled", true);
        return global && linePrefs.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(id, true);
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private String limit(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }

    private void loadPlayerData() {
        var data = getDataFolder().toPath().resolve("players.yml").toFile();
        if (!data.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(data);
        for (String key : y.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
            if (y.getBoolean(key + ".hidden", false)) disabledPlayers.add(uuid);
            for (String id : lineIds) {
                if (y.contains(key + ".lines." + id)) linePrefs.computeIfAbsent(uuid, k -> new HashMap<>()).put(id, y.getBoolean(key + ".lines." + id));
                if (y.contains(key + ".labels." + id)) labelPrefs.computeIfAbsent(uuid, k -> new HashMap<>()).put(id, y.getBoolean(key + ".labels." + id));
            }
        }
    }

    private void savePlayerData() {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        Set<UUID> all = new HashSet<>(disabledPlayers);
        all.addAll(linePrefs.keySet()); all.addAll(labelPrefs.keySet());
        for (UUID uuid : all) {
            y.set(uuid + ".hidden", disabledPlayers.contains(uuid));
            linePrefs.getOrDefault(uuid, Map.of()).forEach((k,v) -> y.set(uuid + ".lines." + k, v));
            labelPrefs.getOrDefault(uuid, Map.of()).forEach((k,v) -> y.set(uuid + ".labels." + k, v));
        }
        try { y.save(getDataFolder().toPath().resolve("players.yml").toFile()); }
        catch (Exception ex) { getLogger().warning("Não foi possível salvar players.yml: " + ex.getMessage()); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&6&lDonutStyleScoreboard &7- comandos:"));
            sender.sendMessage(color("&e/dsb toggle &7- liga/desliga sua scoreboard"));
            sender.sendMessage(color("&e/dsb line <id> <on|off> &7- mostra/oculta uma linha"));
            sender.sendMessage(color("&e/dsb label <id> <on|off> &7- mostra/oculta a palavra"));
            sender.sendMessage(color("&e/dsb reload &7- recarrega config (OP)"));
            sender.sendMessage(color("&7Linhas: &f" + String.join(", ", lineIds)));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dsb.admin")) { sender.sendMessage(color("&cSem permissão.")); return true; }
            reloadConfig(); setupEconomyReflectively();
            for (Player p : Bukkit.getOnlinePlayers()) updateBoard(p);
            sender.sendMessage(color("&aConfiguração recarregada.")); return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Este comando deve ser usado por um jogador."); return true; }
        UUID uuid = p.getUniqueId();
        if (args[0].equalsIgnoreCase("toggle")) {
            if (disabledPlayers.remove(uuid)) { createBoard(p); p.sendMessage(color("&aScoreboard ativada.")); }
            else { disabledPlayers.add(uuid); p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); p.sendMessage(color("&cScoreboard desativada.")); }
            savePlayerData(); return true;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("line") || args[0].equalsIgnoreCase("label"))) {
            String id = args[1].toLowerCase(Locale.ROOT);
            if (!lineIds.contains(id)) { p.sendMessage(color("&cLinha inválida. Use: " + String.join(", ", lineIds))); return true; }
            boolean on = args[2].equalsIgnoreCase("on");
            if (!on && !args[2].equalsIgnoreCase("off")) { p.sendMessage(color("&cUse on ou off.")); return true; }
            Map<UUID, Map<String, Boolean>> target = args[0].equalsIgnoreCase("line") ? linePrefs : labelPrefs;
            target.computeIfAbsent(uuid, k -> new HashMap<>()).put(id, on);
            updateBoard(p); savePlayerData();
            p.sendMessage(color("&a" + id + (args[0].equalsIgnoreCase("line") ? " linha " : " palavra ") + (on ? "ativada." : "desativada.")));
            return true;
        }
        p.sendMessage(color("&cUso: /dsb, /dsb toggle, /dsb line <id> <on|off>, /dsb label <id> <on|off>, /dsb reload"));
        return true;
    }
}
