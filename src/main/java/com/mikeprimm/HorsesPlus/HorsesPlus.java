package com.mikeprimm.HorsesPlus;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.ConversationPrefix;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.conversations.ValidatingPrompt;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import com.forgenz.horses.Horses;
import com.forgenz.horses.PlayerHorse;
import com.forgenz.horses.Stable;
import com.forgenz.horses.config.HorseTypeConfig;
import com.forgenz.horses.config.HorsesConfig;
import com.forgenz.horses.config.HorsesPermissionConfig;

public class HorsesPlus extends JavaPlugin {
    public static Logger log;
    public static Horses horses;
    public ConversationFactory cfact;
    public ConversationFactory cbuyfact;
    public static final String signlabel = "<Stable>";
    public static final String goodsignlabel = "-<Stable>-";
    public static final String summonlabel = "Summon";
    public static final String buylabel = "Buy";
    public static final String dismisslabel = "Dismiss";
    
    private class HorseSelectPrompt extends ValidatingPrompt {

        @Override
        public String getPromptText(ConversationContext cc) {
            Player p = (Player) cc.getForWhom();
            String prompt = "Select which horse (";
            Stable stable = horses.getHorseDatabase().getPlayersStable(p);
            boolean first = true;
            Iterator<PlayerHorse> iter = stable.iterator();
            while(iter.hasNext()) {
                PlayerHorse ph = iter.next();
                if (!first) {
                    prompt += ",";
                }
                prompt += ph.getDisplayName();
                first = false;
            }
            prompt += ")";
            return prompt;
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext cc,
                final String selection) {
            final Player p = (Player) cc.getForWhom();
            HorsesPlus.this.getServer().getScheduler().runTask(HorsesPlus.this, new Runnable() {
                public void run() {
                    Stable stable = horses.getHorseDatabase().getPlayersStable(p);
                    PlayerHorse ph = stable.findHorse(selection, true);
                    if (ph != null) {
                        ph.spawnHorse(p);
                        p.sendMessage("Summoning horse '" + ph.getDisplayName() + "'");
                    }
                    else {
                        p.sendMessage("Horse '" + selection + "' not found");
                    }
                }
            });
            return Prompt.END_OF_CONVERSATION;
        }

        @Override
        protected boolean isInputValid(ConversationContext cc, String selection) {
            Player p = (Player) cc.getForWhom();
            Stable stable = horses.getHorseDatabase().getPlayersStable(p);
            PlayerHorse ph = stable.findHorse(selection, true);
            return (ph != null);
        }
    }

    private class HorseNamePrompt extends ValidatingPrompt {

        @Override
        public String getPromptText(ConversationContext cc) {
            return "Enter name of new horse";
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext cc,
                final String name) {
            final Player p = (Player) cc.getForWhom();
            final HorseTypeConfig type = (HorseTypeConfig) cc.getSessionData("type");
            final Boolean saddle = (Boolean) cc.getSessionData("saddle");
            HorsesPlus.this.getServer().getScheduler().runTask(HorsesPlus.this, new Runnable() {
                public void run() {
                    Stable stable = horses.getHorseDatabase().getPlayersStable(p);
                    // Create horse for player
                    PlayerHorse horse = stable.createHorse(name, type, saddle);
                    if (horse == null) {
                        p.sendMessage("Error buying horse: " + type);
                        return;
                    }
                    p.sendMessage("Horse named '" + name + "' of type '" + type + "' placed in stable");
                }
            });
            return Prompt.END_OF_CONVERSATION;
        }

