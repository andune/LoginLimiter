/**
 * 
 */
package org.morganm.loginlimiter.bans;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.mcbans.firestar.mcbans.BukkitInterface;

/**
 * @author morganm
 *
 */
public class BanFactory {
	
	public static BanInterface getBanObject() {
		BanInterface ban = null;
		Plugin plug = Bukkit.getServer().getPluginManager().getPlugin("mcbans");
		if( plug != null ) {
			BukkitInterface mcbans = (BukkitInterface) plug;
			ban = new MCBansImpl(mcbans);
		}
		return ban;
	}
}
