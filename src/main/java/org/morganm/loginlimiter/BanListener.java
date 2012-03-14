/**
 * 
 */
package org.morganm.loginlimiter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.morganm.loginlimiter.bans.BanInterface;

/** This class exists to listen for PlayerLogin at the END of the priority chain.
 * If the event gets here and has a BAN reason, then we know the player was banned
 * by some plugin and we can update our cache accordingly. 
 * 
 * @author morganm
 *
 */
public class BanListener implements Listener {
	private LoginLimiter plugin;
	private BanInterface ban;
	
	public BanListener(final LoginLimiter plugin) {
		this.plugin = plugin;
		this.ban = this.plugin.getBanObject();
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerPreLogin(PlayerPreLoginEvent event) {
		if( event.getResult() == org.bukkit.event.player.PlayerPreLoginEvent.Result.KICK_BANNED ) {
			ban.updateCache(event.getName(), true);
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerLogin(PlayerLoginEvent event) {
		if( event.getResult() == org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED ) {
			ban.updateCache(event.getPlayer().getName(), true);
		}
	}
}
