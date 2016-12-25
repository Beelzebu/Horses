/*
 * Copyright 2013 Michael McKnight. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */
 
package com.forgenz.horses;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import com.forgenz.forgecore.v1_0.ForgeCore;
import com.forgenz.horses.util.HorseSpeedUtil;

public class PlayerHorse implements ForgeCore
{
	private static final String OWNERSHIP_METADATA_KEY = "Horses.Ownership"; 
	private static final Location cacheLoc = new Location(null, 0.0, 0.0, 0.0);
	
	public static final Pattern FORMATTING_CODES_PATTERN = Pattern.compile("&[klmnor]", Pattern.CASE_INSENSITIVE);
	
	private final Horses plugin;
	private final Stable stable;
	private int id;
	
	private AbstractHorse horse;
	
	private long lastDeath = 0;
	
	private String name;
	private String displayName;
	
	private HorseType type;
	private double maxHealth;
	private double health;
	
	private double speed;
	private double jumpStrength;

	private boolean hasChest = false;
	
	private final ArrayList<ItemStack> inventory = new ArrayList<ItemStack>();
	
	public PlayerHorse(Horses plugin, Stable stable, String name, HorseType type, double maxHealth, double health, double speed, double jumpStrength, AbstractHorse horse)
	{
		this(plugin, stable, name, type, maxHealth, health, speed, jumpStrength, horse, -1);
	}
	
	public PlayerHorse(Horses plugin, Stable stable, String name, HorseType type, double maxHealth, double health, double speed, double jumpStrength, AbstractHorse horse, int id)
	{
		this.plugin = plugin;
		this.stable = stable;
		this.id = id;
		
		this.displayName = ChatColor.translateAlternateColorCodes('&', name).replaceAll("&", "").replaceAll(" ", "_").replaceAll("\\.", "");
		this.name = ChatColor.stripColor(this.displayName);
		
		this.type = type;
		
		if (health <= 0)
			health = getPlugin().getHorsesConfig().getHorseTypeConfig(null, type).horseHp;
		if (health > maxHealth || maxHealth <= 0)
			maxHealth = health;
			
		this.maxHealth = maxHealth;
		this.health = health;
		
		if (getPlugin().getHorsesConfig().fixZeroJumpStrength && jumpStrength <= 0.0)
			jumpStrength = 0.7;
		
		this.speed = speed;
		this.jumpStrength = jumpStrength;
		
		this.horse = horse;
		
		if (this.horse != null)
		{
			getMaxHealth();
			getHealth();
			hasChest();
			getItems();
			getSpeed();
			getJumpStrength();
			this.horse = null;
			horse.remove();
		}
	}
	
	public Stable getStable()
	{
		return stable;
	}

	@Override
	public Horses getPlugin()
	{
		return plugin;
	}
	
	public AbstractHorse getHorse()
	{
		return horse;
	}
	
	public String getName()
	{
		return name;
	}
	
	public String getDisplayName()
	{
		return displayName;
	}
	
	public HorseType getType()
	{
		return type;
	}
	
	public long getLastDeath()
	{
		return lastDeath;
	}
	
	public void setLastDeath(long time)
	{
		lastDeath = time;
	}
	
	public int getId()
	{
		return id;
	}
	
	public void setId(int id)
	{
		this.id = id;
	}
	
	public double getMaxHealth()
	{
		if (horse != null && horse.isValid())
		{
			maxHealth = horse.getMaxHealth();
		}
		return maxHealth;
	}
	
	public double getHealth()
	{
		if (horse != null && horse.isValid())
		{
			health = horse.getHealth();
		}
		return health;
	}
	
	public double getHealEstimate(float amount)
	{
		if (getMaxHealth() < getHealth() + amount)
		{
			return maxHealth - health;
		}
		
		return amount;
	}
	
