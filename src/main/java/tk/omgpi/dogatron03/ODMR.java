package tk.omgpi.dogatron03;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import tk.omgpi.OMGPI;
import tk.omgpi.files.Mapfig;
import tk.omgpi.game.Game;
import tk.omgpi.game.GameState;
import tk.omgpi.game.OMGPlayer;
import tk.omgpi.game.OMGTeam;
import tk.omgpi.utils.Coordinates;
import tk.omgpi.utils.MovingTwinkle;
import tk.omgpi.utils.ObjectiveBuffer;

import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.ChatColor.*;

public class ODMR extends Game {

    public static OMGTeam a, b;

    public void onEnable() {
        name = "ODMR";
        super.onEnable();
        a = new OMGTeam("a", BLUE + "Team A", BLUE + "", false, GameMode.SURVIVAL);
        b = new OMGTeam("b", RED + "Team B", RED + "", false, GameMode.SURVIVAL);
        settings.allowIngameJoin = true;
    }

    public Location player_spawnLocation(OMGPlayer p) {
        double[] coords = Coordinates.parse(loadedMap.mapfig.getString("teams." + p.team.id + ".spawn"), Coordinates.CoordinateType.ROTATION);
        return new Location(OMGPI.gameworld.bukkit, coords[0], coords[1], coords[2], (float) (coords.length > 3 ? coords[3] : 0), (float) (coords.length > 3 ? coords[4] : 0));
    }

    public void event_team_creation(OMGTeam team) {
        if (team.id.equals("default")) team.gameMode = GameMode.SURVIVAL;
    }

    public void event_preMapfigSave(Mapfig m) {
        if (!m.contains("teams.a")) m.set("teams.a.spawn", "0, 0, 0, 0, 0");
        if (!m.contains("teams.b")) m.set("teams.b.spawn", "0, 0, 0, 0, 0");
        if (!m.contains("monuments")) m.set("monuments", new String[]{"a;Core;0,0,0"});
    }


    public void event_game_preStart() {
        List<Monument> mons = new LinkedList<>();
        loadedMap.mapfig.getStringList("monuments").forEach(mt -> {
            Monument psd = Monument.parseMon(mt);
            if (mons.stream().anyMatch(m -> m.owner == psd.owner && m.group.equals(psd.group)))
                mons.stream().filter(m -> m.owner == psd.owner && m.group.equals(psd.group)).forEach(m -> psd.broken.keySet().stream().filter(m1 -> m.broken.keySet().stream().noneMatch(mm -> mm[0] == m1[0] && mm[1] == m1[1] && mm[2] == m1[2])).forEach(m1 -> m.broken.put(m1, false)));
            else mons.add(psd);
        });
        a.hashdata_set("dualmr", false);
        a.hashdata_set("mr_mons", new LinkedList<Monument>() {{
            addAll(mons.stream().filter(m -> m.owner == a).collect(Collectors.toList()));
        }});
        b.hashdata_set("dualmr", false);
        b.hashdata_set("mr_mons", new LinkedList<Monument>() {{
            addAll(mons.stream().filter(m -> m.owner == b).collect(Collectors.toList()));
        }});
    }

