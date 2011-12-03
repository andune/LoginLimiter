/**
 * 
 */
package org.morganm.loginlimiter;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author morganm
 *
 */
public class LoginLimiter extends JavaPlugin {
	public static final Logger log = Logger.getLogger(LoginLimiter.class.toString());
	public static final String logPrefix = "[LoginLimiter] ";
	
	public static final String CONFIG_GROUP_LIMIT = "groupLimits.";
	public static final String CONFIG_GLOBAL = "global.";
	
    private Permission vaultPermission = null;
    private LoginQueue loginQueue;
	private String version;

	@Override
	public void onEnable() {
		version = getDescription().getVersion();
		setupVaultPermissions();
		loginQueue = new LoginQueue(this);
		
		getServer().getPluginManager().registerEvent(Type.PLAYER_LOGIN, new MyPlayerListener(this), Priority.Lowest, this);
		getServer().getPluginManager().registerEvent(Type.PLAYER_QUIT, new MyPlayerListener(this), Priority.Lowest, this);
		
		log.info(logPrefix + "version "+version+" is enabled");
	}

	@Override
	public void onDisable() {
		log.info(logPrefix + "version "+version+" is disabled");
	}
	
	public LoginQueue getLoginQueue() { return loginQueue; }

    private Boolean setupVaultPermissions()
    {
    	Plugin vault = getServer().getPluginManager().getPlugin("Vault");
    	if( vault != null ) {
	        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
	        if (permissionProvider != null) {
	            vaultPermission = permissionProvider.getProvider();
	        }
    	}
    	// we don't print any errors on "else" because we just fall back to our own perms code
    	// at this point and no functionality is lost.
    	
        return (vaultPermission != null);
    }
    
    public boolean isNewPlayer(Player p) {
    	String playerDat = p.getName() + ".dat";
    	
    	// start with the easy, most likely check
    	File file = new File("world/players/"+playerDat);
    	if( file.exists() )
    		return false;
    	
    	/* It seems all player files go to the defaultWorld, so this part is unnecessary.
    	 * Not sure if it's possible to change the default world, should probably look
    	 * into that and adjust the above check accordingly.
    	 * 
    	// failing that, check all worlds
    	List<World> worlds = getServer().getWorlds();
    	for(World w : worlds) {
    		file = new File(w.getName()+"/players/"+playerDat);
    		if( file.exists() )
    			return false;
    	}
    	*/
    	
    	// if we didn't find any record of this player on any world, they must be new
    	return true;
    }
    
    /** Check to see if player has a given permission.
     * 
     * @param p The player
     * @param permission the permission to be checked
     * @return true if the player has the permission, false if not
     */
    public boolean has(Player p, String permission) {
    	if( vaultPermission != null )
    		return vaultPermission.has(p, permission);
    	else
    		return p.hasPermission(permission);		// fall back to superperms
    }
    
    /*
    public boolean isInGroup(Player p, String group) {
    	if( group == null )
    		return false;
    	
    	if( vaultPermission != null )
    		return group.equals(vaultPermission.getPrimaryGroup(p));
    	else
    		return false;
    }
    */
}
