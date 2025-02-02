package taxi.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.EulerAngle;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import taxi.data.DataManager;
import taxi.main.Main;

public class TaxiEventManager implements Listener {
	public static TaxiEventManager taxiEventManager;
	HashMap<String, Long> call_cooldown = new HashMap<String, Long>();
	HashMap<String, Long> time_cooldown = new HashMap<String, Long>();
	HashMap<String, Long> rightclick_cooldown = new HashMap<String, Long>();
	HashMap<String, Integer> call_task = new HashMap<String, Integer>();
	HashMap<String, Integer> boss_task = new HashMap<String, Integer>();
	HashMap<String, Integer> armorstand_task = new HashMap<String, Integer>();
	HashMap<String, String> taxi_map = new HashMap<String, String>();
	HashMap<String, ArrayList<UUID>> armorStand_map = new HashMap<String, ArrayList<UUID>>();

	ArrayList<String> taxi_list = new ArrayList<String>();
	public static ArrayList<UUID> all_armorStand_list = new ArrayList<UUID>();
	DataManager data = Main.data;
	FileConfiguration file = data.getFile();
	BukkitScheduler scheduler;
	BukkitScheduler bossbar_scheduler;
	BukkitScheduler armorstand_scheduler;
	Main plugin = Main.main;
	int task_id;
	int armorstand_task_id;
	BossBar bossBar = Bukkit.createBossBar(" ", BarColor.YELLOW, BarStyle.SOLID);

	public TaxiEventManager() {
		taxiEventManager = this;
	}

