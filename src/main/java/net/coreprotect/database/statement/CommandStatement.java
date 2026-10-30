package net.coreprotect.database.statement;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;

import java.sql.PreparedStatement;
import net.coreprotect.utility.ErrorReporter;

public class CommandStatement {

    private CommandStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, long time, int user, int wid, int x, int y, int z, String message, boolean cancelled) {
        try {
            preparedStmt.setLong(1, time);
            preparedStmt.setInt(2, user);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setString(7, message);
            preparedStmt.setBoolean(8, cancelled);
            preparedStmt.setLong(9, CoreProtect.getInstance().rowNumbers().nextRowNumber("command", preparedStmt.getConnection()));
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % Config.getGlobal().BATCH_SIZE == 0) {
                preparedStmt.executeBatch();
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }
}
