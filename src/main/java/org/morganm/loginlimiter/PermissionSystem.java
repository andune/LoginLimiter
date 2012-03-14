package org.morganm.loginlimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;
import com.sk89q.wepif.PermissionsResolverManager;

/** Permission abstraction class, use Vault, WEPIF, Perm2 or superperms, depending on what's available.
 * 
 * Dependencies: (to be handled through maven when I setup a repository some day..)
 *   Vault 1.x: http://dev.bukkit.org/server-mods/vault/
 *   WorldEdit 5.x: http://build.sk89q.com/
 *   PermissionsEx: http://goo.gl/jthCz
 *   Permissions 2.7 or 3.x: http://goo.gl/liHFt (2.7) or http://goo.gl/rn4LP (3.x) 
 *   
 * Author's note: The "ideal" design would be to setup an interface class and let each permission
 *   type implement that interface and use polymorphism. In fact, that's how Vault and WEPIF work.
 *   However, the design goal for this class is to have a single class I can use between projects
 *   that implements permissions abstraction, thus the less-than-great C-style integer values,
 *   switch statements and if/else ladders.
 * 
 * @author morganm
 *
 */
public class PermissionSystem {
	// class version: 11
	public static final int SUPERPERMS = 0x00;		// default
	public static final int VAULT = 0x01;
	public static final int WEPIF = 0x02;
	public static final int PERM2_COMPAT = 0x04;
	public static final int PEX = 0x08;
	public static final int OPS = 0x10;
	
    private static final String GROUP_PREFIX = "group.";

	private static PermissionSystem instance;
	
	private final JavaPlugin plugin;
	private final Logger log;
	private final String logPrefix;
	private int systemInUse;
	
    private net.milkbowl.vault.permission.Permission vaultPermission = null;
    private PermissionsResolverManager wepifPerms = null;
    private PermissionHandler perm2Handler;
    private PermissionsEx pex;
    
	public PermissionSystem(JavaPlugin plugin, Logger log, String logPrefix) {
		this.plugin = plugin;
		if( log != null )
			this.log = log;
		else
			this.log = Logger.getLogger(PermissionSystem.class.toString());
		
		if( logPrefix != null ) {
			if( logPrefix.endsWith(" ") )
				this.logPrefix = logPrefix;
			else
				this.logPrefix = logPrefix + " ";
		}
		else
			this.logPrefix = "["+plugin.getDescription().getName()+"] ";
		
		instance = this;
	}
	
	/** **WARNING** Not your typical singleton pattern, this CAN BE NULL. An instance
	 * must be created by the plugin before this will return a value.
	 * 
	 * @return
	 */
	public static PermissionSystem getInstance() {
		return instance;
	}
	
	public int getSystemInUse() { return systemInUse; }
	
	public void setupPermissions() {
		setupPermissions(true);
	}
	public void setupPermissions(final boolean verbose) {
		List<String> permPrefs = null;
		if( plugin.getConfig().get("permissions") != null ) {
			permPrefs = plugin.getConfig().getStringList("permissions");
		}
		else {
			permPrefs = new ArrayList<String>(5);
			permPrefs.add("vault");
			permPrefs.add("wepif");
			permPrefs.add("pex");
			permPrefs.add("perm2-compat");
			permPrefs.add("superperms");
			permPrefs.add("ops");
		}
		
		for(String system : permPrefs) {
			if( "vault".equalsIgnoreCase(system) ) {
				if( setupVaultPermissions() ) {
					systemInUse = VAULT;
					if( verbose )
						log.info(logPrefix+"using Vault permissions");
					break;
				}
			}
			else if( "wepif".equalsIgnoreCase(system) ) {
				if( setupWEPIFPermissions() ) {
					systemInUse = WEPIF;
					if( verbose )
						log.info(logPrefix+"using WEPIF permissions");
					break;
				}
			}
			else if( "pex".equalsIgnoreCase(system) ) {
				if( setupPEXPermissions() ) {
					systemInUse = PEX;
					if( verbose )
						log.info(logPrefix+"using PEX permissions");
					break;
				}
			}
			else if( "perm2".equalsIgnoreCase(system) || "perm2-compat".equalsIgnoreCase(system) ) {
				if( setupPerm2() ) {
					systemInUse = PERM2_COMPAT;
					if( verbose )
						log.info(logPrefix+"using Perm2-compatible permissions");
					break;
				}
			}
			else if( "superperms".equalsIgnoreCase(system) ) {
				systemInUse = SUPERPERMS;
				if( verbose )
					log.info(logPrefix+"using Superperms permissions");
	        	break;
			}
			else if( "ops".equalsIgnoreCase(system) ) {
				systemInUse = OPS;
				if( verbose )
					log.info(logPrefix+"using basic Op check for permissions");
	        	break;
			}
		}
	}
	
