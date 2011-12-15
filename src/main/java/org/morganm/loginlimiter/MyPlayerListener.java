/**
 * 
 */
package org.morganm.loginlimiter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;
import org.morganm.loginlimiter.bans.BanInterface;

/**
 * @author morganm
 *
 */
public class MyPlayerListener extends PlayerListener implements ConfigConstants {
	private static final Logger log = LoginLimiter.log;
	private static final String logPrefix = LoginLimiter.logPrefix;
	
	private final LoginLimiter plugin;
	private final Debug debug;
	private final BanInterface ban;
	private boolean warningNoGroupLimitsDisplayed = false;
	private HashSet<String> noRequiredPermsRejects = new HashSet<String>(10);
	
	public MyPlayerListener(LoginLimiter plugin) {
		this.plugin = plugin;
		this.debug = Debug.getInstance();
		this.ban = plugin.getBanObject();
	}
	
	@Override
	public void onPlayerPreLogin(PlayerPreLoginEvent event) {
		// if the login was already refused by another plugin, don't do anything
		if( event.getResult() != Result.ALLOWED )
			return;
		
		String playerName = event.getName();
		debug.debug("onPlayerLogin(): playerName=",playerName);
		
		String playerIP = event.getAddress().getHostAddress();
		if( ban != null && ban.isBanned(playerName, playerIP) ) {
			debug.debug("Player",playerName," is banned, refusing login");
			event.disallow(Result.KICK_BANNED, "You have been banned");
			return;
		}
		
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
		
		// player is exempt from queue limits?
		if( freeCount == -2 ) {
			debug.debug("Player ",playerName," is exempt from queue limits, login allowed");
			event.setResult(Result.ALLOWED);
			return;
		}
		
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
						msg = plugin.getConfig().getString("messages.queued", "Server full, now queued (${queueNumber}/${queueTotal}). Connect at least every ${queueLoginTime} to hold spot");
						msg = msg.replaceAll("\\$\\{queueNumber\\}", Integer.toString(queue.getQueuePosition(playerName)));
						msg = msg.replaceAll("\\$\\{queueTotal\\}", Integer.toString(queue.getQueueSize()));
						msg = msg.replaceAll("\\$\\{queueLoginTime\\}",
								shortTime(queue.getQueueLoginTime(playerName)));
					}
					else {
						msg = plugin.getConfig().getString("messages.queued", "Queued: ${queueNumber} of ${queueTotal}. Connect at least every ${queueLoginTime} to hold spot");
						msg = msg.replaceAll("\\$\\{queueNumber\\}", Integer.toString(queue.getQueuePosition(playerName)));
						msg = msg.replaceAll("\\$\\{queueTotal\\}", Integer.toString(queue.getQueueSize()));
						msg = msg.replaceAll("\\$\\{queueLoginTime\\}",
								shortTime(queue.getQueueLoginTime(playerName)));
						queue.loginAttempt(playerName);
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
	
	private String shortTime(int seconds) {
		if( seconds == 1 ) {
			return "1 second";
		}
		else if( seconds < 60 ) {
			return seconds + " seconds";
		}
		else {
			// we don't display the seconds once we're past 60
			return (seconds / 60) + " mins";
		}
	}
	
	/** Check to see if player is allowed to login based on global limit settings. Will
	 * modify the event to disallow the login if they are not.
	 * 
	 * @param event
	 * @return the number of available slots on the server, or -2 if player is exempt from queue limits
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
		
		boolean exempt = false;
		if( globalLimit > 0 && onlinePlayers.length >= globalLimit ) {
			// check to see if they are part of the exempt perm list
			List<String> globalExemptPerms = config.getStringList(CONFIG_GLOBAL+"limitExemptPerms");
			if( globalExemptPerms != null ) {
				for(String perm : globalExemptPerms) {
					if( plugin.has(playerName, perm) ) {
						debug.debug("checkGlobalLimits() player ",playerName," is exempt due to permission ",perm);
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
		
		if( exempt )
			return -2;
		else
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
							
							// check required perms before we do any limit checks
							for(int i=0; i < onlinePlayers.length; i++) {
								// note once this flag goes to true, we don't need to check it anymore.
								if( !isRequiredPermsOnline && requiredPerms != null ) {
									for(String reqPerm : requiredPerms) {
										if( plugin.has(onlinePlayers[i],  reqPerm) ) {
											if( plugin.getOnDuty().isOffDuty(onlinePlayers[i].getName()) ) {
												debug.debug("checkGroupLimits(): node ",node,", permission ",perm," player ",onlinePlayers[i]," meets required perm, but is OFF duty (perm=",reqPerm,")");
											}
											else {
												debug.debug("checkGroupLimits(): node ",node,", permission ",perm," required permission requirement met by player ",onlinePlayers[i]," (perm=",reqPerm,")");
												isRequiredPermsOnline = true;
												break;
											}
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
								debug.debug("checkGroupLimits(): node ",node,", permission ",perm," required online permission not met for this section");
								requiredPermsLoginFlag = false;
								break;
							}

							// calculate the requiredPermRatio limit, if any
							int requiredPermRatioLimit = -1;
							double requiredPermRatio = config.getDouble(CONFIG_GROUP_LIMIT+node+".requiredPermRatio", -1);
							if( requiredPermRatio != -1 ) {
								List<Player> onDutyPlayers = plugin.getOnDuty().getOnDutyPlayers();
								requiredPermRatioLimit = (int) Math.floor(onDutyPlayers.size() * requiredPermRatio);
								debug.debug("checkGroupLimits(): node ",node,", permission ",perm," onDutyPlayers=",onDutyPlayers.size(),", requiredPermRatio=",requiredPermRatio,", requiredPermRatioLimit=",requiredPermRatioLimit);
							}

							// we do some short-circuit checks, but only if there is no requiredPermRatio limit
							if( requiredPermRatioLimit == -1 ) {
								// check the ifOver limit against current player size to see
								// if we should proceed. Note if there is a requiredPermRatio,
								// we proceed anyway.
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
							
							int groupCount = 0;		// the total count of people online for this permission grouping
							
							for(int i=0; i < onlinePlayers.length; i++) {
								if( limitAllowed ) {
									// check online players who are in this same group of perms
									// to find out how many are online
									for(String thePerm : perms) {
										if( plugin.has(onlinePlayers[i], thePerm) ) {
											groupCount++;

											// we only process limit checks if we are over the ifOver limit
											if( ifOver == -1 || onlinePlayers.length > ifOver ) {
												if( limit != -1 && groupCount >= limit ) {
													debug.debug("checkGroupLimits(): node ",node,", permission ",perm," limit exceeded (",groupCount," >= ",limit,") player ",thisPlayer," is over the limit");
													limitAllowed = false;
													break;
												}
											}
											
											// requiredPermRatio checks apply regardless of ifOver limit
											if( requiredPermRatioLimit != -1 && groupCount >= requiredPermRatioLimit ) {
												debug.debug("checkGroupLimits(): node ",node,", permission ",perm," requiredPermRatioLimit exceeded (",groupCount," >= ",requiredPermRatioLimit,") player ",thisPlayer," is over the limit");
												limitAllowed = false;
												break;
											}
										}
									}
								}
							}
							
							// record the smallest limit remaining that we encounter for this player
							if( smallestLimit == -1 || (limit - groupCount) < smallestLimit )
								smallestLimit = limit - groupCount;
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
			noRequiredPermsRejects.add(thisPlayer);
			if( plugin.getConfig().getBoolean("verbose", true) ) {
				String preMsg = "Player ";
				if( plugin.isNewPlayer(thisPlayer) )
					preMsg = "New player ";
				log.info(logPrefix+preMsg+thisPlayer+" denied login since required rank is not online at this time");
			}
		}
		
		return smallestLimit;
	}
	
	/** Not used for the queue itself, we use prelogin for that. This is used for doing some maintenance
	 * AFTER we know someone has successfully logged in (this is done at priority MONITOR).
	 * 
	 */
	@Override
	public void onPlayerLogin(PlayerLoginEvent event) {
		// do nothing if the login has been rejected
		if( event.getResult() != org.bukkit.event.player.PlayerLoginEvent.Result.ALLOWED )
			return;

		Player p = event.getPlayer();
		if( plugin.isDutyEligible(p) ) {
			p.sendMessage(ChatColor.YELLOW+"You are currently "
					+ (plugin.getOnDuty().isOffDuty(p.getName()) ? "OFF" : "ON")
					+ " duty.");
		}
		
		// this person doesn't count as a requiredPerms reject anymore since they made it online
		noRequiredPermsRejects.remove(p.getName());
	}
	
	public Set<String> getNoRequiredPermsRejects() { return noRequiredPermsRejects; }
}
