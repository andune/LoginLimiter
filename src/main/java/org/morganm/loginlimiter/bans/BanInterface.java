/**
 * 
 */
package org.morganm.loginlimiter.bans;

/**
 * @author morganm
 *
 */
public interface BanInterface {
	public boolean isBanned(String playerName, String playerIP);
	
	/** If the Ban implementation implements a cache, this method can be used
	 * to manage that cache externally (such as updating it real-time when
	 * someone is banned, rather than waiting for a callback).
	 * 
	 * @param playerName
	 * @param isBanned
	 * @return
	 */
	public void updateCache(String playerName, boolean isBanned);
}
