package net.veilmc.dataapi.listeners;

import com.customhcf.base.BasePlugin;
import com.customhcf.base.user.BaseUser;
import net.veilmc.dataapi.DataAPI;
import org.apache.commons.collections4.map.HashedMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class PlayerListener implements Listener{
    private DataAPI plugin;

    public PlayerListener(final DataAPI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Jedis jedis = plugin.getJedisPool().getResource();

        if(jedis.exists("data:players:" + player.getUniqueId().toString())) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                BaseUser baseUser = BasePlugin.getPlugin().getUserManager().getUser(player.getUniqueId()); //Player data fromm base
                Map<String, String> playerData = new HashedMap<>();
                playerData.putAll(jedis.hgetAll("data:players:" + player.getUniqueId().toString()));
                //Staff things
                if (playerData.get("staff_modmode").equals("true") && !baseUser.isStaffUtil())
                    Bukkit.dispatchCommand(player, "h");

                if (playerData.get("staff_sc").equals("true") && !baseUser.isInStaffChat())
                    Bukkit.dispatchCommand(player, "sc");

                if (playerData.get("staff_vanish").equals("true") && !baseUser.isVanished())
                    Bukkit.dispatchCommand(player, "v");

                //Options things
                baseUser.setGlobalChatVisible(playerData.get("options_gc").equals("true"));
                baseUser.setGlobalChatVisible(playerData.get("options_pm").equals("true"));
                baseUser.setGlobalChatVisible(playerData.get("options_sc").equals("true"));

                //Notification message.
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour options has been &eloaded &afrom the database."));

                plugin.saveSinglePlayerData(player, true); //Now save the data on database
                plugin.getLogger().info("Saved " + player.getName() + " data as he joined the game.");

                plugin.getJedisPool().returnResource(jedis);
                jedis.close();
            }, 3 * 20L);
        }



        if(player.hasPermission("rank.staff")){ //staff notification about server switched
            plugin.getPublisher().write("staffswitch;" + player.getName() + ";" + Bukkit.getServerName() + ";" + "joined the server.");
        }

        if(!plugin.getPlayerToSave().contains(player)) plugin.getPlayerToSave().add(player); //Player needs to be added to save-data list :p

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        final Player player = event.getPlayer();
        new BukkitRunnable() {
            public void run() {
                plugin.saveSinglePlayerData(player, false);
                plugin.getLogger().info("Saved " + player.getName() + " data as he quit the game.");
            }
        }.runTaskAsynchronously(this.plugin);

        if(player.hasPermission("rank.staff")){ //staff notification about server switched
            plugin.getPublisher().write("staffswitch;" + player.getName() + ";" + Bukkit.getServerName() + ";" + "left the server.");
        }

        if(plugin.getPlayerToSave().contains(player)) plugin.getPlayerToSave().remove(player); //We don't need to keep player in

                                                                                                        //arraylist of players-to-save (scheduler)

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStaffChatChat(AsyncPlayerChatEvent event){
        Player player = event.getPlayer();
        if(BasePlugin.getPlugin().getUserManager().getUser(player.getUniqueId()).isInStaffChat()){//Staffchat
            plugin.getPublisher().write("staffchat;" + player.getName() + ";" + Bukkit.getServerName() + ";" + event.getMessage().replace(";", ":"));
            event.setCancelled(true);
        }
    }


}