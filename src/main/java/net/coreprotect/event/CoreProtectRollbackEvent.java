package net.coreprotect.event;

import java.util.Collection;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after CoreProtect has applied the block changes of a rollback or restore to a single chunk.
 */
public class CoreProtectRollbackEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final World world;
    private final int chunkX;
    private final int chunkZ;
    private final List<Location> blocks;
    private final boolean restore;

    public CoreProtectRollbackEvent(@NotNull World world, int chunkX, int chunkZ, @NotNull Collection<Location> blocks, boolean restore) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = List.copyOf(blocks);
        this.restore = restore;
    }

    public @NotNull World getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    /**
     * @return locations of every block whose state was changed in this chunk
     */
    public @NotNull List<Location> getBlocks() {
        return blocks;
    }

    /**
     * @return true for a restore, false for a rollback
     */
    public boolean isRestore() {
        return restore;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
