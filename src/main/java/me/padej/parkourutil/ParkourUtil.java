package me.padej.parkourutil;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class ParkourUtil extends JavaPlugin implements Listener {
    private final Map<Player, Long> cooldowns = new HashMap<>();
    private final Map<Block, BukkitRunnable> blockTimers = new HashMap<>();
    private final Map<Block, BukkitRunnable> pistonActivationCooldowns = new HashMap<>();
    private final Map<Block, BukkitRunnable> trapdoorTimers = new HashMap<>();
    private boolean isEnabled = true;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("parkourutils").setExecutor(this);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled) {
            return;
        }

        Player player = event.getPlayer();

        // Проверяем, что игрок не находится в креативе или режиме спектатора
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Location location = player.getLocation();
        Block block = location.getBlock().getRelative(BlockFace.DOWN);

        // Проверяем, что игрок стоит на WHITE_STAINED_GLASS или WHITE_STAINED_GLASS_PANE
        if (block.getType() == Material.WHITE_STAINED_GLASS || block.getType() == Material.WHITE_STAINED_GLASS_PANE) {
            handleWhiteStainedGlassStanding(player, block);
        }
    }

    private void handleWhiteStainedGlassStanding(Player player, Block glassBlock) {
        BukkitRunnable timer = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                switch (ticks) {
                    case 5:
                        glassBlock.setType(Material.LIME_STAINED_GLASS);
                        break;
                    case 10:
                        glassBlock.setType(Material.YELLOW_STAINED_GLASS);
                        break;
                    case 15:
                        glassBlock.setType(Material.ORANGE_STAINED_GLASS);
                        break;
                    case 20:
                        glassBlock.setType(Material.RED_STAINED_GLASS);
                        break;
                    case 25:
                    case 30:
                    case 35:
                        glassBlock.setType(Material.AIR);
                        break;
                    case 40:
                        glassBlock.setType(Material.WHITE_STAINED_GLASS);
                        this.cancel();
                        break;
                }

                ticks++;
            }
        };

        timer.runTaskTimer(this, 1L, 0L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled) {
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();

        // Проверяем, что игрок не находится в креативе
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) {
            if (event.getClickedBlock() != null) {
                Material clickedBlockType = event.getClickedBlock().getType();

                if (clickedBlockType == Material.TARGET) {
                    handleTargetBlockClick(event.getClickedBlock(), player);
                } else if (clickedBlockType == Material.PISTON || clickedBlockType == Material.STICKY_PISTON) {
                    handlePistonBlockClick(event.getClickedBlock(), player);
                } else if (isTrapdoor(clickedBlockType) && action == Action.RIGHT_CLICK_BLOCK) {
                    handleTrapdoorClick(event.getClickedBlock(), player);
                } else if (clickedBlockType == Material.LODESTONE) {
                    handleLodestoneBlockClick(event.getClickedBlock(), player);
                }
            }
        }
    }

    private void handleTargetBlockClick(Block targetBlock, Player player) {
        if (hasCooldown(player)) {
            return;
        }

        player.setVelocity(player.getVelocity().setY(0.5));

        Vector direction = player.getLocation().getDirection().clone().setY(0).normalize();
        player.setVelocity(player.getVelocity().add(direction.multiply(0.3)));

        Random random = new Random();

        // Генерируем случайное число от 1 до 10 (включительно)
        float pitch = 0.1f + random.nextInt(10) / 10.0f;

        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, pitch);

        targetBlock.getWorld().spawnParticle(Particle.CRIT, targetBlock.getLocation().add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, 0.2);

        setCooldown(player);

        targetBlock.setType(Material.RED_STAINED_GLASS);

        BukkitRunnable timer = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 0 && ticks < 10) {
                    targetBlock.setType(Material.RED_STAINED_GLASS);
                } else if (ticks >= 10 && ticks < 20) {
                    targetBlock.setType(Material.ORANGE_STAINED_GLASS);
                } else if (ticks >= 20 && ticks < 30) {
                    targetBlock.setType(Material.YELLOW_STAINED_GLASS);
                } else if (ticks >= 30 && ticks < 40) {
                    targetBlock.setType(Material.LIME_STAINED_GLASS);
                }

                ticks++;

                if (ticks > 40) {
                    targetBlock.setType(Material.TARGET);
                    blockTimers.remove(targetBlock);
                    this.cancel();
                }
            }
        };

        timer.runTaskTimer(this, 0L, 1L);
        blockTimers.put(targetBlock, timer);
    }

    private void handleLodestoneBlockClick(Block targetBlock, Player player) {
        if (hasCooldown(player)) {
            return;
        }

        player.setVelocity(player.getVelocity().setY(0.5));

        Vector direction = player.getLocation().getDirection().clone().setY(0).normalize();
        player.setVelocity(player.getVelocity().add(direction.multiply(-0.3)));

        Random random = new Random();

        // Генерируем случайное число от 1 до 10 (включительно)
        float pitch = 0.1f + random.nextInt(10) / 10.0f;

        player.playSound(player.getLocation(), Sound.ENTITY_SHULKER_BULLET_HURT, 1.0f, pitch);

        targetBlock.getWorld().spawnParticle(Particle.CRIT_MAGIC, targetBlock.getLocation().add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, 0.2);

        setCooldown(player);

        targetBlock.setType(Material.RED_STAINED_GLASS);

        BukkitRunnable timer = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 0 && ticks < 10) {
                    targetBlock.setType(Material.RED_STAINED_GLASS);
                } else if (ticks >= 10 && ticks < 20) {
                    targetBlock.setType(Material.ORANGE_STAINED_GLASS);
                } else if (ticks >= 20 && ticks < 30) {
                    targetBlock.setType(Material.YELLOW_STAINED_GLASS);
                } else if (ticks >= 30 && ticks < 40) {
                    targetBlock.setType(Material.LIME_STAINED_GLASS);
                }

                ticks++;

                if (ticks > 40) {
                    targetBlock.setType(Material.LODESTONE);
                    blockTimers.remove(targetBlock);
                    this.cancel();
                }
            }
        };

        timer.runTaskTimer(this, 0L, 1L);
        blockTimers.put(targetBlock, timer);
    }

    private void handlePistonBlockClick(Block pistonBlock, Player player) {
        if (pistonActivationCooldowns.containsKey(pistonBlock)) {
            return;
        }

        BlockFace facing = getFacingDirection(pistonBlock);
        Block buttonBlock = pistonBlock.getRelative(facing.getOppositeFace());

        buttonBlock.setType(Material.STONE_BUTTON);
        setButtonFacing(buttonBlock, facing);

        pistonBlock.getWorld().spawnParticle(Particle.REDSTONE, pistonBlock.getLocation().add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, new Particle.DustOptions(org.bukkit.Color.RED, 1));

        BukkitRunnable cooldownTimer = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20) {
                    pistonActivationCooldowns.remove(pistonBlock);
                    this.cancel();
                }

                ticks++;
            }
        };

        cooldownTimer.runTaskTimer(this, 0L, 1L);
        pistonActivationCooldowns.put(pistonBlock, cooldownTimer);

        BukkitRunnable buttonTimer = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20) {
                    buttonBlock.setType(Material.AIR);
                    this.cancel();
                }

                ticks++;
            }
        };

        buttonTimer.runTaskTimer(this, 0L, 1L);
    }

    private void handleTrapdoorClick(Block trapdoorBlock, Player player) {
        if (trapdoorTimers.containsKey(trapdoorBlock)) {
            return;
        }

        closeTrapdoor(trapdoorBlock);

        BukkitRunnable timer = new BukkitRunnable() {
            @Override
            public void run() {
                openTrapdoor(trapdoorBlock);
                trapdoorTimers.remove(trapdoorBlock);
            }
        };

        // Запускаем таймер на закрытие люка через 10 тиков
        timer.runTaskLater(this, 10L);
        trapdoorTimers.put(trapdoorBlock, timer);
    }

    private void openTrapdoor(Block trapdoorBlock) {
        BlockData blockData = trapdoorBlock.getBlockData();
        if (blockData instanceof TrapDoor) {
            TrapDoor trapdoor = (TrapDoor) blockData;
            trapdoor.setOpen(true);
            trapdoorBlock.setBlockData(trapdoor);
        }
    }

    private void closeTrapdoor(Block trapdoorBlock) {
        BlockData blockData = trapdoorBlock.getBlockData();
        if (blockData instanceof TrapDoor) {
            TrapDoor trapdoor = (TrapDoor) blockData;
            trapdoor.setOpen(false);
            trapdoorBlock.setBlockData(trapdoor);
        }
    }

    private void setButtonFacing(Block buttonBlock, BlockFace pistonFacing) {
        BlockFace buttonFacing = getButtonFacing(pistonFacing);

        buttonBlock.setBlockData(Bukkit.createBlockData(Material.STONE_BUTTON, "[facing=" + buttonFacing.name().toLowerCase() + ",powered=true]"));
    }

    private BlockFace getButtonFacing(BlockFace pistonFacing) {
        switch (pistonFacing) {
            case WEST:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.WEST;
            case NORTH:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.NORTH;
            default:
                return BlockFace.EAST;
        }
    }

    private BlockFace getFacingDirection(Block pistonBlock) {
        BlockFace pistonFacing = BlockFace.NORTH;

        if (pistonBlock.getBlockData() instanceof org.bukkit.block.data.type.Piston) {
            org.bukkit.block.data.type.Piston pistonData = (org.bukkit.block.data.type.Piston) pistonBlock.getBlockData();
            pistonFacing = pistonData.getFacing();
        }

        return pistonFacing;
    }

    private boolean hasCooldown(Player player) {
        if (cooldowns.containsKey(player)) {
            long currentTime = System.currentTimeMillis();
            long lastUseTime = cooldowns.get(player);
            long cooldownTime = 0;

            return currentTime - lastUseTime < cooldownTime;
        }
        return false;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player, System.currentTimeMillis());
        new BukkitRunnable() {
            @Override
            public void run() {
                cooldowns.remove(player);
            }
        }.runTaskLater(this, 40L);
    }

    private boolean isTrapdoor(Material material) {
        return material == Material.OAK_TRAPDOOR || material == Material.ACACIA_TRAPDOOR ||
                material == Material.BIRCH_TRAPDOOR || material == Material.DARK_OAK_TRAPDOOR ||
                material == Material.JUNGLE_TRAPDOOR || material == Material.SPRUCE_TRAPDOOR ||
                material == Material.MANGROVE_TRAPDOOR  || material == Material.BAMBOO_TRAPDOOR;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[₪] Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Проверяем наличие права parkourutils.event
        if (!player.hasPermission("parkourutils.event")) {
            player.sendMessage(ChatColor.RED + "[₪] You don't have permission to use this command.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("parkourutils")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("enable")) {
                    isEnabled = true;
                    player.sendMessage(ChatColor.GREEN + "[₪] ParkourUtil methods are now enabled.");
                } else if (args[0].equalsIgnoreCase("disable")) {
                    isEnabled = false;
                    player.sendMessage(ChatColor.RED + "[₪] ParkourUtil methods are now disabled.");
                } else {
                    player.sendMessage(ChatColor.RED + "[₪] Invalid argument. Use /parkourutils enable or /parkourutils disable.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "[₪] Invalid usage. Use /parkourutils enable or /parkourutils disable.");
            }
        }

        return true;
    }
}