        @Override
        protected boolean isInputValid(ConversationContext cc, String name) {
            Player p = (Player) cc.getForWhom();
            HorsesConfig cfg = horses.getHorsesConfig();
            HorsesPermissionConfig pcfg = cfg.getPermConfig(p);
            // Validate horse name
            if (name.length() > pcfg.maxHorseNameLength) {
                p.sendMessage("Horse name is too long: " + name);
                return false;
            }
            // Make uncolorized name for pattern + length validation
            String basename = name;
            if (name.contains("&")) {
                basename = ChatColor.translateAlternateColorCodes('&', name);
                basename = ChatColor.stripColor(basename);
            }
            if (basename.length() == 0) {
                p.sendMessage("Horse name must be defined");
                return false;
            }
            if (cfg.rejectedHorseNamePattern.matcher(basename).find()) {
                p.sendMessage("Horse name is not valid: " +  basename);
                return false;
            }
            Stable stable = horses.getHorseDatabase().getPlayersStable(p);
            PlayerHorse ph = stable.findHorse(name, true);
            if (ph != null) {
                p.sendMessage("Name is already in use");
                return false;
            }
            return true;
        }
    }

    private class SignListener implements Listener {
        @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
        public void onSignChange(SignChangeEvent event) {
            String[] lines = event.getLines();
            Player p = event.getPlayer();
            if (p == null) return;
            if (lines[0].equals(signlabel)) {   // One of our signs being created?
                if (lines[1].startsWith(summonlabel)) {
                    if (!(p.hasPermission("horsesplus.sign.summon.create") || p.hasPermission("horsesplus.sign.create"))) {
                        p.sendMessage("Not permitted to create horse summon signs");
                        event.setCancelled(true);
                        return;
                    }
                }
                else if (lines[1].startsWith(buylabel)) {
                    String type = lines[2].trim();
                    if (horses.getHorsesConfig().getHorseTypeConfigLike(p, type) == null) {
                        p.sendMessage("Not valid horse type: " + type);
                        event.setCancelled(true);
                        return;
                    }
                    if (!(p.hasPermission("horsesplus.sign.buy.create") || p.hasPermission("horsesplus.sign.buy." + type + ".create") || p.hasPermission("horsesplus.sign.create"))) {
                        p.sendMessage("Not permitted to create buy horse signs for type " + type);
                        event.setCancelled(true);
                        return;
                    }
                }
                else if (lines[1].startsWith(dismisslabel)) {
                    if (!(p.hasPermission("horsesplus.sign.dismiss.create") || p.hasPermission("horsesplus.sign.create"))) {
                        p.sendMessage("Not permitted to create horse dismiss signs");
                        event.setCancelled(true);
                        return;
                    }
                }
                else {  // Not good sign
                    p.sendMessage("Not valid horse sign option:" + lines[1]);
                    event.setCancelled(true);
                }
                // If we're here, its a valid sign
                event.setLine(0, goodsignlabel); // Set to good label
            }
            else if (lines[0].equals(goodsignlabel)) {
                event.setLine(0, goodsignlabel + " "); // Set to not be same as good label
            }
        }
        @EventHandler(ignoreCancelled=true)
        public void onBlockInteract(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            Block blk = event.getClickedBlock();
            if ((blk != null) && (blk.getType() == Material.SIGN) || (blk.getType() == Material.SIGN_POST)) {
                BlockState bs = blk.getState();
                Sign sbs = (Sign) bs;
                if (!goodsignlabel.equals(sbs.getLine(0))) { // If not one of ours
                    return;
                }
                String cmd = sbs.getLine(1);
                if (cmd.startsWith(summonlabel)) {
                    handleSummonEvent(event.getPlayer());
                }
                else if (cmd.startsWith(buylabel)) {
                    handleBuyEvent(event.getPlayer(), sbs.getLine(2).trim());
                }
                else if (cmd.startsWith(dismisslabel)) {
                    handleDismissEvent(event.getPlayer());
                }
            }
        }
    }
    
    private void handleSummonEvent(Player player) {
        if (!(player.hasPermission("horsesplus.sign.summon.use") || player.hasPermission("horsesplus.sign.use"))) {
            player.sendMessage("You are not permitted to use this sign.");
            return;
        }
        // Get player's stable
        Stable stable = horses.getHorseDatabase().getPlayersStable(player);
        // If only one horse, just summon it
        if (stable.getHorseCount() < 2) {
            PlayerHorse ph = stable.iterator().next();
            if (ph != null) {
                ph.spawnHorse(player);
                player.sendMessage("Summoning horse '" + ph.getDisplayName() + "'");
            }
            else {
                player.sendMessage("You have no horses to summon.");
            }
        }
        else {  // Else, need to prompt for horse
            Conversation convo = cfact.buildConversation(player);
            convo.begin();
        }
    }

