/**
 * 
 */
package org.morganm.loginlimiter;

import java.util.List;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;

/**
 * @author morganm
 *
 */
public class MyPlayerListener extends PlayerListener {
	private LoginLimiter plugin;
	
	public MyPlayerListener(LoginLimiter plugin) {
		this.plugin = plugin;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void onPlayerLogin(PlayerLoginEvent event) {
		// if the login was already refused by another plugin, don't do anything
		if( event.getResult() != Result.ALLOWED )
			return;
		
		Player p = event.getPlayer();

		boolean limitAllowed = true;
		boolean requiredPermsLoginFlag = true;
		
		Player[] onlinePlayers = plugin.getServer().getOnlinePlayers();
		
		FileConfiguration config = plugin.getConfig();
		ConfigurationSection section = config.getConfigurationSection("limits");
		Set<String> nodes = section.getKeys(false);
		
		// do global limit checks first
		int globalLimit = config.getInt("globalLimit", -1);
		if( globalLimit != -1 && onlinePlayers.length >= globalLimit ) {
			boolean exempt = false;
			List<String> globalExemptPerms = config.getStringList("globalOverridePerms");
			if( globalExemptPerms != null ) {
				for(String perm : globalExemptPerms) {
					if( plugin.has(p, perm) ) {
						exempt = true;
						break;
					}
				}
			}
		}
		
		if( nodes != null ) {
			for(String node : nodes) {
				List<String> perms = config.getStringList("limits."+node+".permissions");
				if( perms != null ) {
					// if the player has one of the perms listed, then this limit applies to them
					for(String perm : perms) {
						if( plugin.has(p, perm) ) {
							int limit = config.getInt("limits."+node+".limit", -1);
							int ifOver = config.getInt("limits."+node+".ifOver", -1);
							List<String> requiredPerms = config.getShortList("limits."+node+".requiredOnlinePerms");
							boolean requiredPermsOnlyForNew = config.getBoolean("limits."+node+".requiredPermsOnlyForNew", false);
							
							// boolean flag to determine if the required permission is online. This
							// is used so you can require a moderator be in order for new guests to
							// login, for example.
							boolean isRequiredPermsOnline = false;
							if( requiredPerms == null || requiredPerms.size() == 0 )
								isRequiredPermsOnline = true;
							
							// if requiredPerms check is only for new players, and this isn't a new
							// player, then set the flag to true (since we don't need to do the check)
							if( requiredPermsOnlyForNew && !plugin.isNewPlayer(p) )
								isRequiredPermsOnline = true;
							
							// check the ifOver limit against current player size to see
							// if we should proceed
							if( ifOver != -1 && onlinePlayers.length < ifOver ) {
								break;
							}
							
							// better just to not define the limit at all, but this allows
							// for explicit "infinite" limit
							if( limit == -1 ) {
								limitAllowed = true;
								return;
							}
							
							// if we already know from a previous iteration through the loop
							// that we are restricted by a limit definition, then no need
							// to do any further processing, move on to next entry. (note we
							// don't just return b/c we have to loop through all entries in
							// case we hit a -1 limit definition that overrides all other
							// limits)
							if( !limitAllowed || !requiredPermsLoginFlag )
								break;
							
//							String group = config.getString("limits."+node+".group");
							
							// the total count of people online for this permission grouping
							int groupCount = 0;
							
							for(int i=0; i < onlinePlayers.length; i++) {
								if( limitAllowed ) {
									// check online players who are in this same group of perms
									// to find out how many are online
									for(String thePerm : perms) {
										if( plugin.has(onlinePlayers[i], thePerm) ) {
											if( ++groupCount >= limit ) {
												limitAllowed = false;
												break;
											}
										}
									}
								}

								// do the requiredPerms check while we're iterating players, too.
								// note once this flag goes to true, we don't need to check it anymore.
								if( !isRequiredPermsOnline && requiredPerms != null ) {
									for(String reqPerm : requiredPerms) {
										if( plugin.has(p,  reqPerm) ) {
											isRequiredPermsOnline = true;
											break;
										}
									}
								}
							}
							
							// if we get here, that means we've looped through all online players.
							// if there was a requiredPerms requirement that went unmet, then
							// isRequiredPermsOnline will still be false. In that case, set the
							// login flag to false so this player will be rejected by virtue
							// of not having anyone online that meets the required perms definition.
							if( !isRequiredPermsOnline ) {
								requiredPermsLoginFlag = false;
							}
						} // end if( plugin.has(p, perm) )
					}
				}
			}  // end for(String node : nodes)
		} // end if( nodes != null ) 
		
		if( !limitAllowed ) {
			String msg = plugin.getConfig().getString("messages.limitReached", "The number of reserved slots for your rank has been reached. Try again later");
			event.disallow(Result.KICK_OTHER, msg);
		}
		
		if( !requiredPermsLoginFlag ) {
			String msg = plugin.getConfig().getString("messages.noPermsOnlineString", "The required rank is not online at this time");
			event.disallow(Result.KICK_OTHER, msg);
		}
	}
}
