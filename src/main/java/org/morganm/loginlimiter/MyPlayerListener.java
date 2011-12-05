/**
 * 
 */
package org.morganm.loginlimiter;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * @author morganm
 *
 */
public class MyPlayerListener extends PlayerListener {
	private static final Logger log = LoginLimiter.log;
	private static final String logPrefix = LoginLimiter.logPrefix;
	private static final String CONFIG_GROUP_LIMIT = LoginLimiter.CONFIG_GROUP_LIMIT;
	private static final String CONFIG_GLOBAL = LoginLimiter.CONFIG_GLOBAL;
	
	private final LoginLimiter plugin;
	private final Debug debug;
	private boolean warningNoGroupLimitsDisplayed = false;
	
	public MyPlayerListener(LoginLimiter plugin) {
		this.plugin = plugin;
		this.debug = Debug.getInstance();
	}
	
	@Override
	public void onPlayerPreLogin(PlayerPreLoginEvent event) {
		// if the login was already refused by another plugin, don't do anything
		if( event.getResult() != Result.ALLOWED )
			return;
		
		String playerName = event.getName();
		debug.debug("onPlayerLogin(): playerName=",playerName);
		
		int freeCount = 0;
		
		freeCount = checkGlobalLimits(event);
		debug.debug("checkGlobalLimits result=",freeCount);
		
		if( event.getResult() == Result.ALLOWED ) {
			int tmp = checkGroupLimits(event);
			debug.debug("checkGroupLimits result=",tmp);
			
			// if group limit is lower than global, we use that as our freeCount limit
			if( tmp != -1 && tmp < freeCount )
				freeCount = tmp;
		}
		debug.debug("freeCount=",freeCount);
		
		// need to re-arrange so player gets added to queue when server is full
		
		// if we get this far and the login has not been refused, then there is
		// possibly a free slot to be had for this player. Now we need to check
		// the queue to see if they are eligible for that free slot based on
		// who else might be waiting ahead of them.
//		if( event.getResult() != Result.ALLOWED ) {
//			debug.debug("slot is available, checking queue");
			LoginQueue queue = plugin.getLoginQueue();
			
			// if there are more free slots than the queue+reconnect size, then that means
			// there are plenty of free slots and so the player can have this slot. If
			// not, run some queue checks.
			if( (queue.getQueueSize() + queue.getReconnectQueueSize()) >= freeCount ) {
				debug.debug("queue size larger than freeCount, running queue checks.",
						" queueSize=",queue.getQueueSize(),
						" queueReconnectSize=",queue.getReconnectQueueSize());
				boolean playerAdded = false;
				// if player is not in the queue, add them to it
				if( !queue.isPlayerQueued(playerName) ) {
					queue.addQueuedPlayer(playerName);
					playerAdded = true;
				}
				
				if( !queue.isEligible(playerName, freeCount) ) {
					String msg = null;
					if( playerAdded ) {
						msg = plugin.getConfig().getString("messages.queued", "Server full, now queued (${queueNumber}/${queueTotal}). Connect at least every ${reconnectSeconds} seconds to hold spot");
						msg = msg.replaceAll("\\$\\{queueNumber\\}", Integer.toString(queue.getQueuePosition(playerName)));
						msg = msg.replaceAll("\\$\\{queueTotal\\}", Integer.toString(queue.getQueueSize()));
						msg = msg.replaceAll("\\$\\{reconnectSeconds\\}", Integer.toString(plugin.getConfig().getInt(CONFIG_GLOBAL+"queueLoginTime", 0)));
					}
					else {
						msg = plugin.getConfig().getString("messages.queued", "Queued: ${queueNumber} of ${queueTotal}. Connect at least every ${reconnectSeconds} seconds to hold spot");
						msg = msg.replaceAll("\\$\\{queueNumber\\}", Integer.toString(queue.getQueuePosition(playerName)));
						msg = msg.replaceAll("\\$\\{queueTotal\\}", Integer.toString(queue.getQueueSize()));
						msg = msg.replaceAll("\\$\\{reconnectSeconds\\}", Integer.toString(plugin.getConfig().getInt(CONFIG_GLOBAL+"queueLoginTime", 0)));
					}
					
					event.disallow(Result.KICK_OTHER, msg);
				}
			}
			else {
				if( event.getResult() == Result.ALLOWED )
					debug.debug("more slots available than people in queue, login allowed");
			}
//		}
			
		if( event.getResult() == Result.ALLOWED ) {
			queue.playerLoggedIn(playerName);
		}
	}
	
