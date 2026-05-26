package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.WorldStatement;

class WorldInsertProcess {

    private static final Set<String> loggedWorldIdConflicts = ConcurrentHashMap.newKeySet();

    static void process(PreparedStatement preparedStmt, int batchCount, Statement statement, Object world, int worldId) {
        if (world instanceof String worldName) {
            try {
                String conflictingWorld = getWorldById(statement, worldId);
                if (worldName.equals(conflictingWorld)) {
                    cacheWorld(worldId, worldName);
                    return;
                }

                int existingWorldId = getWorldIdByName(statement, worldName);
                if (existingWorldId != -1) {
                    cacheWorld(existingWorldId, worldName);
                    return;
                }

                if (conflictingWorld == null) {
                    WorldStatement.insert(preparedStmt, batchCount, worldId, worldName);
                    cacheWorld(worldId, worldName);
                    return;
                }

                ConfigHandler.MAX_WORLD_ID.updateAndGet(curr -> Math.max(worldId, curr));
                String conflictKey = worldId + ":" + worldName;
                if (loggedWorldIdConflicts.add(conflictKey)) {
                    CoreProtect.getInstance().getSLF4JLogger().warn("Skipping world cache insert for '{}' with id {} because that id already belongs to '{}'.", worldName, worldId, conflictingWorld);
                }
            }
            catch (SQLException e) {
                CoreProtect.getInstance().getSLF4JLogger().warn("Failed to validate world cache entry '{}'", worldName, e);
            }
        }
    }

    private static String getWorldById(Statement statement, int worldId) throws SQLException {
        String query = "SELECT world FROM " + ConfigHandler.prefix + "world WHERE id = ? ORDER BY rowid ASC LIMIT 1";

        try (PreparedStatement lookupStatement = statement.getConnection().prepareStatement(query)) {
            lookupStatement.setInt(1, worldId);

            try (ResultSet resultSet = lookupStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("world");
                }
            }
        }

        return null;
    }

    private static int getWorldIdByName(Statement statement, String worldName) throws SQLException {
        String query = "SELECT id FROM " + ConfigHandler.prefix + "world WHERE world = ? ORDER BY id ASC, rowid ASC";
        List<Integer> ids = new ArrayList<>();

        try (PreparedStatement lookupStatement = statement.getConnection().prepareStatement(query)) {
            lookupStatement.setString(1, worldName);

            try (ResultSet resultSet = lookupStatement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getInt("id"));
                }
            }
        }

        for (int id : ids) {
            String cachedWorld = getWorldById(statement, id);
            if (worldName.equals(cachedWorld)) {
                return id;
            }
        }

        return -1;
    }

    private static void cacheWorld(int worldId, String worldName) {
        synchronized (ConfigHandler.WORLD_CACHE_LOCK) {
            ConfigHandler.worlds.merge(worldName, worldId, Math::min);
            ConfigHandler.worldsReversed.putIfAbsent(worldId, worldName);
            ConfigHandler.MAX_WORLD_ID.updateAndGet(curr -> Math.max(worldId, curr));
        }
    }
}
