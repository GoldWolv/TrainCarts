package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualArmorStandItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;

/**
 * Seated entity that is using flying skull with a player skin on an
 * armor stand. Some types of mobs are also supported as skulls.
 */
class SeatedEntityHead extends SeatedEntity {
    private VirtualArmorStandItemEntity skull;

    public SeatedEntityHead(CartAttachmentSeat seat) {
        super(seat);
    }

    public int getSkullEntityId() {
        return skull.getEntityId();
    }

    @Override
    public Vector getThirdPersonCameraOffset() {
        return new Vector(0.0, 1.2, 0.0);
    }

    @Override
    public void makeVisible(Player viewer) {
        if (isPlayer()) {
            // Despawn/hide original entity
            if (entity != viewer) {
                hideRealPlayer(viewer);
            }

            if (skull == null) {
                ItemStack skullItem = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skullItem.getItemMeta();
                meta.setOwningPlayer((Player) entity);
                skullItem.setItemMeta(meta);

                skull = new VirtualArmorStandItemEntity(seat.getManager());
                skull.setSyncMode(seat.isMinecartInterpolation() ? SyncMode.NORMAL_MINECART_FIX : SyncMode.NORMAL);
                skull.setItem(ItemTransformType.SMALL_HEAD, skullItem);
                skull.setRelativeOffset(0.0, -0.72, 0.0);
                skull.updatePosition(seat.getTransform());
                skull.syncPosition(true);
            }

            // Spawn and send head equipment
            skull.spawn(viewer, seat.calcMotion());
        } else if (!isEmpty()) {
            // Default behavior for non-player entities is just to mount them
            PlayerUtil.getVehicleMountController(viewer).mount(this.spawnVehicleMount(viewer), this.entity.getEntityId());
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        if (isPlayer()) {
            if (skull != null) {
                skull.destroy(viewer);

                // Force a reset when the passenger changes
                if (!skull.hasViewers()) {
                    skull = null;
                }
            }

            // Show real player again
            if (viewer != entity) {
                showRealPlayer(viewer);
            }
        } else if (!isEmpty()) {
            // Unmount for generic entities
            PlayerUtil.getVehicleMountController(viewer).unmount(this.parentMountId, this.entity.getEntityId());
            despawnVehicleMount(viewer);
        }
    }

    @Override
    public void updateMode(boolean silent) {
        // Compute new first-person state of whether the player sees himself from third person using a fake camera
        FirstPersonViewMode new_firstPersonMode = this.seat.firstPerson.getMode();
        boolean new_smoothCoasters;

        // Whether a fake entity is used to represent this seated entity
        if (this.isPlayer()) {
            new_smoothCoasters = TrainCarts.plugin.getSmoothCoastersAPI().isEnabled((Player) this.getEntity());
        } else {
            new_smoothCoasters = false;
        }

        // No other mode is supported here
        if (new_firstPersonMode == FirstPersonViewMode.DYNAMIC) {
            new_firstPersonMode = FirstPersonViewMode.THIRD_P;
        }

        // If unchanged, do nothing
        if (new_smoothCoasters == seat.firstPerson.useSmoothCoasters() &&
            new_firstPersonMode == seat.firstPerson.getLiveMode())
        {
            return;
        }

        // Sometimes a full reset of the FPV controller is required. Avoid when silent.
        if (!silent &&
            seat.firstPerson.doesViewModeChangeRequireReset(new_firstPersonMode) &&
            seat.getViewersSynced().contains(this.getEntity()))
        {
            // Hide, change, and make visible again, just for the first-player-view player
            Player viewer = (Player) this.getEntity();
            seat.makeHidden(viewer);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
            seat.makeVisibleImpl(viewer);
            return;
        }

        // Silent update
        seat.firstPerson.setLiveMode(new_firstPersonMode);
        seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        if (skull != null) {
            skull.updatePosition(transform);
        }
        updateVehicleMountPosition(transform);
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (skull != null) {
            skull.syncPosition(absolute);
        }
        syncVehicleMountPosition(absolute);
    }
}
