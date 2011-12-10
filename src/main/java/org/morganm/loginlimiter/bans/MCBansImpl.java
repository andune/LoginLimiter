/**
 * 
 */
package org.morganm.loginlimiter.bans;

import java.util.HashMap;

import com.mcbans.firestar.mcbans.BukkitInterface;
import com.mcbans.firestar.mcbans.pluginInterface.Connect;

/**
 * @author morganm
 *
 */
public class MCBansImpl implements BanInterface {
	private BukkitInterface mcbans;
	
	private HashMap<String, Boolean> banCache = new HashMap<String,Boolean>();
	
	public MCBansImpl(BukkitInterface mcbans) {
		this.mcbans = mcbans;
	}

	/* (non-Javadoc)
	 * @see org.morganm.loginlimiter.bans.BanInterface#isBanned(java.lang.String)
	 */
	@Override
	public boolean isBanned(String playerName, String playerIP) {
		if( !mcbans.isEnabled() || !mcbans.isInitialized() )
			return false;
		
		Boolean banned = banCache.get(playerName);
		if( banned == null )
			banned = Boolean.FALSE;
		else if( banned )
			return true;
		
		Connect playerConnect = new Connect( mcbans );
		String result = playerConnect.exec( playerName, playerIP );
		if( result != null ){
			banned = Boolean.TRUE;
			banCache.put(playerName, banned);
		}
		
		return banned.booleanValue();
	}

}