	@Override
	public void onPlayerQuit(PlayerQuitEvent event) {
		plugin.getLoginQueue().addReconnectPlayer(event.getPlayer());
	}
	
	/** Check to see if player is allowed to login based on global limit settings. Will
	 * modify the event to disallow the login if they are not.
	 * 
	 * @param event
	 * @return the number of available slots on the server
	 */
	@SuppressWarnings("unchecked")
	private int checkGlobalLimits(PlayerPreLoginEvent event) {
		String playerName = event.getName();
		Player[] onlinePlayers = plugin.getServer().getOnlinePlayers();
		
		FileConfiguration config = plugin.getConfig();
		
		int globalLimit = config.getInt(CONFIG_GLOBAL+"limit", -1);
		globalLimit -= plugin.getLoginQueue().getReconnectQueueSize();
		
		// if current player is in the reconnect Queue, then add 1
		// back to the global limit since that doesn't count against
		// our limit
		if( plugin.getLoginQueue().isInReconnectQueue(playerName) ) {
			debug.debug("checkGlobalLimits() player ",playerName," is in reconnect queue. Limit +1.");
			globalLimit++;
		}
		
		debug.debug("checkGlobalLimits() globalLimit=",globalLimit," onlinePlayers.length=",onlinePlayers.length);
		
		if( globalLimit > 0 && onlinePlayers.length >= globalLimit ) {
			boolean exempt = false;

			// check to see if they are part of the exempt perm list
			List<String> globalExemptPerms = config.getStringList(CONFIG_GLOBAL+"limitExemptPerms");
			if( globalExemptPerms != null ) {
				for(String perm : globalExemptPerms) {
					if( plugin.has(playerName, perm) ) {
						exempt = true;
						break;
					}
				}
			}
			
			if( !exempt ) {
				String msg = plugin.getConfig().getString("messages.globalLimitReached", "The server is full.");
				event.disallow(Result.KICK_OTHER, msg);
				return 0;
			}
		}
		
		return globalLimit - onlinePlayers.length;
	}
	
