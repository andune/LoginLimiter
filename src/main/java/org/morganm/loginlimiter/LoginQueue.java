/**
 * 
 */
package org.morganm.loginlimiter;

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author morganm
 *
 */
public class LoginQueue {
	private final LinkedHashMap<String, Long> loginQueue = new LinkedHashMap<String, Long>();
	private LoginLimiter plugin;
	
	public LoginQueue(LoginLimiter plugin) {
		this.plugin = plugin;
	}
	
	public void addQueuedPlayer(String playerName) {
		loginQueue.put(playerName, System.currentTimeMillis());
	}
	
	private void cleanupQueue() {
		
	}
	
	/** Check the queue to see if a player is eligible to login.
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
		
		cleanupQueue();
		
		int count = 0;
		for(Entry<String,Long> entry : loginQueue.entrySet()) {
			if( playerName.equals(entry.getKey()) )
				return true;
			
			if( ++count > freeCount )
		}
	}
}