	public void setMaxHealth(double amount)
	{
		maxHealth = amount;
		
		if (horse != null)
		{
			AttributeInstance maxHealth = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH);
			if (maxHealth != null)
				maxHealth.setBaseValue(amount);
		}
	}
	
	public void setHealth(double amount)
	{
		health = amount;
		
		if (horse != null)
		{
			horse.setHealth(amount);
		}
	}
	
	public double addHealth(double amount)
	{
		if (getMaxHealth() < getHealth() + amount)
		{
			amount = maxHealth - health;
		}
		
		health += amount;
		
		if (horse != null && horse.isValid())
		{
			horse.setHealth(health);
		}
		
		return amount;
	}
	
	public void addMaxHealth(double amount)
	{
		maxHealth = getMaxHealth() + amount;
		health = getHealth() + amount;
		
		if (horse != null && horse.isValid())
		{
			horse.setMaxHealth(maxHealth);
			horse.setHealth(health);
		}
	}
	
	public double getJumpStrength()
	{
		if (horse != null)
		{
			jumpStrength = horse.getJumpStrength();
		}
		
		return jumpStrength;
	}
	
	public double getSpeed()
	{
		if (horse != null)
		{
			speed = HorseSpeedUtil.getHorseSpeed(horse);
		}
		
		return speed;
	}

	public void setSaddle(Material saddle)
	{
		setItem(0, new ItemStack(saddle));
	}
		
	public void setHasChest(boolean hasChest)
	{
		if (type != HorseType.Mule && type != HorseType.Donkey)
			return;
		
		this.hasChest = hasChest;
	}
	
	public boolean hasChest()
	{
		if (type != HorseType.Mule && type != HorseType.Donkey)
			return false;
		
		if (horse != null && horse instanceof Donkey)
		{
			hasChest = ((Donkey) horse).isCarryingChest();
		}
		
		return hasChest;
	}
	
	public void setArmour(Material material)
	{
		setItem(1, new ItemStack(material));
	}
	
	public ItemStack getItem(int i)
	{
		if (inventory.size() <= i && i < 0)
			return null;
		
		if (horse != null)
		{
			ItemStack item = horse.getInventory().getItem(i);
			if (item != null && item.getType() == Material.AIR)
				item = null;
			
			inventory.set(i, item);
		}
		
		return inventory.get(i);
	}
	
	public void setItem(int i, ItemStack item)
	{
		if (i < 0)
			return;
		
		if (inventory.size() <= i)
		{
			for (int index = inventory.size(); index < i; ++index)
				inventory.add(null);
		}
		
		inventory.add(item);
	}
	
	public void setItems(ItemStack[] items)
	{
		inventory.clear();
		inventory.ensureCapacity(items.length);
		
		for (ItemStack item : items)
			inventory.add(item);
	}
	
	public ItemStack[] getItems()
	{
		if (horse != null)
		{
			ItemStack[] items = horse.getInventory().getContents();
			
			inventory.clear();
			for (ItemStack item : items)
				inventory.add(item);
			
			return items;
		}
		
		return inventory.toArray(new ItemStack[inventory.size()]);
	}
	
	public void removeHorse()
	{
		if (horse != null)
		{
			if (!horse.isDead())
			{
				health = horse.getHealth();
			}

			getItems();
			hasChest();
			
			// Handle the horses death
			if (horse.isDead())
			{
				if (getPlugin().getHorsesConfig().getPermConfig(getStable().getPlayerOwner()).keepEquipmentOnDeath)
				{
					horse.getInventory().clear();
					if (horse instanceof Donkey)
						((Donkey) horse).setCarryingChest(false);
				}
				else
				{
					inventory.clear();
					hasChest = false;
				}
			}
			
			horse.remove();
			
			stable.removeActiveHorse(this);
			
			horse.removeMetadata(OWNERSHIP_METADATA_KEY, getPlugin());
			horse = null;
		}
		
		saveChanges();
	}
	
	public boolean spawnHorse(Player player)
	{
		if (!player.getName().equals(getStable().getOwner()))
		{
			return false;
		}
		
		if (horse != null && horse.isValid())
		{
			horse.teleport(player);
			return true;
		}
		
		// Remove the old horse
		if (getStable().getActiveHorse() != null)
		{
			getStable().getActiveHorse().removeHorse();
		}
		
		Location loc = player.getLocation(cacheLoc);
		
		// Prevent the horse from not being spawned
		if (getPlugin().getHorsesConfig().getPermConfig(getStable().getPlayerOwner()).bypassSpawnProtection)
			getPlugin().getHorseSpawnListener().setSpawning();

		EntityType entityType = getType().getVariantType();
		AbstractHorse horse = (AbstractHorse) loc.getWorld().spawnEntity(loc, entityType);
		
		if (horse != null)
		{
			if (entityType == EntityType.HORSE)
			{
				// Setup the horses type
				getType().setHorseType((Horse) horse);
			}

			if (entityType == EntityType.LLAMA) {
				getType().setHorseType((Llama) horse);
			}
			
			horse.setAdult();
			horse.setTamed(true);
			horse.setOwner(getStable().getPlayerOwner());

			if (entityType == EntityType.DONKEY && hasChest())
			{
				Donkey donkey = (Donkey) horse;
				// Check if it has a chest?
				if (hasChest())
					donkey.setCarryingChest(true);
			}
			
			// Setup the horses inventory
			Inventory inv = horse.getInventory();
			
			ItemStack[] items = inv.getContents();
			for (int i = 2; i < items.length; ++i) {
				if (i >= inventory.size())
					items[i] = null;
				else
					items[i] = inventory.get(i);
			}
			inv.setContents(items);

			if (entityType == EntityType.HORSE) {
				Horse normalHorse = (Horse) horse;
				HorseInventory horseInv = normalHorse.getInventory();
				// Temp fix for saddles/armour
				if (inventory.size() > 0)
					horseInv.setSaddle(inventory.get(0));
				if (inventory.size() > 1)
					horseInv.setArmor(inventory.get(1));
			}
			
			// Set the horses name
			horse.setCustomName(displayName);
			horse.setCustomNameVisible(true);
			
			// Set the horses HP
			AttributeInstance maxHealthAttr = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH);
			if (maxHealthAttr != null)
				maxHealthAttr.setBaseValue(maxHealth);
			horse.setHealth(health);
			
			HorseSpeedUtil.setHorseSpeed(horse, getSpeed());
			horse.setJumpStrength(getJumpStrength());
			
			// Make the horse follow the player
			horse.setTarget(player);
			
			// Setup the horses metadata
			horse.setMetadata(OWNERSHIP_METADATA_KEY, new FixedMetadataValue(getPlugin(), this));
			
			getStable().setActiveHorse(this);
			
			this.horse = horse;
			
			return true;
		}
		
		// Boo hoo, something went wrong :(
		return false;
	}
	
	public void saveChanges()
	{
		getPlugin().getHorseDatabase().saveHorse(this);
	}
	
	public boolean deleteHorse()
	{
		if (horse != null)
		{
			horse.remove();
			getStable().removeActiveHorse(this);
		}
		
		return stable.deleteHorse(this);
	}
	
	public void rename(String name)
	{
		this.displayName = ChatColor.translateAlternateColorCodes('&', name).replaceAll("&", "").replaceAll(" ", "_");
		this.name = ChatColor.stripColor(this.displayName);
		
		if (horse != null && horse.isValid())
		{
			horse.setCustomName(this.displayName);
		}
		
		saveChanges();
	}
	
	public static PlayerHorse getFromEntity(AbstractHorse horse)
	{
		for (MetadataValue meta : horse.getMetadata(OWNERSHIP_METADATA_KEY))
		{
			if (meta.getOwningPlugin() == Horses.getInstance() && meta.value().getClass() == PlayerHorse.class)
			{
				return (PlayerHorse) meta.value();
			}
		}
		
		return null;
	}
}