	/** Check to see if a player is allowed to login based on group limits.  Will
	 * modify the event to disallow the login if they are not.
	 * 
	 * @param event
	 * @return the remaining slots for this player's group category, or -1 if this player is part of
	 * an unlimited group
	 */
	@SuppressWarnings("unchecked")
	private int checkGroupLimits(PlayerPreLoginEvent event) {
		String thisPlayer = event.getName();

		int smallestLimit = -1;
		boolean limitAllowed = true;
		boolean requiredPermsLoginFlag = true;
		
		Player[] onlinePlayers = plugin.getServer().getOnlinePlayers();
		
		FileConfiguration config = plugin.getConfig();
		ConfigurationSection section = config.getConfigurationSection("groupLimits");
		if( section == null ) {
			if( !warningNoGroupLimitsDisplayed ) {
				log.warning(logPrefix + "config.yml mising \"groupLimits\" section!");
				warningNoGroupLimitsDisplayed = true;
			}
			return -1;
		}
		Set<String> nodes = section.getKeys(false);
		
		if( nodes != null ) {
			for(String node : nodes) {
				List<String> perms = config.getStringList(CONFIG_GROUP_LIMIT+node+".permissions");
				if( perms != null ) {
					for(String perm : perms) {
						debug.debug("checkGroupLimits(): node ",node,", permission ",perm," checking permission limits");
						
						// if the player has one of the perms listed, then this limit applies to them
						if( plugin.has(thisPlayer, perm) ) {
							debug.debug("player ",thisPlayer," HAS permission ",perm);
							int limit = config.getInt(CONFIG_GROUP_LIMIT+node+".limit", -1);
							int ifOver = config.getInt(CONFIG_GROUP_LIMIT+node+".ifOver", -1);
							
							List<String> requiredPerms = null;
							// extra check required to work around Bukkit NPE bug BUKKIT-213
							Object o = config.get(CONFIG_GROUP_LIMIT+node+".requiredOnlinePerms");
							if( o != null )
								requiredPerms = config.getStringList(CONFIG_GROUP_LIMIT+node+".requiredOnlinePerms");
							boolean requiredPermsOnlyForNew = config.getBoolean(CONFIG_GROUP_LIMIT+node+".requiredPermsOnlyForNew", false);
							
							// boolean flag to determine if the required permission is online. This
							// is used so you can require a moderator be in order for new guests to
							// login, for example.
							boolean isRequiredPermsOnline = false;
							if( requiredPerms == null || requiredPerms.size() == 0 )
								isRequiredPermsOnline = true;
							
							// if requiredPerms check is only for new players, and this isn't a new
							// player, then set the flag to true (since we don't need to do the check)
							if( requiredPermsOnlyForNew && !plugin.isNewPlayer(thisPlayer) )
								isRequiredPermsOnline = true;
							
							// check the ifOver limit against current player size to see
							// if we should proceed
							if( ifOver != -1 && onlinePlayers.length < ifOver ) {
								debug.debug("checkGroupLimits(): node ",node,", permission ",perm," ifOver threshold not met, skipping group limit check");
								break;
							}
							
							// better just to not define the limit at all, but this allows
							// for explicit "infinite" limit
							if( limit == -1 ) {
								limitAllowed = true;
								debug.debug("checkGroupLimits(): node ",node,", permission ",perm," limit is -1, returning unlimited");
								return -1;
							}
							
							// if we already know from a previous iteration through the loop
							// that we are restricted by a limit definition, then no need
							// to do any further processing, move on to next entry. (note we
							// don't just return b/c we have to loop through all entries in
							// case we hit a -1 limit definition that overrides all other
							// limits)
							if( !limitAllowed || !requiredPermsLoginFlag ) {
								debug.debug("checkGroupLimits(): node ",node,", permission ",perm," previous perm defined limit, not checking this permission");
								break;
							}
							
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
												debug.debug("checkGroupLimits(): node ",node,", permission ",perm," limit exceeded (",groupCount," >= ",limit,") player ",thisPlayer," is over the limit");
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
										if( plugin.has(onlinePlayers[i],  reqPerm) ) {
											debug.debug("checkGroupLimits(): node ",node,", permission ",perm," required permission requirement met by player ",onlinePlayers[i]," (perm=",reqPerm,")");
											isRequiredPermsOnline = true;
											break;
										}
									}
								}
							}
							
							// record the smallest limit remaining that we encounter for this player
							if( smallestLimit == -1 || (limit - groupCount) < smallestLimit )
								smallestLimit = limit - groupCount;
							
							// if we get here, that means we've looped through all online players.
							// if there was a requiredPerms requirement that went unmet, then
							// isRequiredPermsOnline will still be false. In that case, set the
							// login flag to false so this player will be rejected by virtue
							// of not having anyone online that meets the required perms definition.
							if( !isRequiredPermsOnline ) {
								debug.debug("checkGroupLimits(): node ",node,", permission ",perm," required permission requirement not met for this section");
								requiredPermsLoginFlag = false;
							}
						} // end if( plugin.has(p, perm) )
						else
							debug.debug("checkGroupLimits(): node ",node,", player ",thisPlayer," DOES NOT HAVE permission ",perm);
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
			if( plugin.getConfig().getBoolean("verbose", true) ) {
				String preMsg = "Player ";
				if( plugin.isNewPlayer(thisPlayer) )
					preMsg = "New player ";
				log.info(logPrefix+preMsg+thisPlayer+" denied login since required rank is not online at this time");
			}
		}
		
		return smallestLimit;
	}
}
