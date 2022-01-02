package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;

/**
 * Synchronizes the seat to the player sitting in the seat
 */
public abstract class FirstPersonView {
    protected final CartAttachmentSeat seat;
    private FirstPersonViewMode _liveMode = FirstPersonViewMode.DEFAULT;
    private FirstPersonViewMode _mode = FirstPersonViewMode.DYNAMIC;
    private FirstPersonViewLockMode _lock = FirstPersonViewLockMode.MOVE;
    private boolean _useSmoothCoasters = false;
    protected ObjectPosition _eyePosition = new ObjectPosition();

    public FirstPersonView(CartAttachmentSeat seat) {
        this.seat = seat;
    }

    public ObjectPosition getEyePosition() {
        return this._eyePosition;
    }

    public boolean isFakeCameraUsed() {
        if (!this._eyePosition.isDefault()) {
            return true;
        }
        if (this._liveMode.isVirtual()) {
            return true;
        }

        // The elytra has a 'weird' mount position to make it work in third-person
        // This causes the default camera, mounted for the same entity, to no longer work
        // To fix this, make use of the virtual camera mount
        if (this._liveMode == FirstPersonViewMode.DEFAULT && this.seat.seated instanceof SeatedEntityElytra) {
            return true;
        }

        return false;
    }

    /**
     * Gets the base transform for use with eye/view logic. If an eye position is set, returns the
     * seat transform transformed with this eye. Otherwise, returns the seat transform. It is up
     * to the caller to perform further appropriate adjustments.
     *
     * @return First-person base transform
     */
    protected Matrix4x4 getBaseTransform() {
        if (this._eyePosition.isDefault()) {
            return seat.getTransform();
        } else {
            return Matrix4x4.multiply(seat.getTransform(), this._eyePosition.transform);
        }
    }

    /**
     * Called when a Player entered the seat. Should make the first-person view mode
     * active for this viewer.
     *
     * @param viewer Player that entered the seat
     */
    public abstract void makeVisible(Player viewer);

    /**
     * Called when a Player was inside the seat, but now exited it, disabling the
     * first-person view.
     *
     * @param viewer Player viewer that left the seat
     */
    public abstract void makeHidden(Player viewer);

    /**
     * Called every tick to perform any logic required
     */
    public abstract void onTick();

    /**
     * Called every tick to update positions of structures used to move the
     * first-person view camera, if any
     *
     * @param absolute Whether this is an absolute position update
     */
    public abstract void onMove(boolean absolute);

    public boolean useSmoothCoasters() {
        return _useSmoothCoasters;
    }

    public void setUseSmoothCoasters(boolean use) {
        this._useSmoothCoasters = use;
    }

    /**
     * Gets the view mode used in first-person currently. This mode alters how the player perceives
     * himself in the seat. This one differs from the {@link #getMode()} if DYNAMIC is used.
     * 
     * @return live mode
     */
    public FirstPersonViewMode getLiveMode() {
        return this._liveMode;
    }

    /**
     * Sets the view mode currently used in first-person. See: {@link #getLiveMode()}
     * 
     * @param liveMode
     */
    public void setLiveMode(FirstPersonViewMode liveMode) {
        this._liveMode = liveMode;
    }

    /**
     * Gets the view mode that should be used. This is set using seat configuration, and
     * alters what mode is picked for {@link #getLiveMode()}. If set to DYNAMIC, the DYNAMIC
     * view mode conditions are checked to switch to the appropriate mode.
     * 
     * @return mode
     */
    public FirstPersonViewMode getMode() {
        return this._mode;
    }

    /**
     * Sets the view mode that should be used. Is configuration, the live view mode
     * is updated elsewhere. See: {@link #getMode()}.
     * 
     * @param mode
     */
    public void setMode(FirstPersonViewMode mode) {
        this._mode = mode;
    }

    /**
     * Gets the way the first person view camera is locked
     *
     * @return lock mode
     */
    public FirstPersonViewLockMode getLockMode() {
        return this._lock;
    }

    /**
     * Sets the view lock mode to use
     *
     * @param lock
     */
    public void setLockMode(FirstPersonViewLockMode lock) {
        this._lock = lock;
    }
}