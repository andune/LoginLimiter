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
}
