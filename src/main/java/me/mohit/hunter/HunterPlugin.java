package me.mohit.hunter;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public final class HunterPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey hunterKey, cellKey, missileKey, shotKey;
    private final Map<UUID, UUID> locks = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Shot> shots = new HashMap<>();
    private final Map<UUID, UUID> missileToShot = new HashMap<>();

    @Override public void onEnable() {
        hunterKey = new NamespacedKey(this,"hunter"); cellKey = new NamespacedKey(this,"cell");
        missileKey = new NamespacedKey(this,"missile"); shotKey = new NamespacedKey(this,"shot");
        Bukkit.getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("hunter")).setExecutor(this);
    }
    @Override public void onDisable(){ bars.forEach((id,b)->{ Player p=Bukkit.getPlayer(id); if(p!=null)p.hideBossBar(b);}); }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a){
        if(!(s instanceof Player p)) return true;
        p.getInventory().addItem(hunter()); p.sendMessage(Component.text("Hunter received.", NamedTextColor.RED)); return true;
    }

    private ItemStack hunter(){
        ItemStack i=new ItemStack(Material.CROSSBOW); ItemMeta m=i.getItemMeta();
        m.displayName(Component.text("⚡ HUNTER | READY ⚡",NamedTextColor.RED).decoration(TextDecoration.ITALIC,false).decorate(TextDecoration.BOLD));
        m.lore(List.of(
            Component.text("Projectile: [Arrow]",NamedTextColor.WHITE), Component.text("━━━━━━━━━━━━━━━━━━━━",NamedTextColor.DARK_GRAY),
            Component.text("▌ Advanced Homing Missile System",NamedTextColor.WHITE),
            Component.text("▌ High-Speed Predictive Tracking",NamedTextColor.LIGHT_PURPLE), Component.text("  Built for Elytra Interception",NamedTextColor.GRAY),
            Component.text("▌ Target Range: 50 Blocks",NamedTextColor.RED), Component.text("  Players & Entities",NamedTextColor.WHITE),
            Component.text("▌ Direct Line-of-Sight Required",NamedTextColor.RED), Component.text("  No Lock Through Blocks",NamedTextColor.GRAY),
            Component.text("▌ Left-Click to Lock Target",NamedTextColor.YELLOW), Component.text("  Right-Click to Fire",NamedTextColor.GREEN),
            Component.text("▌ Load With Any Compatible Arrow",NamedTextColor.AQUA), Component.text("▌ One Hunter • One Missile",NamedTextColor.LIGHT_PURPLE)
        ));
        m.getPersistentDataContainer().set(hunterKey,PersistentDataType.BYTE,(byte)1); i.setItemMeta(m); return i;
    }
    private boolean hunter(ItemStack i){ return i!=null && i.getType()==Material.CROSSBOW && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(hunterKey); }

    @EventHandler public void swing(PlayerArmSwingEvent e){
        Player p=e.getPlayer(); if(!hunter(p.getInventory().getItemInMainHand())) return;
        Bukkit.getScheduler().runTask(this,()->toggleLock(p));
    }
    private void toggleLock(Player p){
        if(locks.containsKey(p.getUniqueId())) { clearLock(p); p.sendActionBar(Component.text("HUNTER TARGET UNLOCKED",NamedTextColor.RED)); return; }
        RayTraceResult r=p.getWorld().rayTraceEntities(p.getEyeLocation(),p.getEyeLocation().getDirection(),50,0.45,en-> en instanceof LivingEntity && en!=p);
        if(r==null || !(r.getHitEntity() instanceof LivingEntity target)) { p.sendActionBar(Component.text("NO VISIBLE TARGET",NamedTextColor.RED)); return; }
        RayTraceResult block=p.getWorld().rayTraceBlocks(p.getEyeLocation(),p.getEyeLocation().getDirection(),p.getEyeLocation().distance(target.getEyeLocation()),FluidCollisionMode.NEVER,true);
        if(block!=null){ p.sendActionBar(Component.text("TARGET BLOCKED",NamedTextColor.RED)); return; }
        locks.put(p.getUniqueId(),target.getUniqueId()); target.setGlowing(true);
        p.getWorld().spawnParticle(Particle.SONIC_BOOM,target.getEyeLocation(),1);
        for(int k=0;k<3;k++){ int d=k*3; Bukkit.getScheduler().runTaskLater(this,()->p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,1f,1.7f),d); }
        BossBar bar=BossBar.bossBar(Component.text("HUNTER | READY TO FIRE! | TARGET: "+target.getName(),NamedTextColor.RED),1f,BossBar.Color.RED,BossBar.Overlay.PROGRESS);
        bars.put(p.getUniqueId(),bar); p.showBossBar(bar);
    }
    private void clearLock(Player p){
        UUID id=locks.remove(p.getUniqueId()); if(id!=null){ Entity e=Bukkit.getEntity(id); if(e instanceof LivingEntity l) l.setGlowing(false); }
        BossBar b=bars.remove(p.getUniqueId()); if(b!=null)p.hideBossBar(b);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fire(PlayerInteractEvent e){
        if(!e.getAction().isRightClick()) return; Player p=e.getPlayer(); ItemStack hand=e.getItem(); if(!hunter(hand))return;
        UUID tid=locks.get(p.getUniqueId()); if(tid==null)return;
        Entity en=Bukkit.getEntity(tid); if(!(en instanceof LivingEntity target)||target.isDead()){ clearLock(p); return; }
        if(!(hand.getItemMeta() instanceof org.bukkit.inventory.meta.CrossbowMeta cm) || !cm.hasChargedProjectiles()) return;
        e.setCancelled(true);
        ItemStack ammo=cm.getChargedProjectiles().getFirst();
        UUID sid=UUID.randomUUID(); Shot shot=new Shot(sid,p.getUniqueId(),target.getUniqueId(),target.getName(),ammo.getType().name(),p.getInventory().getHeldItemSlot()); shots.put(sid,shot);
        Arrow arrow=p.launchProjectile(Arrow.class); arrow.getPersistentDataContainer().set(missileKey,PersistentDataType.BYTE,(byte)1); arrow.getPersistentDataContainer().set(shotKey,PersistentDataType.STRING,sid.toString());
        arrow.setDamage(0); arrow.setGravity(false); arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED); arrow.setVelocity(p.getEyeLocation().getDirection().normalize().multiply(3.4)); missileToShot.put(arrow.getUniqueId(),sid);
        p.getInventory().setItemInMainHand(cell(shot,"IN FLIGHT",null)); clearLock(p);
        for(int k=0;k<3;k++){ int d=k*2; Bukkit.getScheduler().runTaskLater(this,()->p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,1f,1.9f),d); }
        track(arrow,shot);
    }
    private void track(Arrow a, Shot s){
        new BukkitRunnable(){ int ticks=0; public void run(){
            if(!a.isValid()){ if(!s.finished)finish(s,false,"MISSILE LOST"); cancel(); return; }
            Entity te=Bukkit.getEntity(s.target); if(!(te instanceof LivingEntity t)||t.isDead()){ a.remove(); finish(s,false,"TARGET LOST"); cancel(); return; }
            if(++ticks>300){ a.remove(); finish(s,false,"MISSILE TIMEOUT"); cancel(); return; }
            Vector tv=t.getVelocity(); double distance=a.getLocation().distance(t.getEyeLocation()); double speed=Math.min(5.2,3.5+distance*0.035);
            Location predicted=t.getEyeLocation().clone().add(tv.clone().multiply(Math.min(10,distance/speed)));
            Vector desired=predicted.toVector().subtract(a.getLocation().toVector()).normalize().multiply(speed);
            Vector current=a.getVelocity(); Vector steered=current.multiply(.28).add(desired.multiply(.72)); if(steered.length()>speed)steered.normalize().multiply(speed); a.setVelocity(steered); a.getWorld().spawnParticle(Particle.END_ROD,a.getLocation(),2,0.04,0.04,0.04,0);
        }}.runTaskTimer(this,1,1);
    }

    @EventHandler public void hit(ProjectileHitEvent e){
        if(!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(missileKey))return;
        UUID sid=missileToShot.remove(a.getUniqueId()); if(sid==null)return; Shot s=shots.get(sid); if(s==null||s.finished)return;
        Entity hit=e.getHitEntity();
        if(hit!=null && hit.getUniqueId().equals(s.target)){ Location loc=hit.getLocation(); impact(loc); finish(s,true,null); }
        else if(e.getHitBlock()!=null) finish(s,false,"COLLIDED WITH BLOCK");
        else if(hit!=null) finish(s,false,"WRONG ENTITY HIT"); else finish(s,false,"MISSILE NOT IMPACTED");
        a.remove();
    }
    private void impact(Location l){
        World w=l.getWorld(); w.spawnParticle(Particle.SONIC_BOOM,l.clone().add(0,1,0),3); w.spawnParticle(Particle.EXPLOSION,l,5,1,1,1,0.05);
        for(Player online:Bukkit.getOnlinePlayers()) online.playSound(online.getLocation(),Sound.BLOCK_END_PORTAL_SPAWN,1f,1f);
        for(Entity e:w.getNearbyEntities(l,5,5,5)){ if(!(e instanceof LivingEntity le))continue; if(le.getLocation().distanceSquared(l)>25)continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,100,0)); le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE,1,1)); le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,0)); le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,200,0));
            forceTotemOrKill(le);
        }
    }
    private void forceTotemOrKill(LivingEntity le){
        EntityEquipment equipment = le.getEquipment();
        ItemStack main = equipment == null ? null : equipment.getItemInMainHand();
        ItemStack off = equipment == null ? null : equipment.getItemInOffHand();
        boolean hasMain = main != null && main.getType() == Material.TOTEM_OF_UNDYING;
        boolean hasOff = off != null && off.getType() == Material.TOTEM_OF_UNDYING;

        if(hasMain || hasOff){
            if(hasMain) main.setAmount(main.getAmount() - 1);
            else off.setAmount(off.getAmount() - 1);
            le.setHealth(Math.min(1.0, le.getMaxHealth()));
            le.setFireTicks(0);
        } else {
            le.setHealth(0);
        }
    }
    private void finish(Shot s,boolean ok,String reason){
        if(s.finished)return; s.finished=true; s.success=ok; s.reason=reason; Player p=Bukkit.getPlayer(s.shooter);
        if(p!=null){ updateCell(p,s); p.sendMessage(Component.text(ok?"Hunter Missile impacted "+s.targetName+"!":"Missile not impacted!",ok?NamedTextColor.GREEN:NamedTextColor.RED)); }
    }
    private ItemStack cell(Shot s,String status,String reason){
        ItemStack i=new ItemStack(Material.PRISMARINE_CRYSTALS); ItemMeta m=i.getItemMeta(); m.getPersistentDataContainer().set(cellKey,PersistentDataType.STRING,s.id.toString());
        m.displayName(Component.text("🌈 H U N T E R   C E L L 🌈",NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC,false));
        List<Component> lore=new ArrayList<>(); lore.add(Component.text("🚀 MISSILE: FIRED",NamedTextColor.AQUA)); lore.add(Component.text("🎯 TARGET: "+s.targetName,NamedTextColor.YELLOW));
        Player shooter=Bukkit.getPlayer(s.shooter); lore.add(Component.text("👤 FIRED BY: "+(shooter==null?s.shooter.toString():shooter.getName()),NamedTextColor.LIGHT_PURPLE)); lore.add(Component.empty());
        NamedTextColor c=status.contains("SUCCESS")?NamedTextColor.GREEN:status.contains("UNSUCCESS")?NamedTextColor.RED:NamedTextColor.GOLD; lore.add(Component.text("STATUS: "+status,c).decorate(TextDecoration.BOLD));
        if(reason!=null) lore.add(Component.text("⚠ REASON: "+reason,NamedTextColor.RED)); lore.add(Component.empty()); lore.add(Component.text("HUNTER USED!!",NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)); m.lore(lore); i.setItemMeta(m); return i;
    }
    private void updateCell(Player p,Shot s){
        String st=s.success?"IMPACTED SUCCESSFULLY":"NOT IMPACTED UNSUCCESSFULLY"; for(int x=0;x<p.getInventory().getSize();x++){ ItemStack i=p.getInventory().getItem(x); if(i==null||!i.hasItemMeta())continue; String id=i.getItemMeta().getPersistentDataContainer().get(cellKey,PersistentDataType.STRING); if(s.id.toString().equals(id)){ p.getInventory().setItem(x,cell(s,st,s.reason)); return; }}
    }
    private static final class Shot { final UUID id,shooter,target; final String targetName,ammo; final int slot; boolean finished,success; String reason; Shot(UUID i,UUID s,UUID t,String n,String a,int sl){id=i;shooter=s;target=t;targetName=n;ammo=a;slot=sl;} }
          }
          
