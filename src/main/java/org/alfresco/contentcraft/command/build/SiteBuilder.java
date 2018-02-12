package org.alfresco.contentcraft.command.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.alfresco.cmis.client.AlfrescoFolder;
import org.alfresco.contentcraft.cmis.CMIS;
import org.alfresco.contentcraft.command.CommandUsageException;
import org.alfresco.contentcraft.command.macro.MacroCallback;
import org.alfresco.contentcraft.command.macro.MacroCommandExecuter;
import org.alfresco.contentcraft.command.macro.PlaceBlockMacroAction;
import org.alfresco.contentcraft.repository.Room;
import org.alfresco.contentcraft.repository.RoomType;
import org.alfresco.contentcraft.rest.REST;
import org.alfresco.contentcraft.util.CommonUtil;
import org.alfresco.contentcraft.util.VectorUtil;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.Vector;

/**
 * Site builder implementation.
 * 
 * @author Roy Wetherall
 * @since 1.0
 */
public class SiteBuilder implements Builder 
{
	/** macro names */
	private static final String SITE_FOLDER_FRONT = "site.folder.front";
	private static final String SITE_FOLDER_MIDDLE = "site.folder.middle";
	private static final String SITE_FOLDER_BACK = "site.folder.back";
	private static final String SITE_FOLDER_PLATFORM = "site.folder.platform";
	private static final String SITE_SUBFOLDER_RIGHT = "site.subfolder.right";
	private static final String SITE_SUBFOLDER_LEFT = "site.subfolder.left";
	
	private static final String[] FILES = new String[]
	{
		SITE_FOLDER_BACK,
		SITE_FOLDER_FRONT,
		SITE_FOLDER_MIDDLE,
		SITE_FOLDER_PLATFORM,
		SITE_SUBFOLDER_LEFT,
		SITE_SUBFOLDER_RIGHT
	};
	
	/** sign values */
	private static final int NUMBER_OF_LINES = 4;
	private static final int LINE_LEN = 15;
	
	// TODO find a better solution
	private boolean macrosLoaded = false;
	
	/**
	 * @return	{@link MacroCommandExecuter}	macro command executer
	 */
	private void loadMacros()
	{
	    if (macrosLoaded == false)
	    {
	    	try
	    	{
	    		for (String file : FILES) 
	    		{
					InputStream is = getClass().getClassLoader().getResourceAsStream("macros/" + file + ".json");
		    		try
		    		{
		    			InputStreamReader reader = new InputStreamReader(is);
		    			MacroCommandExecuter.getInstance().load(reader);
		    		}
		    		finally
		    		{
		    			is.close();
		    		}
	    		}
	    	}
	    	catch (IOException exception)
	    	{
	    		// just hack the error out
	    		exception.printStackTrace();
	    	}
	    	finally
	    	{
	    		macrosLoaded = true;
	    	}
	    }
	}
	
	/**
	 * @see org.alfresco.contentcraft.command.build.Builder#getName()
	 */
	public String getName() 
	{
		return "site";
	}

	/**
	 * @see org.alfresco.contentcraft.command.build.Builder#build(org.bukkit.entity.Player, org.bukkit.Location, org.bukkit.util.Vector, java.lang.String[])
	 */
	public void build(Player player, Location start, Vector direction, String... args) throws CommandUsageException
	{
		// get the site we want to use as a base
		String siteName = args[1];
		if (siteName == null || siteName.length() == 0)
		{
			throw new CommandUsageException("You didn't provide a site name!");
		}
		
		/// load the macros
		loadMacros();
		
		// grab the document lib for the site
		Session session = CMIS.getSession();
		AlfrescoFolder siteRoot = CMIS.getSiteRoot(session, siteName);
		if (siteRoot == null)
		{
			throw new CommandUsageException("The site (" + siteName + ") you provided couldn't be found!");
		}
		
		spawnSiteMembers(player, siteName);

		// build the root folders
		buildRootFolders(start, siteRoot.getChildren());			
		
	}
	