    /** Check to see if player has a given permission.
     * 
     * @param p The player
     * @param permission the permission to be checked
     * @return true if the player has the permission, false if not
     */
    public boolean has(CommandSender sender, String permission) {
    	Player p = null;
    	// console always has access
    	if( sender instanceof ConsoleCommandSender )
    		return true;
    	if( sender instanceof Player )
    		p = (Player) sender;
    	
    	if( p == null )
    		return false;
    	
    	boolean permAllowed = false;
    	switch(systemInUse) {
    	case VAULT:
    		permAllowed = vaultPermission.has(p, permission);
    		break;
    	case WEPIF:
    		permAllowed = wepifPerms.hasPermission(p.getName(), permission);
    		break;
    	case PEX:
    		permAllowed = pex.has(p, permission);
    		break;
    	case PERM2_COMPAT:
    		permAllowed = perm2Handler.has(p, permission);
    		break;
    	case SUPERPERMS:
    		permAllowed = p.hasPermission(permission);
    		break;
    	case OPS:
    		permAllowed = p.isOp();
    		break;
    	}
    	
    	return permAllowed;
    }
    
    public boolean has(String world, String player, String permission) {
    	boolean permAllowed = false;
    	switch(systemInUse) {
    	case VAULT:
    		permAllowed = vaultPermission.has(world, player, permission);
    		break;
    	case WEPIF:
    		permAllowed = wepifPerms.hasPermission(player, permission);
    		break;
    	case PEX:
            PermissionUser user = PermissionsEx.getPermissionManager().getUser(player);
            if (user != null)
            	permAllowed = user.has(permission, world);
    		break;
    	case PERM2_COMPAT:
    		permAllowed = perm2Handler.has(world, player, permission);
    		break;
    	case SUPERPERMS:
    	{
    		Player p = plugin.getServer().getPlayer(player);
			// technically this is not guaranteed to be accurate since superperms
			// doesn't support checking cross-world perms. Upgrade to a better
			// perm system if you care about this.
    		if( p != null )
    			permAllowed = p.hasPermission(permission);
    		break;
    	}
    	case OPS:
		{
    		Player p = plugin.getServer().getPlayer(player);
    		if( p != null )
    			permAllowed = p.isOp();
    		break;
    	}
    	}

    	return permAllowed;
    }
    public boolean has(String player, String permission) {
    	return has("world", player, permission);
    }
    
	public String getPlayerGroup(String world, String playerName) {
    	String group = null;
    	
    	switch(systemInUse) {
    	case VAULT:
    		group = vaultPermission.getPrimaryGroup(world, playerName);
    		break;
    	case WEPIF:
    	{
    		String[] groups = wepifPerms.getGroups(playerName);
    		if( groups != null && groups.length > 0 )
    			group = groups[0];
    		break;
    	}
    	case PEX:
    	{
            PermissionUser user = PermissionsEx.getPermissionManager().getUser(playerName);
            if (user != null) {
            	String[] groups = user.getGroupsNames();
        		if( groups != null && groups.length > 0 )
        			group = groups[0];
            }
    		break;
    	}
    	case PERM2_COMPAT:
    		group = perm2Handler.getGroup(world, playerName);
    		break;
    	
    	case SUPERPERMS:
    	{
    		Player player = plugin.getServer().getPlayer(playerName);
    		group = getSuperpermsGroup(player);
    		break;
    	}
            
        // OPS has no group support
    	case OPS:
    		break;
    	}
    	
    	return group;
    }
	