	@EventHandler
	public void onPlayerInteractEvent(PlayerInteractEvent e) {
		if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Player p = e.getPlayer();
			if (!call_task.containsKey(p.getName()) && !call_cooldown.containsKey(p.getName())
					&& !armorstand_task.containsKey(p.getName())) {
				ItemStack hand_item = p.getInventory().getItemInMainHand();
				if (!(hand_item == null || hand_item.getType().equals(Material.AIR))) {
					ItemStack ticket = file.getItemStack("ticket.item");
					if (ticket != null) {
						if (hand_item.getItemMeta().equals(ticket.getItemMeta())) {
							if (!rightclick_cooldown.containsKey(p.getName())) {
								callTaxi(p, hand_item);
								rightclick_cooldown.put(p.getName(), System.currentTimeMillis() + (1 * 500));
							} else if (rightclick_cooldown.get(p.getName()) > System.currentTimeMillis()) {
								return;
							} else if (rightclick_cooldown.get(p.getName()) <= System.currentTimeMillis()) {
								callTaxi(p, hand_item);
								rightclick_cooldown.put(p.getName(), System.currentTimeMillis() + (1 * 500));
							}
						}
					}
				}
			}
		}
	}

	@EventHandler
	public void onArmorStandClickEvent(PlayerInteractAtEntityEvent pe) {
		Player p = pe.getPlayer();
		if (armorStand_map.containsKey(p.getName())) {
			ArrayList<UUID> armorStands = armorStand_map.get(p.getName());
			UUID armor_uuid = pe.getRightClicked().getUniqueId();
			if (armorStands.contains(armor_uuid)) {
				Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "speedtaxi " + p.getName());
				for (UUID uuid : armorStands) { // 아머스탠드 제거
					for (World w : Bukkit.getWorlds()) {
						for (Entity e : w.getEntities()) {
							if (e.getUniqueId().equals(uuid))
								e.remove();
						}
					}
				}
				if (boss_task.containsKey(p.getName()) && armorstand_task.containsKey(p.getName())
						&& taxi_map.containsKey(p.getName())) {
					String key = taxi_map.get(p.getName());
					int boss_task_id = boss_task.get(p.getName());
					int armorstand_task_id = armorstand_task.get(p.getName());
					bossbar_scheduler.cancelTask(boss_task_id);
					armorstand_scheduler.cancelTask(armorstand_task_id);
					taxi_list.remove(key);
					taxi_map.remove(p.getName());
					boss_task.remove(p.getName());
					armorStand_map.remove(p.getName());
					armorstand_task.remove(p.getName());
					bossBar.removePlayer(p);
				}
			}
		}
	}

	@EventHandler
	public void onPlayerExitEvent(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		// 택시가 호출되는 중 나갔을 경우
		if (call_cooldown.containsKey(p.getName())) {
			String name = p.getName();
			int id = call_task.get(name);
			call_cooldown.remove(name);
			call_task.remove(name);
			scheduler.cancelTask(id);
			rightclick_cooldown.remove(p.getName());
			// 택시가 호출되고 나서 나갔을 경우
		} else if (armorStand_map.containsKey(p.getName())) {
			ArrayList<UUID> armorStands = armorStand_map.get(p.getName());
			for (UUID uuid : armorStands) { // 아머스탠드 제거
				for (World w : Bukkit.getWorlds()) {
					for (Entity en : w.getEntities()) {
						if (en.getUniqueId().equals(uuid))
							en.remove();
					}
				}
			}
			if (boss_task.containsKey(p.getName()) && armorstand_task.containsKey(p.getName())
					&& taxi_map.containsKey(p.getName())) {
				String key = taxi_map.get(p.getName());
				int boss_task_id = boss_task.get(p.getName());
				int armorstand_task_id = armorstand_task.get(p.getName());
				bossbar_scheduler.cancelTask(boss_task_id);
				armorstand_scheduler.cancelTask(armorstand_task_id);
				taxi_list.remove(key);
				taxi_map.remove(p.getName());
				boss_task.remove(p.getName());
				armorStand_map.remove(p.getName());
				armorstand_task.remove(p.getName());
				bossBar.removePlayer(p);
			}
		}
	}

	// 택시 호출 중 움직였을 때
	@EventHandler
	public void onPlayerMoveEvent(PlayerMoveEvent e) {
		Player p = e.getPlayer();
		if (call_cooldown.containsKey(p.getName())) {
			cancelTaxiCall(p);
			e.setCancelled(true);
			p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
					TextComponent.fromLegacyText(ChatColor.DARK_AQUA + "택시호출 중 움직이셔서 취소되었습니다."));
		}
	}

	// 택시 호출 중 인벤토리 창 움직였을 때
	@EventHandler
	public void onItemHeldEvent(PlayerItemHeldEvent e) {
		Player p = e.getPlayer();
		if (call_cooldown.containsKey(p.getName())) {
			cancelTaxiCall(p);
			e.setCancelled(true);
			p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
					TextComponent.fromLegacyText(ChatColor.DARK_AQUA + "택시호출 중 움직이셔서 취소되었습니다."));
		}
	}

	public void callTaxi(Player p, ItemStack hand_item) {
		Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "taxicall " + p.getName());
		scheduler = Bukkit.getScheduler();
		task_id = scheduler.scheduleSyncRepeatingTask(plugin, new Runnable() {
			int cnt = 0;

			@Override
			public void run() {
				if (call_cooldown.containsKey(p.getName())) { // 5초 지났을 시
					if (!(call_cooldown.get(p.getName()) > System.currentTimeMillis())) {
						p.sendTitle("", "", 0, 3, 0);
						Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
								"npctalk calltaxi " + p.getName());
						String name = p.getName();
						int id = call_task.get(name);
						call_cooldown.remove(name);
						call_task.remove(name);
						scheduler.cancelTask(id);
						// 최소 거리 택시 구하기
						String key = getMinDistance(p);
						if (key == null) {
							p.sendMessage(format("&c- 인근 위치에 다른 플레이어가 이미 택시를 호출하여 취소되었습니다. "
									+ format("&7기다리시거나, 다른 곳에서 재시도 하십시오.")));
							return;
						}
						hand_item.setAmount(hand_item.getAmount() - 1);
						p.getInventory().setItemInMainHand(hand_item);
						taxi_map.put(p.getName(), key);
						getBossBar(p);
					}
				} else {
					call_cooldown.put(p.getName(), System.currentTimeMillis() + (5 * 1000));
					call_task.put(p.getName(), task_id);
				}
				switch (cnt) {
				case 0:
					p.sendTitle(ChatColor.RED + "택시를 호출 중입니다", "", 0, 3, 0);
					break;
				case 1:
					p.sendTitle(ChatColor.RED + "택시를 호출 중입니다.", "", 0, 3, 0);
					break;
				case 2:
					p.sendTitle(ChatColor.RED + "택시를 호출 중입니다. .", "", 0, 3, 0);
					break;
				case 3:
					p.sendTitle(ChatColor.RED + "택시를 호출 중입니다. . .", "", 0, 3, 0);
					cnt = -1;
					break;
				}
				cnt++;
			}
		}, 10, 3);
	}

	public void forcecallTaxi(Player p) {
		if (!call_task.containsKey(p.getName()) && !call_cooldown.containsKey(p.getName())
				&& !armorstand_task.containsKey(p.getName())) {
			Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "taxicall " + p.getName());
			scheduler = Bukkit.getScheduler();
			task_id = scheduler.scheduleSyncRepeatingTask(plugin, new Runnable() {
				int cnt = 0;
				@Override
				public void run() {
					if (call_cooldown.containsKey(p.getName())) { // 5초 지났을 시
						if (!(call_cooldown.get(p.getName()) > System.currentTimeMillis())) {
							p.sendTitle("", "", 0, 3, 0);
							Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
									"npctalk calltaxi " + p.getName());
							String name = p.getName();
							int id = call_task.get(name);
							call_cooldown.remove(name);
							call_task.remove(name);
							scheduler.cancelTask(id);
							// 최소 거리 택시 구하기
							String key = getMinDistance(p);
							if (key == null) {
								p.sendMessage(format("&c- 인근 위치에 다른 플레이어가 이미 택시를 호출하여 취소되었습니다. "
										+ format("&7기다리시거나, 다른 곳에서 재시도 하십시오.")));
								return;
							}
							taxi_map.put(p.getName(), key);
							getBossBar(p);
						}
					} else {
						call_cooldown.put(p.getName(), System.currentTimeMillis() + (5 * 1000));
						call_task.put(p.getName(), task_id);
					}
					switch (cnt) {
					case 0:
						p.sendTitle(ChatColor.RED + "택시를 호출 중입니다", "", 0, 10, 0);
						break;
					case 1:
						p.sendTitle(ChatColor.RED + "택시를 호출 중입니다.", "", 0, 10, 0);
						break;
					case 2:
						p.sendTitle(ChatColor.RED + "택시를 호출 중입니다. .", "", 0, 10, 0);
						break;
					case 3:
						p.sendTitle(ChatColor.RED + "택시를 호출 중입니다. . .", "", 0, 10, 0);
						cnt = -1;
						break;
					}
					cnt++;
				}
			}, 10, 10);
		}
	}

	public void forceCancelTaxi(Player p, String name) {
		if (!taxi_map.containsKey(name)) {
			p.sendMessage(ChatColor.RED + "이 플레이어는 현재 택시를 호출하고 있지 않습니다.");
			return;
		} else {
			ArrayList<UUID> armorStands = armorStand_map.get(name);
			for (UUID uuid : armorStands) { // 아머스탠드 제거
				for (World w : Bukkit.getWorlds()) {
					for (Entity en : w.getEntities()) {
						if (en.getUniqueId().equals(uuid))
							en.remove();
					}
				}
			}
			if (boss_task.containsKey(name) && armorstand_task.containsKey(name) && taxi_map.containsKey(name)) {
				String key = taxi_map.get(name);
				int boss_task_id = boss_task.get(name);
				int armorstand_task_id = armorstand_task.get(name);
				bossbar_scheduler.cancelTask(boss_task_id);
				armorstand_scheduler.cancelTask(armorstand_task_id);
				taxi_list.remove(key);
				taxi_map.remove(name);
				boss_task.remove(name);
				armorStand_map.remove(name);
				armorstand_task.remove(name);
				Player target = Bukkit.getPlayer(name);
				bossBar.removePlayer(target);
				ItemStack ticket = file.getItemStack("ticket.item");
				target.getInventory().addItem(ticket);
			}
			p.sendMessage(ChatColor.GREEN + "성공적으로 택시호출이 중단되었습니다.");
		}
	}

	private void getBossBar(Player p) {
		if (taxi_map.containsKey(p.getName())) {
			String key = taxi_map.get(p.getName());
			Location taxi_loc = (Location) file.get("main_taxiLocation." + key + ".loc");
			getTakeTime(p);
			bossbar_scheduler = Bukkit.getScheduler();
			bossBar.addPlayer(p);
			makeMainArmorStand(p, key);
			task_id = bossbar_scheduler.scheduleSyncRepeatingTask(plugin, new Runnable() {
				@Override
				public void run() {
					if (boss_task.containsKey(p.getName())) {
						Location p_loc = p.getLocation();
						int distance = (int) taxi_loc.distanceSquared(p_loc);
						long timeleft = (time_cooldown.get(p.getName()) - System.currentTimeMillis()) / 1000;
						bossBar.setVisible(true);
						bossBar.setTitle(format("&6[ ") + format("&c&lTAXI ") + format("&6] &f택시가 대기중입니다! ")
								+ format("&7<") + format("&6거리: ") + format("&e" + distance + "블럭 ") + format("&8/ ")
								+ format("&6남은시간: ") + format("&c" + timeleft + "초") + format("&7>"));
						if (timeleft <= 0) {
							int task_id = boss_task.get(p.getName());
							bossBar.removePlayer(p);
							bossbar_scheduler.cancelTask(task_id);
							taxi_list.remove(key);
							taxi_map.remove(p.getName());
							boss_task.remove(p.getName());
							armorstand_task.remove(p.getName());
						}
					} else {
						boss_task.put(p.getName(), task_id);
					}
				}
			}, 0, 3);
		}
	}

	private void makeMainArmorStand(Player p, String key) {
		ArrayList<UUID> armorStand_list = new ArrayList<UUID>();
		// 메인 택시 아머스탠드 생성
		Location main_loc = (Location) file.get("main_taxiLocation." + key + ".loc");
		ItemStack headItem = file.getItemStack("main_taxi.item");
		ArmorStand armorStand = (ArmorStand) p.getWorld().spawnEntity(main_loc, EntityType.ARMOR_STAND);
		UUID uuid = armorStand.getUniqueId();
		armorStand_list.add(uuid);
		// Location look = p.getLocation().subtract(armorStand.getLocation());
		// EulerAngle poseAngle = directionToEuler(look);
		// armorStand.setHeadPose(poseAngle);
		armorStand.setVisible(true);
		armorStand.setBasePlate(false);
		armorStand.setVisible(false);
		armorStand.setInvulnerable(true);
		armorStand.setHelmet(headItem);
		armorStand.setGravity(false);
		all_armorStand_list.add(uuid);
		// 홀로그램 1 생성
		Location h_loc = main_loc.clone();
		ArmorStand hologram1 = (ArmorStand) p.getWorld().spawnEntity(h_loc.add(0, 0.0, 0), EntityType.ARMOR_STAND);
		UUID hologram1_uuid = hologram1.getUniqueId();
		armorStand_list.add(hologram1_uuid);
		String name = format("&7택시 호출: ") + format("&c <" + p.getName() + ">");
		hologram1.setVisible(false);
		hologram1.setCustomNameVisible(true);
		hologram1.setInvulnerable(true);
		hologram1.setCustomName(name);
		hologram1.setGravity(false);
		all_armorStand_list.add(hologram1_uuid);
		// 홀로그램 2 생성
		ArmorStand hologram2 = (ArmorStand) p.getWorld().spawnEntity(h_loc.add(0, 0.25, 0), EntityType.ARMOR_STAND);
		UUID hologram2_uuid = hologram2.getUniqueId();
		armorStand_list.add(hologram2_uuid);
		name = format("&7택시 호출: ") + format("&c <" + p.getName() + ">");
		hologram2.setVisible(false);
		hologram2.setCustomNameVisible(true);
		hologram2.setInvulnerable(true);
		hologram2.setCustomName(name);
		hologram2.setGravity(false);
		all_armorStand_list.add(hologram2_uuid);
		// 홀로그램 3 생성
		ArmorStand hologram3 = (ArmorStand) p.getWorld().spawnEntity(h_loc.add(0, 0.3, 0), EntityType.ARMOR_STAND);
		UUID hologram3_uuid = hologram3.getUniqueId();
		armorStand_list.add(hologram3_uuid);
		name = ChatColor.GOLD + " [ " + ChatColor.RED + "" + ChatColor.BOLD + "TAXI " + ChatColor.DARK_GRAY + "/ "
				+ ChatColor.WHITE + "대기중" + ChatColor.GOLD + " ] ";
		hologram3.setVisible(false);
		hologram3.setCustomNameVisible(true);
		hologram3.setInvulnerable(true);
		hologram3.setCustomName(name);
		hologram3.setGravity(false);
		all_armorStand_list.add(hologram3_uuid);
		// 데코 택시 아머스탠드 생성
		Location deco_loc = (Location) file.get("deco_taxiLocation." + key + ".loc");
		ItemStack deco_headItem = file.getItemStack("deco_taxi.item");
		ArmorStand deco_armorStand = (ArmorStand) p.getWorld().spawnEntity(deco_loc, EntityType.ARMOR_STAND);
		UUID deco_uuid = deco_armorStand.getUniqueId();
		// Location deco_look = p.getLocation().subtract(deco_armorStand.getLocation());
		// EulerAngle deco_poseAngle = directionToEuler(deco_look);
		// deco_armorStand.setHeadPose(deco_poseAngle);
		deco_armorStand.setVisible(true);
		deco_armorStand.setBasePlate(false);
		deco_armorStand.setVisible(false);
		deco_armorStand.setGravity(false);
		deco_armorStand.setInvulnerable(true);
		deco_armorStand.setHelmet(deco_headItem);
		armorStand_list.add(deco_uuid);
		all_armorStand_list.add(deco_uuid);
		armorStand_map.put(p.getName(), armorStand_list);
		armorstand_scheduler = Bukkit.getScheduler();
		armorstand_task_id = armorstand_scheduler.scheduleSyncRepeatingTask(plugin, new Runnable() {
			@Override
			public void run() {
				if (!armorstand_task.containsKey(p.getName())) {
					armorstand_task.put(p.getName(), armorstand_task_id);
				} else {
					long timeleft = (time_cooldown.get(p.getName()) - System.currentTimeMillis()) / 1000;
					if (timeleft <= 0) {
						int task = armorstand_task.get(p.getName());
						armorstand_scheduler.cancelTask(task);
						taxi_list.remove(key);
						ArrayList<UUID> uuid_array = armorStand_map.get(p.getName());
						for (UUID uuid : uuid_array) {
							for (World w : Bukkit.getWorlds()) {
								for (Entity e : w.getEntities()) {
									if (e.getUniqueId().equals(uuid))
										e.remove();
								}
							}
						}
						armorStand_map.remove(p.getName());
					}
					String name = format("&6남은 대기시간: ") + format("&c&l<" + timeleft + ">" + format("&c초"));
					hologram1.setCustomName(name);
				}
			}
		}, 0, 3);
	}

	private void cancelTaxiCall(Player p) {
		p.sendTitle("", "", 0, 3, 0);
		String name = p.getName();
		int id = call_task.get(name);
		call_cooldown.remove(name);
		call_task.remove(name);
		scheduler.cancelTask(id);
	}

	private void getTakeTime(Player p) {
		int time = 0;
		if (taxi_map.containsKey(p.getName())) {
			String key = taxi_map.get(p.getName());
			Location taxi_loc = (Location) file.get("main_taxiLocation." + key + ".loc");
			Location p_loc = p.getLocation();
			double distance = taxi_loc.distanceSquared(p_loc);
			// 시간 구하기
			if (distance >= 0 && distance < 20) {
				time_cooldown.put(p.getName(), System.currentTimeMillis() + (30 * 1000));
				time = 30;
			} else if (distance >= 20 && distance < 40) {
				time_cooldown.put(p.getName(), System.currentTimeMillis() + (40 * 1000));
				time = 40;
			} else if (distance >= 40 && distance < 60) {
				time_cooldown.put(p.getName(), System.currentTimeMillis() + (50 * 1000));
				time = 50;
			} else if (distance >= 60 && distance < 100) {
				time_cooldown.put(p.getName(), System.currentTimeMillis() + (80 * 1000));
				time = 80;
			} else {
				time_cooldown.put(p.getName(), System.currentTimeMillis() + (120 * 1000));
				time = 120;
			}
		}
	}

	private EulerAngle directionToEuler(Location dir) {
		double xzLength = Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ());
		double pitch = Math.atan2(xzLength, dir.getY()) - Math.PI / 2;
		double yaw = -Math.atan2(dir.getX(), dir.getZ()) + Math.PI / 4;
		return new EulerAngle(pitch, yaw, 0);
	}

	public String getMinDistance(Player p) {
		Set<String> keys = file.getConfigurationSection("main_taxiLocation").getKeys(false);
		Iterator<String> iter = keys.iterator();
		Location p_loc = p.getLocation();
		double min = 999999999;
		String min_key = null;
		while (iter.hasNext()) {
			String key = iter.next();
			if (file.get("main_taxiLocation." + key + ".loc") != null) {
				Location taxi_loc = (Location) file.get("main_taxiLocation." + key + ".loc");
				double distance = taxi_loc.distanceSquared(p_loc);
				if ((distance < min) && !taxi_list.contains(key)) {
					min = distance;
					min_key = key;
				}
			}
		}
		taxi_list.add(min_key);
		return min_key;
	}

	private String format(String msg) {
		return ChatColor.translateAlternateColorCodes('&', msg);
	}
}
