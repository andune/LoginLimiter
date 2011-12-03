/**
 * 
 */
package org.morganm.loginlimiter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * @author morganm
 *
 */
public class LoginQueue {
	private static final Logger log = LoginLimiter.log;
	private static final String logPrefix = LoginLimiter.logPrefix;
	private static final String CONFIG_GLOBAL = LoginLimiter.CONFIG_GLOBAL;
	
	private final LinkedHashMap<String, PlayerInfo> loginQueue = new LinkedHashMap<String, PlayerInfo>();
	private final HashMap<String, Long> reconnectQueue = new HashMap<String, Long>();
	private long lastCleanup = 0;
	
	private LoginLimiter plugin;
	
	public LoginQueue(LoginLimiter plugin) {
		this.plugin = plugin;
	}
	
	public void addQueuedPlayer(Player player) {
		String playerName = player.getName();
		
		PlayerInfo pInfo = new PlayerInfo();
		pInfo.firstLoginAttempt = System.currentTimeMillis();
		pInfo.lastLoginAttempt = System.currentTimeMillis();
		pInfo.rank = getPlayerRank(player);
		
		loginQueue.put(playerName, pInfo);
	}
	
	/** Add a player to the reconnect queue, usually called when they have disconnected.
	 * 
	 * @param player
	 */
	public void addReconnectPlayer(Player player) {
		reconnectQueue.put(player.getName(), System.currentTimeMillis());
	}
	
	public int getQueueSize() {
		cleanupQueue();
		return loginQueue.size();
	}
	
	public int getReconnectQueueSize() {
		cleanupQueue();
		return reconnectQueue.size();
	}
	
	public int getQueuePosition(Player player) {
		String playerName = player.getName();
		
		int position = 1;
		for(Entry<String,PlayerInfo> entry : loginQueue.entrySet()) {
			if( playerName.equals(entry.getKey()) )
				break;
			position++;
		}
		
		return position;
	}
	
	public boolean isPlayerQueued(Player player) {
		return loginQueue.get(player.getName()) != null;
	}
	
	public boolean isInReconnectQueue(Player player) {
		cleanupQueue();
		if( reconnectQueue.get(player.getName()) != null )
			return true;
		else
			return false;
	}
	
	private int getPlayerRank(Player player) {
		int currentRank = 0;
		
		ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_GLOBAL+"queueRankPerms");
		Set<String> ranks = section.getKeys(false);
		if( ranks != null ) {
			for(String strRank : ranks) {
				try {
					Integer rank = Integer.parseInt(strRank);
					String perm = plugin.getConfig().getString(CONFIG_GLOBAL+"queueRankPerms."+strRank);
					
					// if the rank is higher than our current detected rank and the player has
					// the permission, then record the new rank
					if( rank > currentRank && plugin.has(player, perm) )
						currentRank = rank;
				}
				catch(NumberFormatException nfe) {
					log.warning(logPrefix + "Invalid entry "+strRank+" for queueRankPerms: must be a number");
				}
			}
		}
		
		return currentRank;
	}
	
	private void cleanupQueue() {
		// to avoid any performance hit from this method being called frequently,
		// we make sure we run at most once per second. This method is fast anyway
		// so this is just defensive coding on my part to make sure this can be
		// called in the accessor methods above without concern for performance.
		if( System.currentTimeMillis() - lastCleanup < 1000 )
			return;
		
		// make sure we're thread safe, in case Bukkit allows multiple Player
		// login threads to run at once (not sure...)
		synchronized(LoginQueue.class) {
			FileConfiguration config = plugin.getConfig();
			int maxTime = config.getInt(CONFIG_GLOBAL+"queueMaxTime", 0) * 1000;
			int loginTime = config.getInt(CONFIG_GLOBAL+"queueLoginTime", 0) * 1000;
			int reconnectTime = config.getInt(CONFIG_GLOBAL+"reconnectTime", 0) * 1000;
			
			// TODO: add reconnectTime cleanup
			
			long currentTime = System.currentTimeMillis();
			Set<Entry<String,PlayerInfo>> entrySet = loginQueue.entrySet();
			for(Iterator<Entry<String,PlayerInfo>> i = entrySet.iterator(); i.hasNext();) {
				Entry<String,PlayerInfo> e = i.next();
				
				String playerName = e.getKey();
				PlayerInfo pInfo = e.getValue();
	
				// if p is not null, then the player is already online and should no
				// longer be in the queue
				Player p = plugin.getServer().getPlayer(playerName);
				
				if( p != null || (maxTime > 0 && (currentTime - pInfo.firstLoginAttempt) > maxTime)
						|| (loginTime > 0 && (currentTime - pInfo.lastLoginAttempt) > loginTime) ) {
					i.remove();
				}
			}
			
			lastCleanup = System.currentTimeMillis();
		}
	}
	
	/** Check the queue to see if a player is eligible to login. This is intended to be
	 * called on player login and will update the players lastLoginAttempt accordingly.
	 * 
	 * @param playerName the player being checked
	 * @param freeCount the free slots left (how far in the queue we can check)
	 * @return true if the player is eligible to login based on the queue, false if not
	 */
	public boolean isEligible(Player player, int freeCount) {
		if( player == null )
			return false;
		if( freeCount < 1 )
			return false;
		
		String playerName = player.getName();
		PlayerInfo pInfo = loginQueue.get(playerName);
		
		// if the player is not even in the queue (should never happen), then they're
		// not allowed to login, they need to wait in the queue.
		if( pInfo == null )
			return false;
		
		// this method is called on login attempt, so update the lastLoginAttempt for this player
		pInfo.lastLoginAttempt = System.currentTimeMillis();
		
		cleanupQueue();

		int count = 0;
		for(Entry<String,PlayerInfo> entry : loginQueue.entrySet()) {
			if( playerName.equals(entry.getKey()) )
				return true;
			
			// if this player logging in has a higher rank than the player
			// we're checking in the queue, then player logging in skips the
			// lower ranked player in the queue
			if( pInfo.rank > entry.getValue().rank )
				continue;
			
			// if we're over our freeCount limit, then this person is not yet eligible
			if( ++count >= freeCount )
				return false;
		}
		
		// if we get here, we didn't hit the freeCount limit, so we are allowed to login
		return true;
	}
	
	class PlayerInfo {
		public long firstLoginAttempt;
		public long lastLoginAttempt;
		public int rank;
	}
}
