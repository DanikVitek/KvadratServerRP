package com.danikvitek.serverrp;

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public final class Main extends JavaPlugin {

    private static DataSource dataSource;
    public static final String linkTable = "rp_link";
    private static String dataBaseName;

    public static String getDataBaseName() {
        return dataBaseName;
    }

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        dataBaseName = getConfig().getString("database.name");

        // Database
        MysqlConnectionPoolDataSource mcpDataSource = new MysqlConnectionPoolDataSource();
        mcpDataSource.setServerName(getConfig().getString("database.host"));
        mcpDataSource.setPort(getConfig().getInt("database.port"));
        mcpDataSource.setDatabaseName(dataBaseName);
        mcpDataSource.setUser(getConfig().getString("database.login"));
        mcpDataSource.setPassword(getConfig().getString("database.password"));
        dataSource = mcpDataSource;
        if (!isValidConnection()) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Не удалось подключиться к базе данных");
            Bukkit.getPluginManager().disablePlugin(Main.getPlugin(Main.class));
        }
        createRPTable();

        ResourcePackCommand rpCommand = new ResourcePackCommand();
        Objects.requireNonNull(getCommand("resourcepack")).setExecutor(rpCommand);
        Bukkit.getPluginManager().registerEvents(rpCommand, this);
    }

    private static @Nullable Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean isValidConnection() {
        try {
            Connection connection = getConnection();
            if (connection != null)
                return connection.isValid(1000);
            else return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void makeExecute(@NotNull String query, @Nullable HashMap<Integer, Class<?>> values) {
        Connection connection = getConnection();
        if (connection != null)
            try {
                PreparedStatement ps = connection.prepareStatement(query);
                if (values != null)
                    for (Map.Entry<Integer, Class<?>> value: values.entrySet())
                        ps.setObject(value.getKey(), value.getValue());
                ps.execute();
                connection.close();
            } catch (SQLException e) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "QUERY: " + query);
                e.printStackTrace();
            }
    }

    public static boolean makeExecuteUpdate(@NotNull String query, @Nullable HashMap<Integer, ?> values) {
        Connection connection = getConnection();
        if (connection != null)
            try {
                PreparedStatement ps = connection.prepareStatement(query);
                if (values != null)
                    for (Map.Entry<Integer, ?> value: values.entrySet())
                        ps.setObject(value.getKey(), value.getValue());
                ps.executeUpdate();
                connection.close();
            } catch (SQLException e) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "QUERY: " + query);
                e.printStackTrace();
                return false;
            }
        return true;
    }

    public static @Nullable <T> T makeExecuteQuery(@NotNull String query, @Nullable HashMap<Integer, ?> values, @Nullable BiFunction<List<?>, ResultSet, T> function, @Nullable List<?> args) {
        Connection conn = getConnection();
        T result = null;
        if (conn != null) {
            try {
                PreparedStatement ps = conn.prepareStatement(query);
                if (values != null)
                    for (Map.Entry<Integer, ?> value: values.entrySet())
                        ps.setObject(value.getKey(), value.getValue());
                ResultSet rs = ps.executeQuery();
                if (function != null)
                    result = function.apply(args, rs);
                conn.close();
            } catch (SQLException e) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "QUERY: " + query);
                e.printStackTrace();
            }
        }
        return result;
    }

    private void createRPTable() {
        String createTableQuery =
                "create table if not exists `" + dataBaseName + "`.`" + linkTable + "`(" +
                        "sha1 binary(20) not null unique," +
                        "link varchar(256) not null unique," +
                        "primary key (sha1)" +
                ");";
        makeExecute(createTableQuery, null);
    }
}
