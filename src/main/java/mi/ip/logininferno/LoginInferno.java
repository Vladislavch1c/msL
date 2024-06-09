package mi.ip.logininferno;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.reflect.Method;


import java.io.File;
import java.io.IOException;
import java.util.*;

public class LoginInferno extends JavaPlugin implements CommandExecutor, Listener {
    private HashMap<UUID, Long> sessionMap = new HashMap<>();
    private FileConfiguration playerDataConfig;
    private File playerDataFile;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private FileConfiguration config;
    private File configFile;

    // Map
    private Map<UUID, String> tempPasswords = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("InfernoAuth is enabled!");
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("reg").setExecutor(this);
        this.getCommand("l").setExecutor(this);
        this.getCommand("linferno").setExecutor(this);

        // Load data configuration
        playerDataFile = new File(getDataFolder(), "base.yml");
        if (!playerDataFile.exists()) {
            playerDataFile.getParentFile().mkdirs();
            try {
                playerDataFile.createNewFile();
                getLogger().info("The base.yml file has been created!");
            } catch (IOException e) {
                getLogger().warning("Failed to create base.yml file: " + e.getMessage());
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            messagesFile.getParentFile().mkdirs();
            generateMessagesYml(messagesFile);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    @Override
    public void onDisable() {
        getLogger().info("InfernoAuth is disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!playerDataConfig.contains("passwords." + player.getName())) {
            player.sendMessage(getMessage("welcome.register"));
            sessionMap.put(playerId, Long.MAX_VALUE);
        } else {
            player.sendMessage(getMessage("welcome.login"));
            sessionMap.put(playerId, Long.MAX_VALUE);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (sessionMap.containsKey(player.getUniqueId())) {
            long sessionDuration = config.getLong("session-duration", 1000);
            if (System.currentTimeMillis() - sessionMap.get(player.getUniqueId()) > sessionDuration) {
                sessionMap.remove(player.getUniqueId());
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', "&aSelect your password"))) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.RED_CONCRETE) {
                Player player = (Player) event.getWhoClicked();
                UUID playerId = player.getUniqueId();
                int clickedNumber = event.getSlot();

                ItemStack greenConcrete = new ItemStack(Material.GREEN_CONCRETE, 1);
                ItemMeta meta = greenConcrete.getItemMeta();
                ((ItemMeta) meta).setDisplayName(ChatColor.YELLOW + String.valueOf(clickedNumber));
                greenConcrete.setItemMeta(meta);
                event.getInventory().setItem(event.getSlot(), greenConcrete);

                player.playSound(player.getLocation(), Sound.UI_LOOM_TAKE_RESULT, 1.0f, 1.0f);

                tempPasswords.put(playerId, tempPasswords.getOrDefault(playerId, "") + clickedNumber);

                if (tempPasswords.get(playerId).length() == 4) {
                    if (playerDataConfig.contains("passwords." + player.getName())) {
                        // Login
                        String storedPass = playerDataConfig.getString("passwords." + player.getName() + ".pass");
                        if (storedPass.equals(tempPasswords.get(playerId))) {
                            player.sendMessage(getMessage("login.success"));
                            sessionMap.put(playerId, System.currentTimeMillis());
                            player.playSound(player.getLocation(), getSound("sounds.success", Sound.ENTITY_PLAYER_LEVELUP), 1.0f, 1.0f); // Play success sound
                        } else {
                            player.sendMessage(getMessage("login.incorrect_password"));
                            player.playSound(player.getLocation(), getSound("sounds.failure", Sound.ENTITY_VILLAGER_NO), 1.0f, 1.0f); // Play failure sound
                        }
                    } else {
                        // Registration
                        playerDataConfig.set("passwords." + player.getName() + ".pass", tempPasswords.get(playerId));
                        playerDataConfig.set("passwords." + player.getName() + ".logged_in", true);
                        playerDataConfig.set("passwords." + player.getName() + ".ip", player.getAddress().getHostString());
                        try {
                            playerDataConfig.save(playerDataFile);
                            sessionMap.put(playerId, System.currentTimeMillis());
                            player.sendMessage(getMessage("registration.success"));
                            player.playSound(player.getLocation(), getSound("sounds.success", Sound.ENTITY_PLAYER_LEVELUP), 1.0f, 1.0f); // Play success sound
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    tempPasswords.remove(playerId);
                    player.closeInventory();
                }
            }
        }
    }

    private void openPasswordGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, ChatColor.translateAlternateColorCodes('&', "&aSelect your password"));
        for (int i = 0; i < 9; i++) {
            ItemStack redConcrete = new ItemStack(Material.RED_CONCRETE, 1);
            ItemMeta meta = redConcrete.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + String.valueOf(i));
            redConcrete.setItemMeta(meta);
            gui.setItem(i, redConcrete);
        }
        player.openInventory(gui);
    }


    private void reloadConfigFiles() {
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        config = YamlConfiguration.loadConfiguration(configFile);
        getLogger().info("The base.yml, messages.yml and config.yml files have been reloaded!");
    }

    private String getMessage(String key) {
        List<String> messages = messagesConfig.getStringList(key);
        if (messages.isEmpty()) {
            return "Message not found: " + key;
        }
        return String.join("\n\n", messages.stream()
                .map(message -> ChatColor.translateAlternateColorCodes('&', message))
                .collect(Collectors.toList()));
    }

    private Sound getSound(String path, Sound defaultSound) {
        String soundName = config.getString(path, defaultSound.name());
        try {
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound name in config.yml: " + soundName + ". Using default: " + defaultSound.name());
            return defaultSound;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reg")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                UUID playerId = player.getUniqueId();
                if (!playerDataConfig.contains("passwords." + player.getName())) {
                    openPasswordGUI(player);
                } else {
                    player.sendMessage(getMessage("registration.already_registered"));
                }
            } else {
                sender.sendMessage(getMessage("error.players_only"));
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("l")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                UUID playerId = player.getUniqueId();
                if (playerDataConfig.contains("passwords." + player.getName())) {
                    openPasswordGUI(player);
                } else {
                    player.sendMessage(getMessage("login.not_registered"));
                }
            } else {
                sender.sendMessage(getMessage("error.players_only"));
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("linferno")) {
            if (sender instanceof Player && !sender.hasPermission("logininferno.reload")) {
                sender.sendMessage(getMessage("error.no_permission"));
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfigFiles();
                sender.sendMessage(getMessage("reload.success"));
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private void generateMessagesYml(File messagesFile) {
        YamlConfiguration config = new YamlConfiguration();

        config.set("welcome.register", Arrays.asList(
                "&aWelcome! Please use the &b/reg&a command to register.",
                "&bFor the best game, read the rules on our website."
        ));
        config.set("welcome.login", Arrays.asList(
                "&aWelcome back! Enter your login password &b/l."
        ));

        config.set("registration.success", Arrays.asList(
                "&aYou have successfully registered!"
        ));
        config.set("registration.already_registered", Arrays.asList(
                "&cYou are already registered!"
        ));
        config.set("registration.invalid_password", Arrays.asList(
                "&cInvalid password format! Please use a more secure password."
        ));

        config.set("login.success", Arrays.asList(
                "&aYou have successfully entered the game!"
        ));
        config.set("login.incorrect_password", Arrays.asList(
                "&cIncorrect password!"
        ));
        config.set("login.not_registered", Arrays.asList(
                "&cYou are not registered yet!"
        ));

        config.set("error.players_only", Arrays.asList(
                "&cThis command can only be used by players!"
        ));
        config.set("error.no_permission", Arrays.asList(
                "&cYou do not have permission to run this command!"
        ));

        config.set("reload.success", Arrays.asList(
                "&aThe base.yml and messages.yml files have been successfully reloaded!"
        ));

        config.set("gui.title", Arrays.asList(
                "&aSelect your password"
        ));

        try {
            config.save(messagesFile);
            getLogger().info("The messages.yml file was created with default values!");
        } catch (IOException e) {
            getLogger().warning("Failed to create messages.yml file: " + e.getMessage());
        }
    }

    @Override
    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(getDataFolder(), "config.yml");
        }
        if (!configFile.exists()) {
            this.saveResource("config.yml", false);
        }
    }
}