package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.WorldStatement;

class WorldInsertProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, Statement statement, Object world, int worldId) {
        if (world instanceof String worldName) {
            synchronized (ConfigHandler.WORLD_CACHE_LOCK) {
                try {
                    String existingWorld = getWorldById(statement, worldId);
                    if (worldName.equals(existingWorld)) {
                        cacheWorld(worldId, worldName);
                        return;
                    }

                    int existingWorldId = getWorldIdByName(statement, worldName);
                    if (existingWorldId != -1) {
                        cacheWorld(existingWorldId, worldName);
                        return;
                    }

                    int insertWorldId = worldId;
                    if (existingWorld != null) {
                        insertWorldId = nextAvailableWorldId(statement, worldId);
                        CoreProtect.getInstance().getSLF4JLogger().warn("World cache id {} already belongs to '{}'. Assigning '{}' to id {}.", worldId, existingWorld, worldName, insertWorldId);
                    }

                    WorldStatement.insert(preparedStmt, batchCount, insertWorldId, worldName);
                    cacheWorld(insertWorldId, worldName);
                }
                catch (SQLException e) {
                    CoreProtect.getInstance().getSLF4JLogger().warn("Failed to validate world cache entry '{}'", worldName, e);
                }
            }
        }
    }

    private static String getWorldById(Statement statement, int worldId) throws SQLException {
        String query = "SELECT world FROM " + ConfigHandler.prefix + "world WHERE id = ? LIMIT 1";

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
        String query = "SELECT id FROM " + ConfigHandler.prefix + "world WHERE world = ? ORDER BY id ASC LIMIT 1";

        try (PreparedStatement lookupStatement = statement.getConnection().prepareStatement(query)) {
            lookupStatement.setString(1, worldName);

            try (ResultSet resultSet = lookupStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }

        return -1;
    }

    private static int nextAvailableWorldId(Statement statement, int minimumWorldId) throws SQLException {
        ConfigHandler.MAX_WORLD_ID.updateAndGet(curr -> Math.max(curr, minimumWorldId));

        int nextWorldId;
        do {
            nextWorldId = ConfigHandler.MAX_WORLD_ID.incrementAndGet();
        }
        while (getWorldById(statement, nextWorldId) != null);

        return nextWorldId;
    }

    private static void cacheWorld(int worldId, String worldName) {
        ConfigHandler.worlds.merge(worldName, worldId, Math::min);
        ConfigHandler.worldsReversed.putIfAbsent(worldId, worldName);
        ConfigHandler.MAX_WORLD_ID.updateAndGet(curr -> Math.max(worldId, curr));
    }
}
