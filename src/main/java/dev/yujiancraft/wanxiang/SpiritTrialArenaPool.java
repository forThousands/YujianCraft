package dev.yujiancraft.wanxiang;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A bounded set of reusable trial arenas near the dimension origin.
 *
 * <p>Runtime entity ids must never be used as world coordinates: they grow for the lifetime of
 * a server process and can eventually place an arena at the hard world border. Each occupied
 * arena owns volatile chunk tickets which disappear on restart and are removed when its session
 * ends.</p>
 */
final class SpiritTrialArenaPool {
    static final int ARENA_COUNT = 16;
    private static final int COLUMNS = 4;
    private static final int SPACING = 256;
    private static final int PLATFORM_Y = 126;
    private static final int PLATFORM_RADIUS = 8;
    private static final int TICKET_DISTANCE = 2;
    private static final TicketType<UUID> TICKET = TicketType.create(
            "yujiancraft_spirit_trial", UUID::compareTo);
    private static final Set<Integer> OCCUPIED = new HashSet<>();

    private SpiritTrialArenaPool() {
    }

    static synchronized Arena acquire(ServerLevel level, UUID ticketOwner) {
        for (int slot = 0; slot < ARENA_COUNT; slot++) {
            if (!OCCUPIED.add(slot)) continue;
            Arena arena = arena(slot);
            try {
                retainAndPrepare(level, arena, ticketOwner);
                return arena;
            } catch (RuntimeException exception) {
                releaseTickets(level, arena, ticketOwner);
                OCCUPIED.remove(slot);
                throw exception;
            }
        }
        return null;
    }

    static synchronized void release(ServerLevel level, Arena arena, UUID ticketOwner) {
        if (arena == null) return;
        try {
            if (level != null) releaseTickets(level, arena, ticketOwner);
        } finally {
            OCCUPIED.remove(arena.slot());
        }
    }

    private static Arena arena(int slot) {
        int column = slot % COLUMNS;
        int row = slot / COLUMNS;
        return new Arena(slot, column * SPACING, row * SPACING);
    }

    private static void retainAndPrepare(ServerLevel level, Arena arena, UUID ticketOwner) {
        for (ChunkPos chunk : coveredChunks(arena)) {
            level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_DISTANCE, ticketOwner);
            level.getChunk(chunk.x, chunk.z);
        }
        if (!isPlatformReady(level, arena)) buildPlatform(level, arena);
    }

    private static void releaseTickets(ServerLevel level, Arena arena, UUID ticketOwner) {
        for (ChunkPos chunk : coveredChunks(arena)) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_DISTANCE, ticketOwner);
        }
    }

    private static List<ChunkPos> coveredChunks(Arena arena) {
        BlockPos center = arena.platformCenter();
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - PLATFORM_RADIUS);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + PLATFORM_RADIUS);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - PLATFORM_RADIUS);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + PLATFORM_RADIUS);
        List<ChunkPos> chunks = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    private static boolean isPlatformReady(ServerLevel level, Arena arena) {
        BlockPos center = arena.platformCenter();
        for (int yOffset = 0; yOffset >= -3; yOffset--) {
            int radius = PLATFORM_RADIUS + yOffset;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius + 2) continue;
                    if (!level.getBlockState(center.offset(x, yOffset, z)).is(
                            yOffset == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES)) return false;
                }
            }
        }
        for (int index = 0; index < 8; index++) {
            if (!level.getBlockState(lightPosition(center, index)).is(Blocks.SEA_LANTERN)) return false;
        }
        return true;
    }

    private static void buildPlatform(ServerLevel level, Arena arena) {
        BlockPos center = arena.platformCenter();
        for (int yOffset = 0; yOffset >= -3; yOffset--) {
            int radius = PLATFORM_RADIUS + yOffset;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius + 2) continue;
                    level.setBlockAndUpdate(center.offset(x, yOffset, z), yOffset == 0
                            ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                            : Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }
        for (int index = 0; index < 8; index++) {
            level.setBlockAndUpdate(lightPosition(center, index), Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    private static BlockPos lightPosition(BlockPos center, int index) {
        double radians = Math.PI * 2.0D * index / 8.0D;
        return center.offset((int) Math.round(Math.cos(radians) * 7.0D), 1,
                (int) Math.round(Math.sin(radians) * 7.0D));
    }

    record Arena(int slot, int x, int z) {
        BlockPos platformCenter() {
            return new BlockPos(x, PLATFORM_Y, z + 3);
        }

        double playerX() {
            return x + 0.5D;
        }

        double playerZ() {
            return z + 0.5D;
        }

        double dummyZ() {
            return z + 8.5D;
        }

        double atmosphereZ() {
            return z + 3.5D;
        }

        boolean contains(double entityX, double entityY, double entityZ) {
            double dx = entityX - playerX();
            double dz = entityZ - playerZ();
            return entityY >= 112.0D && entityY <= 144.0D && dx * dx + dz * dz <= 24.0D * 24.0D;
        }
    }
}
