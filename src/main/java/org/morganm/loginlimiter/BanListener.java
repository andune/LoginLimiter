/**
 * 
 */
package org.morganm.loginlimiter;

import org.bukkit.event.player.PlayerListener;
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
public class BanListener extends PlayerListener {
	private LoginLimiter plugin;
	private BanInterface ban;
	
	public BanListener(final LoginLimiter plugin) {
		this.plugin = plugin;
		this.ban = this.plugin.getBanObject();
	}
	
	@Override
	public void onPlayerPreLogin(PlayerPreLoginEvent event) {
		if( event.getResult() == org.bukkit.event.player.PlayerPreLoginEvent.Result.KICK_BANNED ) {
			ban.updateCache(event.getName(), true);
		}
	}

	@Override
	public void onPlayerLogin(PlayerLoginEvent event) {
		if( event.getResult() == org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED ) {
			ban.updateCache(event.getPlayer().getName(), true);
		}
	}
}