    private void handleBuyEvent(Player player, String type) {
        if (!(player.hasPermission("horsesplus.sign.buy." + type + ".use") || player.hasPermission("horsesplus.sign.use") || player.hasPermission("horsesplus.sign.buy.use"))) {
            player.sendMessage("You are not permitted to use this sign.");
            return;
        }
        HorsesConfig cfg = horses.getHorsesConfig();
        HorsesPermissionConfig pcfg = cfg.getPermConfig(player);

        HorseTypeConfig typecfg = pcfg.getHorseTypeConfigLike(type);
        if (typecfg == null) {
            player.sendMessage("Horse type not found: " + type);
            return;
        }
        // Get player's stable
        Stable stable = horses.getHorseDatabase().getPlayersStable(player);
        if (stable.getHorseCount() >= pcfg.maxHorses) { // Already at limit
            player.sendMessage("You have already reached your horse count limit");
            return;
        }
        // Start convo for name
        Conversation convo = cbuyfact.buildConversation(player);
        convo.getContext().setSessionData("type",  typecfg);
        convo.getContext().setSessionData("saddle", pcfg.startWithSaddle);
        convo.begin();
    }
    
    private void handleDismissEvent(Player player) {
        if (!(player.hasPermission("horsesplus.sign.dismiss.use") || player.hasPermission("horsesplus.sign.use"))) {
            player.sendMessage("You are not permitted to use this sign.");
            return;
        }
        // Get player's stable
        Stable stable = horses.getHorseDatabase().getPlayersStable(player);
        PlayerHorse ph = stable.getActiveHorse();
        if (ph == null) {
            player.sendMessage("You have no horses active.");
            return;
        }
        ph.removeHorse();
        player.sendMessage("Horse '" + ph.getDisplayName() + "' has been stabled.");
    }

    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }

    @Override
    public void onLoad() {
        log = this.getLogger();
    }
    
    @Override
    public void onEnable() {
        info("HorsesPlus v" + this.getDescription().getVersion());
        Plugin h = this.getServer().getPluginManager().getPlugin("Horses");
        if (h == null) {
            severe("Horses plugin not found!");
            return;
        }
        horses = (Horses)h;
        // Register sign listener
        this.getServer().getPluginManager().registerEvents(new SignListener(), this);
        // Setup conversation factory for summon
        cfact = new ConversationFactory(this);
        cfact.withModality(true);
        cfact.withFirstPrompt(new HorseSelectPrompt());
        cfact.withPrefix(new ConversationPrefix() {
            @Override
            public String getPrefix(ConversationContext cc) {
                return "Summon Horse: ";
            }
        });
        // Setup conversation factory for buy
        cbuyfact = new ConversationFactory(this);
        cbuyfact.withModality(true);
        cbuyfact.withFirstPrompt(new HorseNamePrompt());
        cbuyfact.withPrefix(new ConversationPrefix() {
            @Override
            public String getPrefix(ConversationContext cc) {
                return "Buy Horse: ";
            }
        });
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String cmdname, String[] args) {
        if (cmd.getName().equals("hplus")) {
            if (args.length < 1) {
                sender.sendMessage("Commands supported: give, take, list");
                return true;
            }
            String scmd = args[0];
            String argsm1[] = new String[args.length - 1];
            System.arraycopy(args, 1, argsm1, 0, argsm1.length);
            args = argsm1;
            if (scmd.equals("give")) {
                return handleGiveHorse(sender, args);
            }
            else if (scmd.equals("take")) {
                return handleTakeHorse(sender, args);
            }
            else {
                sender.sendMessage("Commands supported: give, take");
                return true;
            }
        }
        return false;
    }
    private boolean handleTakeHorse(CommandSender sender, String[] args) {
        if (sender.hasPermission("horsesplus.command.take") == false) {
            sender.sendMessage("Not permitted to use command");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Parameters required: <player> <horsename|*>");
            return true;
        }
        String player = args[0];
        String name = args[1];
        // Find player
        Player plyr = getServer().getPlayerExact(player);
        if (plyr == null) {
            sender.sendMessage("Player not found: " + player);
            return true;
        }
        if (name.contains("&")) {
            name = ChatColor.translateAlternateColorCodes('&', name);
            name = ChatColor.stripColor(name);
        }
        // Get player's stable
        Stable stable = horses.getHorseDatabase().getPlayersStable(plyr);
        if (name.equals("*")) { // All horses
            int cnt = 0;
            PlayerHorse ph = stable.getActiveHorse();
            if (ph != null) {
                ph.deleteHorse();
                cnt++;
            }
            Iterator<PlayerHorse> iter = stable.iterator();
            while (iter.hasNext()) {
                ph = iter.next();
                iter.remove();
                ph.deleteHorse();
                cnt++;
            }
            sender.sendMessage("Deleted " + cnt + " horses for player '" + player + "'");;
        }
        else {
            PlayerHorse ph = stable.findHorse(name, true);
            if (ph != null) {
                ph.deleteHorse();
                sender.sendMessage("Deleted horse '" + name + "' for player '" +player + "'");
            }
            else {
                sender.sendMessage("Horse named '" + name + "' not found for player '" + player + "'");
            }
        }
        return true;
    }
    private boolean handleGiveHorse(CommandSender sender, String[] args) {
        if (sender.hasPermission("horsesplus.command.give") == false) {
            sender.sendMessage("Not permitted to use command");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Parameters required: <player> <horsetype> <horsename>");
            return true;
        }
        String player = args[0];
        String type = args[1];
        String name; // Use type as name if only 2 args
        if (args.length > 2)
            name = args[2];
        else
            name = type; // Use type as name if only 2 args
        // Find player
        Player plyr = getServer().getPlayerExact(player);
        if (plyr == null) {
            sender.sendMessage("Player not found: " + player);
            return true;
        }
        HorsesConfig cfg = horses.getHorsesConfig();
        HorsesPermissionConfig pcfg = cfg.getPermConfig(plyr);

        HorseTypeConfig typecfg = pcfg.getHorseTypeConfigLike(type);
        if (typecfg == null) {
            sender.sendMessage("Horse type not found: " + type);
            return true;
        }
        // Validate horse name
        if (name.length() > pcfg.maxHorseNameLength) {
            sender.sendMessage("Horse name is too long: " + name);
            return true;
        }
        // Make uncolorized name for pattern + length validation
        String basename = name;
        if (name.contains("&")) {
            basename = ChatColor.translateAlternateColorCodes('&', name);
            basename = ChatColor.stripColor(basename);
        }
        if (basename.length() == 0) {
            sender.sendMessage("Horse name must be defined");
            return true;
        }
        if (cfg.rejectedHorseNamePattern.matcher(basename).find()) {
            sender.sendMessage("Horse name is not valid: " +  basename);
            return true;
        }
        // Get player's stable
        Stable stable = horses.getHorseDatabase().getPlayersStable(plyr);
        if (stable.getHorseCount() >= pcfg.maxHorses) { // Already at limit
            sender.sendMessage("Player '" + player + "' already has reached their horse count limit");
            return true;
        }
        if (stable.findHorse(name, true) != null) {
            sender.sendMessage("Player '" + player + "' already has horse with name '" + name + "' - try another");
            return true;
        }
        if (cfg.rejectedHorseNamePattern.matcher(name).find()) {
            sender.sendMessage("Illegal horse name: " + name);
            return true;
        }
        // Create horse for player
        PlayerHorse horse = stable.createHorse(name, typecfg, pcfg.startWithSaddle);
        if (horse == null) {
            sender.sendMessage("Error creating horse: " + type);
            return true;
        }
        sender.sendMessage("Horse named '" + name + "' of type '" + type + "' created for player '" + player + "'");
        
        return true;
    }
}
