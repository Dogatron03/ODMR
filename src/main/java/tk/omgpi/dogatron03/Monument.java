package tk.omgpi.dogatron03;


import org.bukkit.ChatColor;
import org.bukkit.Location;
import tk.omgpi.OMGPI;
import tk.omgpi.game.OMGTeam;
import tk.omgpi.utils.Coordinates;
import tk.omgpi.utils.MovingTwinkle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Monument {
    public MovingTwinkle pl;
    OMGTeam owner;
    String group;
    HashMap<double[], Boolean> broken;
    int canattack;

    private Monument(OMGTeam owner, String group, List<double[]> coords) {
        this.owner = owner;
        this.group = group;
        canattack = 0;
        broken = new HashMap<>();
        coords.forEach(c -> broken.put(c, false));
        Location mid = new Location(OMGPI.gameworld.bukkit, broken.keySet().stream().mapToInt(o -> (int) o[0]).min().orElse(0), broken.keySet().stream().mapToInt(o -> (int) o[1]).min().orElse(0), broken.keySet().stream().mapToInt(o -> (int) o[2]).min().orElse(0)).add(0.5, 0, 0.5);
        pl = new MovingTwinkle(mid, mid.clone().add(0, 30, 0), MovingTwinkle.fromChatColor(ChatColor.getByChar(owner.prefix.charAt(1))));
    }

    public static Monument parseMon(String mon) {
        String[] data = mon.split(";");
        for (int i = 0; i < data.length; i++) data[i] = data[i].trim();
        if (data[2].split(",").length > 3) {
            List<double[]> cts = new LinkedList<>();
            double[] asarea = Coordinates.parse(data[2], Coordinates.CoordinateType.AREA);
            for (int x = (int) asarea[0]; x <= asarea[3]; x++)
                for (int y = (int) asarea[1]; y <= asarea[4]; y++)
                    for (int z = (int) asarea[2]; z <= asarea[5]; z++) cts.add(new double[]{x, y, z});
            return new Monument(OMGTeam.getTeamByID(data[0]), ChatColor.translateAlternateColorCodes('&', data[1]), cts);
        } else
            return new Monument(OMGTeam.getTeamByID(data[0]), ChatColor.translateAlternateColorCodes('&',data[1]), new ArrayList<double[]>() {{
                add(Coordinates.parse(data[2], Coordinates.CoordinateType.POINT));
            }});
    }
}

