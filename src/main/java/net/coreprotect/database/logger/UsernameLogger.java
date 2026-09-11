package net.coreprotect.database.logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;

public class UsernameLogger {

    private static final Set<String> appliedUserUpdates = ConcurrentHashMap.newKeySet();

    private UsernameLogger() {
        throw new IllegalStateException("Database class");
    }

    private static String storedName(Connection connection, int rowid) throws SQLException {
        try (PreparedStatement preparedStmt = connection.prepareStatement("SELECT user FROM " + ConfigHandler.prefix + "user WHERE rowid = ? LIMIT 1")) {
            preparedStmt.setInt(1, rowid);
            try (ResultSet rs = preparedStmt.executeQuery()) {
                return rs.next() ? rs.getString("user") : null;
            }
        }
    }

    public static void log(Connection connection, String user, String uuid, int configUsernames, int time) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }

            int idRow = -1;
            String userRow = null;
            String query = "SELECT rowid as id, user FROM " + ConfigHandler.prefix + "user WHERE uuid = ? LIMIT 0, 1";
            try (PreparedStatement preparedStmt = connection.prepareStatement(query)) {
                preparedStmt.setString(1, uuid);
                ResultSet rs = preparedStmt.executeQuery();
                while (rs.next()) {
                    idRow = rs.getInt("id");
                    userRow = rs.getString("user").toLowerCase(Locale.ROOT);
                }
            }

            boolean nameChanged = false;
            if (userRow == null) {
                Integer cachedId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
                if (cachedId == null) {
                    return;
                }

                idRow = cachedId;
                userRow = storedName(connection, idRow);
                nameChanged = userRow != null && !user.equalsIgnoreCase(userRow);
            }
            else if (!user.equalsIgnoreCase(userRow)) {
                nameChanged = true;
            }

            boolean update = nameChanged;
            if (nameChanged) {
                if (Config.getGlobal().USERNAME_UPDATES && appliedUserUpdates.add(uuid + "." + user.toLowerCase(Locale.ROOT))) {
                    try (PreparedStatement preparedStmt = connection.prepareStatement("ALTER TABLE " + ConfigHandler.prefix + "user UPDATE user = ?, uuid = ? WHERE rowid = ?")) {
                        preparedStmt.setString(1, user);
                        preparedStmt.setString(2, uuid);
                        preparedStmt.setInt(3, idRow);
                        preparedStmt.executeUpdate();
                    }
                }

                /*
                    //Commented out to prevent potential issues if player manages to stay logged in with old username
                    if (ConfigHandler.playerIdCache.get(user_row)!=null){
                        int cache_id = ConfigHandler.playerIdCache.get(user_row);
                        if (cache_id==id_row){
                            ConfigHandler.playerIdCache.remove(user_row);
                        }
                    }
                 */
            }
            else {
                boolean foundUUID = false;
                query = "SELECT rowid as id FROM " + ConfigHandler.prefix + "username_log WHERE uuid = ? AND user = ? LIMIT 0, 1";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                    preparedStatement.setString(1, uuid);
                    preparedStatement.setString(2, user);
                    ResultSet rs = preparedStatement.executeQuery();
                    while (rs.next()) {
                        foundUUID = true;
                    }
                }

                if (!foundUUID) {
                    update = true;
                }
            }

            if (update && configUsernames == 1) {
                try (PreparedStatement preparedStmt = connection.prepareStatement("INSERT INTO " + ConfigHandler.prefix + "username_log (time, uuid, user) VALUES (?, ?, ?)")) {
                    preparedStmt.setInt(1, time);
                    preparedStmt.setString(2, uuid);
                    preparedStmt.setString(3, user);
                    preparedStmt.executeUpdate();
                }
            }

            ConfigHandler.playerIdCache.put(user.toLowerCase(Locale.ROOT), idRow);
            ConfigHandler.playerIdCacheReversed.put(idRow, user);
            ConfigHandler.uuidCache.put(user.toLowerCase(Locale.ROOT), uuid);
            ConfigHandler.uuidCacheReversed.put(uuid, user);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