	/**
	 * Spawn the site members
	 * 
	 * @param player
	 * @param siteName
	 */
	private void spawnSiteMembers(Player player, String siteName) {
		try {
			List<String> members = REST.getMembers(siteName, REST.getTicket());
			int count = 1;
			for (String member : members) {
		        Location startLocation = player.getLocation().clone().add(VectorUtil.SOUTH.clone().multiply(2*count));
		        Villager other = (Villager) player.getWorld().spawnEntity(startLocation, EntityType.VILLAGER);
		        other.setCustomName(ChatColor.GOLD + member);
		        other.setCustomNameVisible(true);
		        count++;
		        System.out.println("Villager " + member + " has been spawned at " + startLocation.getX() + "," + startLocation.getY() + "," + startLocation.getZ());
			}
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
		
	}
	
	/**
	 * Build the root folders 
	 * 
	 * @param folderFrontLocation
	 * @param docLibTree
	 */
	private void buildRootFolders(Location start, ItemIterable<CmisObject> items)
	{
		Location startLocation = start.clone().add(VectorUtil.NORTH.clone().multiply(10));
				
		for (CmisObject item : items) 
		{
			if (item instanceof AlfrescoFolder)
			{
				// build root folder				
				buildRootFolder(startLocation, (AlfrescoFolder)item);			
				
				// move to the next folder location
				startLocation.add(VectorUtil.UP.clone().multiply(5));
			}
		}		
	}
	
	/**
	 * 
	 * @param start
	 * @param folder
	 */
	private void buildRootFolder(Location start, AlfrescoFolder folder)
	{				
		Location startClone = start.clone();
		
		final String[] messages = 
		{
			folder.getName(),
			"Created By     " + folder.getPropertyValue(PropertyIds.CREATED_BY)
		};
		
		// execute the folder front macro
		MacroCommandExecuter.run(SITE_FOLDER_FRONT, startClone, new MacroCallback() 
		{
			/** sign count */
			int signCount = 0;
			
			/**
			 * @see org.alfresco.contentcraft.command.macro.MacroCallback#placeBlock(org.bukkit.block.Block)
			 */
			public void callback(String macroAction, Block block) 
			{
				if (PlaceBlockMacroAction.NAME.equals(macroAction))
				{
					// set the messages on the signs
					if (Material.WALL_SIGN.equals(block.getType()))
					{
						setSignMessage(block, messages[signCount]);
						signCount ++;
					}
				}
			}					
		});	
		
		// build the folder platform
		Location platformStart = startClone.clone().add(VectorUtil.SOUTH.clone().multiply(2));
		MacroCommandExecuter.run(SITE_FOLDER_PLATFORM, platformStart);
		
		// grab all sub-folders
		List<Folder> folders = new ArrayList<Folder>(21);
		ItemIterable<CmisObject> children = folder.getChildren();
		for (CmisObject cmisObject : children) 
		{
			if (cmisObject instanceof Folder)
			{
				// add to folder list
				folders.add((Folder)cmisObject);
			}
		}
		
		// build a middle section for each pair of folders
		Location middleClone = startClone.clone().add(VectorUtil.NORTH.clone().multiply(4));
		for (int i = 0; i < folders.size(); i++) 
		{
			Folder folder1 = folders.get(i);
			Folder folder2 = null;
			
			
			String folder2Name = "";
			String folder2CreatedBy = "";
			if (i+1 < folders.size())
			{
				folder2 = folders.get(i+1);
				folder2Name = folder2.getName();
				folder2CreatedBy = "Created By     " + folder.getPropertyValue(PropertyIds.CREATED_BY);
			}
						
			final String[] signs = 
			{
				folders.get(i).getName(),
				"Created By     " + folder.getPropertyValue(PropertyIds.CREATED_BY),
				folder2Name,
				folder2CreatedBy
			};
			
			MacroCommandExecuter.run(SITE_FOLDER_MIDDLE, middleClone, new MacroCallback() 
			{
				/** sign count */
				int signCount = 0;
				
				/**
				 * @see org.alfresco.contentcraft.command.macro.MacroCallback#placeBlock(org.bukkit.block.Block)
				 */
				public void callback(String macroAction, Block block) 
				{
					if (PlaceBlockMacroAction.NAME.equals(macroAction))
					{
						// set the messages on the signs
						if (Material.WALL_SIGN.equals(block.getType()))
						{
							setSignMessage(block, signs[signCount]);
							signCount ++;
						}
					}
				}					
			});		
						
			//buildSubFolder(folder1, middleClone, SubFolderOrinitation.LEFT);
			new Room(folder1, middleClone, RoomType.ROOM_LEFT).build();
			i++;
			if (i < folders.size())
			{
				//buildSubFolder(folder2, middleClone, SubFolderOrinitation.RIGHT);
	            new Room(folder2, middleClone, RoomType.ROOM_RIGHT).build();
			}
			middleClone.add(VectorUtil.NORTH.clone().multiply(8));
		}
		
		// build the end section
		MacroCommandExecuter.run(SITE_FOLDER_BACK, middleClone);
	}

	
	/**
	 * Divide the string over the four available lines on the given 
	 * sign.
	 * 
	 * @param block		sign block
	 * @param message	message
	 */
	private void setSignMessage(Block block, String message)
	{
		if (message != null && !message.isEmpty())
		{
			org.bukkit.block.Sign sign = (org.bukkit.block.Sign)(block.getState());	
	
			List<String> messages = CommonUtil.split(message, NUMBER_OF_LINES, LINE_LEN);
			for (int index = 0; index < messages.size(); index++)
			{
				sign.setLine(index, messages.get(index));
			}
			
			sign.update();
		}
	}
	
	
}
