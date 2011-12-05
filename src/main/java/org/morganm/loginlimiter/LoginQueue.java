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
	
	private final LoginLimiter plugin;
	private final Debug debug;
	
	public LoginQueue(LoginLimiter plugin) {
		this.plugin = plugin;
		this.debug = Debug.getInstance();
	}
	
	public void addQueuedPlayer(String playerName) {
		debug.debug("addQueuedPlayer: playerName=",playerName);
		
		PlayerInfo pInfo = new PlayerInfo();
		pInfo.firstLoginAttempt = System.currentTimeMillis();
		pInfo.lastLoginAttempt = System.currentTimeMillis();
		pInfo.rank = getPlayerRank(playerName);
		
		loginQueue.put(playerName, pInfo);
		
		if( plugin.getConfig().getBoolean("verbose", true) )
			log.info(logPrefix+"Player "+playerName+" added to the queue"
					+ " (" + getQueuePosition(playerName) + "/" + loginQueue.size() + ")");
	}
	
	/** Add a player to the reconnect queue, usually called when they have disconnected.
	 * 
	 * @param player
	 */
	public void addReconnectPlayer(Player player) {
		debug.debug("addReconnectPlayer: player=",player);
		reconnectQueue.put(player.getName(), System.currentTimeMillis());
	}
	
	public int getQueueSize() {
		cleanupQueue();
		return reconnectQueue.size() + loginQueue.size();
	}
	
	public int getReconnectQueueSize() {
		cleanupQueue();
		return reconnectQueue.size();
	}
	
	public int getQueuePosition(String playerName) {
		int position = 1;
		for(Entry<String,PlayerInfo> entry : loginQueue.entrySet()) {
			if( playerName.equals(entry.getKey()) )
				break;
			position++;
		}
		
		return position;
	}
	
	public boolean isPlayerQueued(String player) {
		return isInReconnectQueue(player) || loginQueue.get(player) != null;
	}
	
	public boolean isInReconnectQueue(String player) {
		cleanupQueue();
		if( reconnectQueue.get(player) != null )
			return true;
		else
			return false;
	}
	
	/** Called when a player successfully logs in to empty them from any queues
	 * they might have been in.
	 * 
	 * @param player
	 */
	public void playerLoggedIn(String playerName) {
		debug.debug("playerLoggedIn(): removing player ",playerName," from queues");
		
		boolean wasRemoved = false;
		
		synchronized(LoginQueue.class) {
			if( reconnectQueue.remove(playerName) != null )
				wasRemoved = true;
			if(	loginQueue.remove(playerName) != null )
				wasRemoved = true;
		}
		
		if( wasRemoved && plugin.getConfig().getBoolean("verbose", true) ) {
			log.info(logPrefix+"Player "+playerName+" logged in and removed from queue");
		}
	}
	
	private int getPlayerRank(String player) {
		int currentRank = 0;
		
		ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_GLOBAL+"queueRankPerms");
		
		Set<String> ranks = null;
		if( section != null )
			 ranks = section.getKeys(false);
		
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
		
		debug.debug("getPlayerRank: player=",player," rank=",currentRank);
		return currentRank;
	}
	
	/** Administrative method for manually clearing the queues.
	 * 
	 */
	public void clearQueues() {
		synchronized(LoginQueue.class) {
			loginQueue.clear();
			reconnectQueue.clear();
		}
	}
	
	public Set<String> getQueuedPlayers() {
		return loginQueue.keySet();
	}
	public Set<String> getReconnectQueuedPlayers() {
		return reconnectQueue.keySet();
	}
	
	private void cleanupQueue() {
		// to avoid any performance hit from this method being called frequently,
		// we make sure we run at most once per second. This method is fast anyway
		// so this is just defensive coding on my part to make sure this can be
		// called in the accessor methods above without concern for performance.
		if( System.currentTimeMillis() - lastCleanup < 1000 )
			return;
		
		debug.debug("cleanupQueue running");
		
		// make sure we're thread safe, in case Bukkit allows multiple Player
		// login threads to run at once (not sure...)
		synchronized(LoginQueue.class) {
			FileConfiguration config = plugin.getConfig();
			int maxTime = config.getInt(CONFIG_GLOBAL+"queueMaxTime", 0) * 1000;
			int loginTime = config.getInt(CONFIG_GLOBAL+"queueLoginTime", 0) * 1000;
			int reconnectTime = config.getInt(CONFIG_GLOBAL+"reconnectTime", 0) * 1000;
			
			Set<Entry<String,Long>> reconnectEntrySet = reconnectQueue.entrySet();
			for(Iterator<Entry<String,Long>> i = reconnectEntrySet.iterator(); i.hasNext();) {
				Entry<String,Long> e = i.next();
				
				if( (System.currentTimeMillis() - e.getValue()) > reconnectTime )
					i.remove();
			}
			
			final long currentTime = System.currentTimeMillis();
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
					debug.debug("removing player from queue, p=",p,
							" firstLoginAttempt=",pInfo.firstLoginAttempt,
							" lastLoginAttempt=",pInfo.lastLoginAttempt,
							" currentTime=",currentTime);
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
	public boolean isEligible(String playerName, int freeCount) {
		if( playerName == null )
			return false;
		if( freeCount < 1 )
			return false;
		
		debug.debug("isEligible called for playerName ",playerName,", freeCount=",freeCount);
		
		PlayerInfo pInfo = loginQueue.get(playerName);
		
		// if they're in the reconnect queue, they are allowed on
		for(Entry<String,Long> entry : reconnectQueue.entrySet()) {
			String queuePlayerName = entry.getKey();
			debug.debug("checking queued player ",queuePlayerName," against current player ",playerName);
			if( playerName.equals(queuePlayerName) ) {
				debug.debug("player ",playerName," is in reconnectQueue, isElligible returning true");
				return true;
			}
		}
		
		// if they're not in the login queue, check to make sure they are in the regular
		// queue (should always be the case). If they're not in the queue at all, then
		// they are not eligible to login (they need to wait in the queue).
		if( pInfo == null ) {
			debug.debug("isEligible(): SHOULDN'T EVER HAPPEN: player ",playerName," is not in the queue!");
			return false;
		}
		
		// this method is called on login attempt, so update the lastLoginAttempt for this player
		pInfo.lastLoginAttempt = System.currentTimeMillis();
		
		cleanupQueue();

		int count = 0;
		for(Entry<String,PlayerInfo> entry : loginQueue.entrySet()) {
			if( playerName.equals(entry.getKey()) ) {
				debug.debug("player ",playerName," found in queue within an available slot, isElligible returning true");
				return true;
			}
			
			// if this player logging in has a higher rank than the player
			// we're checking in the queue, then player logging in skips the
			// lower ranked player in the queue
			if( pInfo.rank > entry.getValue().rank ) {
				debug.debug("player ",playerName," has higher rank than queue slot ",count,", ignoring that queue slot");
				continue;
			}
			
			// if we're over our freeCount limit, then this person is not yet eligible
			if( ++count >= freeCount ) {
				debug.debug("player ",playerName," found in queue beyond available slots, isElligible returning FALSE");
				return false;
			}
		}
		
		debug.debug("player ",playerName," not found in queue, but there are plenty of free slots available, isElligible returning true");
		// if we get here, we didn't hit the freeCount limit, so we are allowed to login
		return true;
	}
	
	class PlayerInfo {
		public long firstLoginAttempt;
		public long lastLoginAttempt;
		public int rank;
	}
}
