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

package com.forgenz.horses.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import com.forgenz.forgecore.v1_0.bukkit.ForgeListener;
import com.forgenz.horses.Horses;
import com.forgenz.horses.Messages;
import com.forgenz.horses.PlayerHorse;
import com.forgenz.horses.config.HorsesConfig;
import com.forgenz.horses.config.HorsesPermissionConfig;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;

/**
 * Protects owned horses from being killed by players
 * Including their owners.
 */
public class DamageListener extends ForgeListener
{
	public DamageListener(Horses plugin)
	{
		super(plugin);
		
		register();
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent event)
	{
		// Ignore any entities which are not horses
		if (event.getEntity() == null || !(event.getEntity() instanceof AbstractHorse))
		{
			return;
		}

		// Fetch our lovely horse :)
		AbstractHorse horse = (AbstractHorse) event.getEntity();

		// Check if the Horse is owned
		PlayerHorse horseData = PlayerHorse.getFromEntity(horse); 

		// If the horse has player data then they are owned and managed by Horses
		if (horseData == null)
		{
			// Else let the horse die
			return;
		}
		
		// Fetch the config
		Player player = horseData.getStable().getPlayerOwner();
		
		HorsesConfig cfg = getPlugin().getHorsesConfig();
		HorsesPermissionConfig pcfg = cfg.getPermConfig(player);
		
		// Invincible!!
		if (pcfg.invincibleHorses)
		{
			event.setCancelled(true);
			return;
		}
		
		if (pcfg.protectedDamageCauses.contains(event.getCause()))
		{
			event.setCancelled(true);
			return;
		}
		
		if (event.getClass() == EntityDamageByEntityEvent.class)
		{
			onEntityDamageByEntity((EntityDamageByEntityEvent) event, horse, horseData, pcfg);
		}
		
		if (!event.isCancelled() && (pcfg.onlyHurtHorseIfOwnerCanBeHurt || pcfg.transferDamageToRider))
		{
			if (player == null)
			{
				return;
			}
			
			EntityDamageEvent e = null;
			double damage = pcfg.transferDamageToRider ? event.getDamage() / horse.getMaxHealth() * player.getMaxHealth() : 0.0;
			
			Player exemptedPlayer = null;
			
			// Create a copy of the Damage event (But with 0 damage)
			if (event.getClass() == EntityDamageEvent.class)
				e = new EntityDamageEvent(player, event.getCause(), damage);
			else if (event.getClass() == EntityDamageByEntityEvent.class)
			{
				Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
				e = new EntityDamageByEntityEvent(damager, player, event.getCause(), damage);
				
				// Make sure NoCheatPlus does not cancel this event
				if (damager.getType() == EntityType.PLAYER && getPlugin().isNoCheatPlusEnabled())
				{
					exemptedPlayer = (Player) damager;
					if (!NCPExemptionManager.isExempted(player, CheckType.ALL))
					{
						NCPExemptionManager.exemptPermanently(exemptedPlayer, CheckType.ALL);
					}
					// If the player is already exempt we don't want to unExempt them.
					else
					{
						exemptedPlayer = null;
					}
				}
			}
			else if (event.getClass() == EntityDamageByBlockEvent.class)
				e = new EntityDamageByBlockEvent(((EntityDamageByBlockEvent) event).getDamager(), player, event.getCause(), damage);
			else
				return;
			
			Bukkit.getPluginManager().callEvent(e);
			
			// If we exmepted a player from ncp checks we much unexempt them
			if (getPlugin().isNoCheatPlusEnabled() && exemptedPlayer != null)
			{
				NCPExemptionManager.unexempt(player, CheckType.ALL);
			}
			
			if (!e.isCancelled() && pcfg.transferDamageToRider && horse.getPassenger() == player)
			{
				event.setCancelled(true);
				player.damage(e.getDamage());
			}
			else
			{
				event.setCancelled(e.isCancelled());
			}
		}
	}
	
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event, AbstractHorse horse, PlayerHorse horseData, HorsesPermissionConfig cfg)
	{
		// Find a player which tried to hurt the horse
		Player player = getPlayerDamager(event.getDamager());
				
		// Don't let any players hurt the poor horsey
		if (player != null)
		{
			// If we are protecting horses from their owners, check if the damager is their owner
			if (cfg.protectFromOwner && horseData.getStable().getOwner().equals(player.getName()))
			{
				event.setCancelled(true);
			}
			// If this is set we don't let any players hurt the horse
			else if (cfg.protectFromPlayers)
			{
				Messages.Event_Damage_Error_CantHurtOthersHorses.sendMessage(player);
				event.setCancelled(true);
			}
		}
		else
		{
			// If set, don't let mobs hurt the horse
			if (cfg.protectFromMobs)
			{
				event.setCancelled(true);
			}
		}
	}
	
	public static Player getPlayerDamager(Entity entity)
	{
		if (entity == null)
			return null;
		
		if (entity.getType() == EntityType.PLAYER)
			return (Player) entity;
		
		if (entity.getType() == EntityType.PRIMED_TNT)
			return castPlayer(((TNTPrimed) entity).getSource());
		
		if (entity instanceof Projectile)
		{
			ProjectileSource source = ((Projectile) entity).getShooter();
			if (source instanceof Player)
				castPlayer((Entity) source);
		}
		
		return null;
	}
	
	public static Player castPlayer(Entity entity)
	{
		if (entity == null)
			return null;
		
		if (entity.getType() == EntityType.PLAYER)
			return (Player) entity;
		
		return null;
	}
	
	@Override
	public Horses getPlugin()
	{
		return (Horses) super.getPlugin();
	}
}