    public void update() {
        super.update();
        if (state == GameState.INGAME)
            OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).forEach(t -> ((List<Monument>) t.hashdata_get("mr_mons")).forEach(m -> m.canattack--));
    }

    @EventHandler
    public void onPush(BlockPistonExtendEvent e) {
        for (Block block : e.getBlocks())
            if (OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).stream().anyMatch(t -> ((List<Monument>) t.hashdata_get("mr_mons")).stream().anyMatch(m -> m.broken.keySet().stream().anyMatch(cds -> cds[0] == block.getX() && cds[1] == block.getY() && cds[2] == block.getZ()))) || state != GameState.INGAME)
                e.setCancelled(true);
    }

    @EventHandler
    public void onPush(BlockPistonRetractEvent e) {
        for (Block block : e.getBlocks())
            if (OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).stream().anyMatch(t -> ((List<Monument>) t.hashdata_get("mr_mons")).stream().anyMatch(m -> m.broken.keySet().stream().anyMatch(cds -> cds[0] == block.getX() && cds[1] == block.getY() && cds[2] == block.getZ()))) || state != GameState.INGAME)
                e.setCancelled(true);
    }

    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam && t != OMGPlayer.get(e.getPlayer()).team).forEach(t -> ((List<Monument>) t.hashdata_get("mr_mons")).forEach(m -> new ArrayList<>(m.broken.keySet()).stream().filter(cds -> cds[0] == e.getClickedBlock().getX() && cds[1] == e.getClickedBlock().getY() && cds[2] == e.getClickedBlock().getZ()).forEach(cds -> {
                if (!m.broken.values().stream().allMatch(b -> b) && m.canattack <= 0) {
                    broadcast(DARK_GREEN + t.displayName + GOLD + " " + m.group + DARK_GREEN + " monument is under attack!");
                    OMGPlayer.link.values.forEach(p -> p.bukkit.playSound(p.bukkit.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1, 3));
                }
                m.canattack = 200;
            })));
        }
    }


    public void game_game_checkForEnd() {
        if (state == GameState.INGAME) {
            ObjectiveBuffer buf = new ObjectiveBuffer();
            OMGTeam.getFiltered(x -> x != spectatorTeam && x != defaultTeam).forEach(x -> {
                buf.lines.add("");
                buf.lines.add(otherTeam(x).displayName);
                List<Monument> mons = (List<Monument>) x.hashdata_get("mr_mons");
                mons.forEach(y -> {
                    long broke = y.broken.values().stream().filter(b -> b).count();
                    int all = y.broken.size();
                    int perc = (int) (broke * 100 / all);
                    if (all == 1 && perc != 100) buf.lines.add(RED + "✕ " + RESET + y.group);
                    else if (perc <= 33) buf.lines.add(RED + "" + perc + "% " + RESET + y.group);
                    else if (perc <= 66) buf.lines.add(GOLD + "" + perc + "% " + RESET + y.group);
                    else if (perc <= 99)
                        buf.lines.add(DARK_GREEN + "" + perc + "% " + RESET + y.group);
                    else buf.lines.add(GREEN + "✔ " + RESET + y.group);

                });
            });
            OMGPlayer.link.values.forEach(x -> buf.loadInto(x.displayObjective));
            OMGTeam.registeredTeams.stream().filter(t -> t != defaultTeam && t != spectatorTeam && (((List<Monument>) t.hashdata_get("mr_mons")).stream().allMatch(m -> m.broken.values().stream().allMatch(b -> b)) || t.size() < 1)).forEach(t -> {
                t.hashdata_set("dualmr", true);
                OMGPlayer.getFiltered(p -> p.team == t).forEach(p -> p.setTeam(spectatorTeam));
            });
            if (OMGTeam.registeredTeams.stream().filter(t -> t != defaultTeam && t != spectatorTeam && !(boolean) t.hashdata_get("dualmr")).count() < 2 || (OMGPlayer.getFiltered(p -> p.team != spectatorTeam).size() < 2)) {
                broadcast_win();
                game_stop();
            }
        }
    }

    public String game_winMessage() {
        if (!OMGTeam.somebodyWonOrEveryoneLost()) {
            Optional<OMGTeam> o = OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).stream().filter(t -> !(boolean) t.hashdata_get("dualmr")).findFirst();
            return o.map(OMGTeam -> OMGTeam.displayName + " won!").orElse("Nobody won!");
        }
        return super.game_winMessage();
    }

    public void timer_tick() {
        long t = settings.gameLength - timerTicks;
        if (t > 599) {
            Bukkit.getOnlinePlayers().forEach(p -> p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 1000000, (int) (t / 600 - 1), true, true)));
            if (t % 600 == 0)
                OMGPlayer.link.values.forEach(p -> p.bukkit.sendTitle(YELLOW + "Haste " + t / 600, "", 0, 50, 0));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        OMGPlayer x = OMGPlayer.get(e.getPlayer());
        if (OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).stream().noneMatch(t -> ((List<Monument>) t.hashdata_get("mr_mons")).stream().anyMatch(m -> m.broken.keySet().stream().anyMatch(cds -> cds[0] == e.getBlock().getX() && cds[1] == e.getBlock().getY() && cds[2] == e.getBlock().getZ()))) || state != GameState.INGAME)
            return;
        OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).forEach(t -> ((List<Monument>) t.hashdata_get("mr_mons")).forEach(m -> new ArrayList<>(m.broken.keySet()).stream().filter(cds -> cds[0] == e.getBlock().getX() && cds[1] == e.getBlock().getY() && cds[2] == e.getBlock().getZ()).forEach(cds -> {
            if (t == x.team) {
                e.setCancelled(true);
                return;
            }
            m.broken.put(cds, true);
            if (m.broken.values().stream().allMatch(b -> b)) {
                MovingTwinkle.twinkles.remove(m.pl);
                broadcast(DARK_GREEN + x.team.displayName + " (" + x.bukkit.getName() + ")" + DARK_GREEN + " has destroyed" + GOLD + " " + m.group + DARK_GREEN + " monument!");
                OMGPlayer.link.values.forEach(p -> {
                    p.play_sound_roar();
                    p.bukkit.sendTitle(t.displayName + GOLD + " " + m.group + DARK_GREEN + " destroyed!", "", 0, 50, 0);
                });
            }
            e.setCancelled(true);
            e.getBlock().setType(Material.AIR);
        })));
        game_game_checkForEnd();
    }

    @EventHandler
    public void onBurn(BlockBurnEvent e) {
        if (OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).stream().noneMatch(t -> ((List<Monument>) t.hashdata_get("mr_mons")).stream().anyMatch(m -> m.broken.keySet().stream().anyMatch(cds -> cds[0] == e.getBlock().getX() && cds[1] == e.getBlock().getY() && cds[2] == e.getBlock().getZ()))) || state != GameState.INGAME)
            return;
        OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).forEach(t -> ((List<Monument>) t.hashdata_get("mr_mons")).forEach(m -> new ArrayList<>(m.broken.keySet()).stream().filter(cds -> cds[0] == e.getBlock().getX() && cds[1] == e.getBlock().getY() && cds[2] == e.getBlock().getZ()).forEach(cds -> {
            m.broken.put(cds, true);
            if (m.broken.values().stream().allMatch(b -> b)) {
                MovingTwinkle.twinkles.remove(m.pl);
                broadcast(DARK_GREEN + t.displayName + " " + GOLD + m.group + DARK_GREEN + " monument burned down!");
                OMGPlayer.link.values.forEach(p -> {
                    p.play_sound_roar();
                    p.bukkit.sendTitle(t.displayName + " " + GOLD + m.group + DARK_GREEN + " destroyed!", "", 0, 50, 0);
                });
            }
            e.setCancelled(true);
            e.getBlock().setType(Material.AIR);
        })));
        game_checkForEnd();
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> {
            if (OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).stream().noneMatch(t -> ((List<Monument>) t.hashdata_get("mr_mons")).stream().anyMatch(m -> m.broken.keySet().stream().anyMatch(cds -> cds[0] == b.getX() && cds[1] == b.getY() && cds[2] == b.getZ()))) || state != GameState.INGAME)
                return false;
            OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).forEach(t -> ((List<Monument>) t.hashdata_get("mr_mons")).forEach(m -> new ArrayList<>(m.broken.keySet()).stream().filter(cds -> cds[0] == b.getX() && cds[1] == b.getY() && cds[2] == b.getZ()).forEach(cds -> {
                m.broken.put(cds, true);
                if (m.broken.values().stream().allMatch(b1 -> b1)) {
                    MovingTwinkle.twinkles.remove(m.pl);
                    broadcast(DARK_GREEN + t.displayName + " " + GOLD + m.group + DARK_GREEN + " monument blasted!");
                    OMGPlayer.link.values.forEach(p -> {
                        p.play_sound_roar();
                        p.bukkit.sendTitle(t.displayName + " " + GOLD + m.group + DARK_GREEN + " destroyed!", "", 0, 50, 0);
                    });
                }
                e.setCancelled(true);
                b.setType(Material.AIR);
            })));
            game_checkForEnd();
            return true;
        });
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (OMGTeam.getFiltered(t -> t != spectatorTeam && t != defaultTeam).stream().anyMatch(t -> ((List<Monument>) t.hashdata_get("mr_mons")).stream().anyMatch(m -> m.broken.keySet().stream().anyMatch(cds -> cds[0] == e.getBlock().getX() && cds[1] == e.getBlock().getY() && cds[2] == e.getBlock().getZ()))) || state != GameState.INGAME)
            e.setCancelled(true);
    }

    public Location trackerLocation(OMGPlayer p) {
        HashMap<double[], Boolean> ms = new HashMap<>();
        ((List<Monument>) (p.team == a ? b : a).hashdata_get("mr_mons")).forEach(m -> ms.putAll(m.broken));
        Optional<double[]> d = ms.keySet().stream().filter(c -> !ms.get(c)).sorted(Comparator.comparingDouble(c -> new Location(p.bukkit.getLocation().getWorld(), c[0], c[1], c[2]).distance(p.bukkit.getLocation()))).findFirst();
        return d.map(doubles -> new Location(p.bukkit.getLocation().getWorld(), doubles[0], doubles[1], doubles[2])).orElseGet(() -> p.bukkit.getWorld().getSpawnLocation());
    }


    public OMGTeam otherTeam(OMGTeam t) {
        return t == a ? b : t == b ? a : defaultTeam;
    }

}