	/** Superperms has no group support, but we fake it (this is slow and stupid since
	  * it has to iterate through ALL permissions a player has).  But if you're
	  * attached to superperms and not using a nice plugin like bPerms and Vault
	  * then this is as good as it gets)
	  * 
	  * @param player
	  * @return the group name or null
	  */
	private String getSuperpermsGroup(Player player) {
		if( player == null )
			return null;
		
		String group = null;
		
		// this code shamelessly adapted from WorldEdit's WEPIF support for superperms
        Permissible perms = getPermissible(player);
        if (perms != null) {
            for (PermissionAttachmentInfo permAttach : perms.getEffectivePermissions()) {
                String perm = permAttach.getPermission();
                if (!(perm.startsWith(GROUP_PREFIX) && permAttach.getValue())) {
                    continue;
                }
                
                // we just grab the first "group.XXX" permission we can find
                group = perm.substring(GROUP_PREFIX.length(), perm.length());
                break;
            }
        }
        
        return group;
	}

	/** This code shamelessly stolen from WEPIF in order to support a fake "group"
	 * notion for Superperms.
	 * 
	 * @param offline
	 * @return
	 */
    private Permissible getPermissible(OfflinePlayer offline) {
        if (offline == null) return null;
        Permissible perm = null;
        if (offline instanceof Permissible) {
            perm = (Permissible) offline;
        } else {
            Player player = offline.getPlayer();
            if (player != null) perm = player;
        }
        return perm;
    }

    private boolean setupPerm2() {
        Plugin permissionsPlugin = plugin.getServer().getPluginManager().getPlugin("Permissions");
        if( permissionsPlugin != null ) {
        	perm2Handler = ((Permissions) permissionsPlugin).getHandler();
        }
        	
        return (perm2Handler != null);
    }
    
    private boolean setupVaultPermissions()
    {
    	Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
    	if( vault != null ) {
	        RegisteredServiceProvider<net.milkbowl.vault.permission.Permission> permissionProvider = plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
	        if (permissionProvider != null) {
	            vaultPermission = permissionProvider.getProvider();
	        }
    	}
    	
        return (vaultPermission != null);
    }
    
    private boolean setupWEPIFPermissions() {
    	try {
	    	Plugin worldEdit = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
	    	String version = null;
	    	int versionNumber = 0;
	    	
	    	try {
		    	version = worldEdit.getDescription().getVersion();

		    	// version "4.7" is equivalent to build #379
		    	if( "4.7".equals(version) )
		    		versionNumber = 379;
		    	// version "5.0" is equivalent to build #670
		    	else if( "5.0".equals(version) )
		    		versionNumber = 670;
		    	else if( version.startsWith("5.") )		// 5.x series
		    		versionNumber = 840;
		    	else {
			    	int index = version.indexOf('-');
			    	versionNumber = Integer.parseInt(version.substring(0, index));
		    	}
	    	}
	    	catch(Exception e) {}	// catch any NumberFormatException or anything else
	    	
//	    	System.out.println("WorldEdit version: "+version+", number="+versionNumber);
	    	if( versionNumber < 660 ) {
	    		log.info(logPrefix + "You are currently running version "+version+" of WorldEdit. WEPIF was changed in #660, please update to latest WorldEdit. (skipping WEPIF for permissions)");
	    		return false;
	    	}

	    	if( worldEdit != null ) {
	    		wepifPerms = PermissionsResolverManager.getInstance();
//	    		wepifPerms.initialize(plugin);
//		    	wepifPerms = new PermissionsResolverManager(this, "LoginLimiter", log);
//		    	(new PermissionsResolverServerListener(wepifPerms, this)).register(this);
	    	}
    	}
    	catch(Exception e) {
    		log.info(logPrefix + " Unexpected error trying to setup WEPIF permissions hooks (this message can be ignored): "+e.getMessage());
    	}
    	
    	return wepifPerms != null;
    }
    
    private boolean setupPEXPermissions() {
    	try {
            Plugin perms = plugin.getServer().getPluginManager().getPlugin("PermissionsEx");
	    	if( perms != null ) {
	    		pex = (PermissionsEx) perms;
	    	}
    	}
    	catch(Exception e) {
    		log.info(logPrefix + " Unexpected error trying to setup PEX permissions: "+e.getMessage());
    	}
    	
    	return pex != null;
    }
}
