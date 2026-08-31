package dev.yujiancraft.combat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent, owner-specific entities which the sword spirit must never target or damage. */
public final class TargetProtectionManager {
    private static final String ROOT_KEY = "YujianCraftTargetProtection";
    private static final String LIST_KEY = "Entries";
    private static final String UUID_KEY = "Uuid";
    private static final String NAME_KEY = "Name";
    private static final String TYPE_KEY = "Type";

    private TargetProtectionManager() {
    }

    public record Entry(UUID uuid, String name, String typeId) {
    }

    public static boolean isProtected(ServerPlayer owner, LivingEntity target) {
        return isNaturallyProtected(target) || entries(owner).stream().anyMatch(entry -> entry.uuid().equals(target.getUUID()));
    }

    /** Companions are protected by an unremovable safety rule rather than a per-player toggle. */
    public static boolean isNaturallyProtected(LivingEntity target) {
        if (target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) return true;
        if (target instanceof AbstractHorse horse && horse.isTamed()) return true;
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (type == null) return false;
        String namespace = type.getNamespace();
        String path = type.getPath();
        return "touhou_little_maid".equals(namespace)
                && (path.contains("maid") || target.getClass().getName().toLowerCase().contains("maid"));
    }

    /** @return true when the target is protected after this operation. */
    public static boolean toggle(ServerPlayer owner, LivingEntity target) {
        if (isNaturallyProtected(target)) return true;
        List<Entry> values = new ArrayList<>(entries(owner));
        boolean removed = values.removeIf(entry -> entry.uuid().equals(target.getUUID()));
        if (!removed) {
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
            values.add(new Entry(target.getUUID(), target.getName().getString(),
                    type == null ? EntityType.getKey(target.getType()).toString() : type.toString()));
        }
        save(owner, values);
        return !removed;
    }

    public static boolean remove(ServerPlayer owner, UUID targetId) {
        List<Entry> values = new ArrayList<>(entries(owner));
        boolean changed = values.removeIf(entry -> entry.uuid().equals(targetId));
        if (changed) save(owner, values);
        return changed;
    }

    public static List<Entry> entries(ServerPlayer owner) {
        ListTag list = root(owner).getList(LIST_KEY, Tag.TAG_COMPOUND);
        List<Entry> values = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            if (!tag.hasUUID(UUID_KEY)) continue;
            values.add(new Entry(tag.getUUID(UUID_KEY), tag.getString(NAME_KEY), tag.getString(TYPE_KEY)));
        }
        return List.copyOf(values);
    }

    private static void save(ServerPlayer owner, List<Entry> values) {
        CompoundTag root = root(owner);
        ListTag list = new ListTag();
        for (Entry value : values) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(UUID_KEY, value.uuid());
            tag.putString(NAME_KEY, value.name());
            tag.putString(TYPE_KEY, value.typeId());
            list.add(tag);
        }
        root.put(LIST_KEY, list);
        CompoundTag persisted = persisted(owner);
        persisted.put(ROOT_KEY, root);
        owner.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag root(ServerPlayer owner) {
        return persisted(owner).getCompound(ROOT_KEY);
    }

    private static CompoundTag persisted(Player owner) {
        CompoundTag persistent = owner.getPersistentData();
        if (!persistent.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistent.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return persistent.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
