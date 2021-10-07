package com.danikvitek.serverrp;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class ResourcePackCommand implements TabExecutor, Listener {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 2 && args[0].equals("upload")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    boolean success;
                    try {
                        URL rpLink = new URL(args[1]);
                        byte[] sha1FromLink = generateSHA1FromFile(downloadFile(rpLink));
                        byte[] sha1FromDB = Main.makeExecuteQuery(
                                "select sha1 from `" + Main.getDataBaseName() + "`.`" + Main.linkTable + "`;",
                                null,
                                (a, sha1RS) -> {
                                    try {
                                        if (sha1RS.next())
                                            return sha1RS.getBytes(1);
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }
                                    return null;
                                },
                                null
                        );
                        if (!Arrays.equals(sha1FromLink, sha1FromDB)) {
                            HashMap<Integer, byte[]> values = new HashMap<>();
                            values.put(1, sha1FromLink);
                            success =
                                    Main.makeExecuteUpdate(
                                            "truncate `" + Main.getDataBaseName() + "`.`" + Main.linkTable + "`;",
                                            null
                                    ) &&
                                    Main.makeExecuteUpdate(
                                            "insert into `" + Main.getDataBaseName() + "`.`" + Main.linkTable + "` values(?, '" + args[1] + "');",
                                            values
                                    );
                            for (Player player : Bukkit.getOnlinePlayers())
                                proposeRP(player);
                        }
                        else {
                            HashMap<Integer, byte[]> values = new HashMap<>();
                            values.put(1, sha1FromLink);
                            success = Main.makeExecuteUpdate(
                                    "update `" + Main.getDataBaseName() + "`.`" + Main.linkTable + "` set link = '" + args[1] + "' where sha1 = ?;",
                                    values
                            );
                        }
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        sender.sendMessage(ChatColor.RED + e.getMessage());
                        success = false;
                    } catch (NoSuchAlgorithmException | IOException e) {
                        e.printStackTrace();
                        success = false;
                    }
                    if (success)
                        sender.sendMessage(ChatColor.GREEN + "Ссылка успешно установлена");
                    else
                        sender.sendMessage(ChatColor.RED + "Ссылка не установлена");
                }
            }.runTaskAsynchronously(Main.getPlugin(Main.class));
        }
        else if (args.length == 1) {
            if (args[0].equals("get")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    Main.makeExecuteQuery(
                            "select * from `" + Main.getDataBaseName() + "`.`" + Main.linkTable + "`;",
                            null,
                            (a, rs) -> {
                                try {
                                    if (rs.next())
                                        player.setResourcePack(rs.getString(2), rs.getBytes(1));
                                } catch (SQLException e) {
                                    player.sendMessage(ChatColor.RED + "Пакет ресурсов не установлен");
                                }
                                return null;
                            },
                            null
                    );
                }
                else
                    sender.sendMessage(ChatColor.RED + "Команду может использовать только игрок");
            }
            else if (args[0].equals("link")) {
                String link = Main.makeExecuteQuery(
                        "select link from `" + Main.getDataBaseName() + "`.`" + Main.linkTable + "`;",
                        null,
                        (a, linkRS) -> {
                            try {
                                if (linkRS.next())
                                    return linkRS.getString(1);
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                            return null;
                        },
                        null
                );
                if (link != null) {
                    TextComponent linkText = new TextComponent(link);
                    linkText.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                    linkText.setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new Text(ChatColor.GOLD + "Тык!")
                    ));
                    linkText.setClickEvent(new ClickEvent(
                            ClickEvent.Action.OPEN_URL,
                            link
                    ));
                    sender.spigot().sendMessage(linkText);
                } else
                    sender.sendMessage(ChatColor.RED + "Ссылка не установлена");
            }
            else
                return false;
        }
        else return false;
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1)
            return copyPartialInnerMatches(
                    args[0],
                    Arrays.asList(
                            sender.hasPermission("serverrp.command.resourcepack.upload") ? "upload" : null,
                            sender.hasPermission("serverrp.command.resourcepack.get") ? "get" : null,
                            sender.hasPermission("serverrp.command.resourcepack.link") ? "link" : null
                    )
            ).stream().filter(Objects::nonNull).collect(Collectors.toList());
        else if (args.length == 2 && args[0].equals("upload") && args[1].length() == 0)
            return Collections.singletonList("<url>");
        return null;
    }

    private static List<String> copyPartialInnerMatches(String lookFor, List<String> lookIn) {
        return lookIn.stream().filter(s -> s.contains(lookFor)).collect(Collectors.toList());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        proposeRP(event.getPlayer());
    }

    private File downloadFile(URL url) throws IOException {
        File to = new File(Main.getPlugin(Main.class).getDataFolder(), "pack.zip");

        ReadableByteChannel rbc = Channels.newChannel(url.openStream());
        FileOutputStream fOutStream = new FileOutputStream(to);

        fOutStream.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        fOutStream.close();
        rbc.close();

        return to;
    }

    private byte[] generateSHA1FromFile(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        InputStream fis = new FileInputStream(file);
        int n = 0;
        byte[] buffer = new byte[8192];
        while (n != -1) {
            n = fis.read(buffer);
            if (n > 0) {
                digest.update(buffer, 0, n);
            }
        }
        return digest.digest();
    }

    private static void proposeRP(Player player) {
        TextComponent message = new TextComponent("====================\n\n");
        TextComponent getRPButton = new TextComponent("[УСТАНОВИТЬ ПАКЕТ РЕСУРСОВ]");
        getRPButton.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        getRPButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.DARK_AQUA + "Нажмите, чтобы открыть окно загрузки")
        ));
        getRPButton.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/serverrp:resourcepack get"
        ));
        TextComponent getLinkButton = new TextComponent("[ПОЛУЧИТЬ ССЫЛКУ]");
        getLinkButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        getLinkButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.DARK_GREEN + "Нажмите, чтобы открыть ссылку на пакет ресурсов")
        ));
        getLinkButton.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/serverrp:resourcepack link"
        ));
        message.addExtra(getRPButton);
        message.addExtra("\n");
        message.addExtra(getLinkButton);
        message.addExtra("\n\n====================");

        player.spigot().sendMessage(message);
    }
}
