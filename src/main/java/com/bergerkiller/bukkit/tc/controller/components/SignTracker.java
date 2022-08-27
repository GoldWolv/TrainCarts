package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.collections.ImplicitlySharedList;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.utils.modlist.ModificationTrackedList;

import org.bukkit.block.Block;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Keeps track of the active signs and detector regions from rail information
 */
public abstract class SignTracker {
    protected static final Set<TrackedSign> blockBuffer = new HashSet<TrackedSign>();
    private final Map<Object, TrackedSign> activeSignsByKey = new LinkedHashMap<Object, TrackedSign>();
    private final ImplicitlySharedList<TrackedSign> activeSigns = new ImplicitlySharedList<>();
    protected ImplicitlySharedList<DetectorRegion> detectorRegions = new ImplicitlySharedList<>();
    protected final ToggledState needsUpdate = new ToggledState();
    protected final SignSkipTracker signSkipTracker;

    protected SignTracker(IPropertiesHolder owner) {
        this.signSkipTracker = new SignSkipTracker(owner);
    }

    /**
     * Gets the Owner of this sign tracker. This owner must be part of TrainCarts.
     *
     * @return owner
     */
    public abstract TrainCarts.Provider getOwner();

    /**
     * Gets all actively tracked signs. Can use implicit clone/copy iteration functions to
     * safely iterate the signs without causing concurrent modification exceptions.
     *
     * @return list of signs
     */
    public ImplicitlySharedList<TrackedSign> getActiveTrackedSigns() {
        return activeSigns;
    }

    public Collection<DetectorRegion> getActiveDetectorRegions() {
        return this.detectorRegions;
    }

    public boolean containsSign(TrackedSign sign) {
        if (sign != null) {
            TrackedSign tracked = activeSignsByKey.get(sign.getUniqueKey());
            if (sign == tracked) {
                return true;
            }
            if (tracked != null && sign.isRealSign() && tracked.isRealSign()) {
                return sign.signBlock.equals(tracked.signBlock);
            }
        }
        return false;
    }

    /**
     * Removes an active sign
     *
     * @param sign TrackedSign to remove
     * @return True if the Sign was removed, False if not
     */
    public boolean removeSign(TrackedSign sign) {
        if (sign == null) {
            return false;
        }

        TrackedSign removed = activeSignsByKey.remove(sign.getUniqueKey());
        if (removed != null) {
            activeSigns.remove(removed);
            onSignChange(removed, false);
            return true;
        } else {
            return false;
        }
    }

    public boolean hasSigns() {
        return !this.activeSigns.isEmpty();
    }

    /**
     * Clears all active signs and other Block info, resulting in leave events being fired
     */
    public void clear() {
        if (!activeSignsByKey.isEmpty()) {
            int maxResetIterCtr = 100; // happens more than this, infinite loop suspected
            int expectedCount = activeSignsByKey.size();
            Iterator<TrackedSign> iter = activeSignsByKey.values().iterator();
            while (iter.hasNext()) {
                TrackedSign sign = iter.next();
                iter.remove();
                activeSigns.remove(sign);
                expectedCount--;
                onSignChange(sign, false);

                if (expectedCount != activeSignsByKey.size()) {
                    expectedCount = activeSignsByKey.size();
                    iter = activeSignsByKey.values().iterator();
                    if (--maxResetIterCtr <= 0) {
                        getOwner().getTrainCarts().log(Level.WARNING, "[SignTracker] Number of iteration reset attempts exceeded limit");
                        break;
                    }
                }
            }

            // Just to be sure
            activeSigns.clear();
            activeSignsByKey.clear();
        }
    }

    /**
     * Tells all the Minecarts part of this Minecart Member or Group that something changed
     */
    public void update() {
        needsUpdate.set();
    }

    /**
     * Checks whether the Minecart Member or Group is traveling on top of a given rails block<br>
     * <br>
     * <b>Deprecated:</b> use {@link MinecartMember#getRailTracker()} or
     * {@link MinecartGroup#getRailTracker()} for this instead.
     *
     * @param railsBlock to check
     * @return True if part of the rails, False if not
     */
    @Deprecated
    public abstract boolean isOnRails(Block railsBlock);

    protected abstract void onSignChange(TrackedSign signblock, boolean active);

    protected void updateActiveSigns(Supplier<ModificationTrackedList<TrackedSign>> activeSignListSupplier) {
        int limit = 1000;
        while (!tryUpdateActiveSigns(activeSignListSupplier.get())) {
            // Check for infinite loops, just in case, you know?
            if (--limit == 0) {
                getOwner().getTrainCarts().getLogger().log(Level.SEVERE, "Reached limit of loops updating active signs");
                break;
            }
        }
    }

    // Tries to update the active sign list, returns false if the list was modified during it
    private boolean tryUpdateActiveSigns(final ModificationTrackedList<TrackedSign> list) {
        // Retrieve the list and modification counter
        final int mod_start = list.getModCount();
        final boolean hadSigns = !activeSigns.isEmpty();

        // Perform all operations, for those that could leak into executing code
        // that could modify it, track the mod counter. If changed, restart from
        // the beginning.

        // When there are no signs, only remove previously detected signs
        if (list.isEmpty()) {
            if (hadSigns) {
                Iterator<TrackedSign> iter = activeSignsByKey.values().iterator();
                while (iter.hasNext()) {
                    TrackedSign sign = iter.next();
                    activeSigns.remove(sign);
                    iter.remove();
                    onSignChange(sign, false);

                    // If list changed, restart from the beginning
                    if (list.getModCount() != mod_start) {
                        return false;
                    }
                }
            }

            // All good!
            return true;
        }

        // Go by all detected signs and try to add it to the map
        // If this succeeds, fire an 'enter' event
        // This enter event might modify the list, if so, restart from the beginning
        for (TrackedSign newActiveSign : list) {
            TrackedSign prevActiveSign = activeSignsByKey.put(newActiveSign.getUniqueKey(), newActiveSign);
            if (prevActiveSign != newActiveSign) {
                if (prevActiveSign != null) {
                    activeSigns.remove(prevActiveSign);

                    // If old and new signs have identical text, don't fire any events
                    if (prevActiveSign.hasIdenticalText(newActiveSign)) {
                        activeSigns.add(newActiveSign);
                        continue;
                    }

                    onSignChange(prevActiveSign, false);
                }
                activeSigns.add(newActiveSign);
                onSignChange(newActiveSign, true);

                // If list changed, restart from the beginning
                if (list.getModCount() != mod_start) {
                    return false;
                }
            }
        }

        // Check if any previously detected signs are no longer in the active sign list
        if (hadSigns) {
            // Calculate all the signs that are now missing
            blockBuffer.clear();
            blockBuffer.addAll(activeSigns);
            blockBuffer.removeAll(list);

            // Remove all the signs that are now inactive
            // This leave event might cause the list to change, if so, restart from the beginning
            for (TrackedSign old : blockBuffer) {
                TrackedSign removed = activeSignsByKey.remove(old.getUniqueKey());
                if (removed != null) {
                    activeSigns.remove(removed);
                }
                if (removed == old) {
                    onSignChange(old, false);
                }

                // If list changed, restart from the beginning
                if (list.getModCount() != mod_start) {
                    return false;
                }
            }
        }

        // Done!
        return true;
    }

    /*
     * Below methods should not be used, because they only work for real sign blocks.
     * Fake signs added by add-ons are not supported at all, and are ignored.
     */

    /**
     * @deprecated Only works with real sign blocks
     */
    @Deprecated
    public Collection<Block> getActiveSigns() {
        return getActiveTrackedSigns().stream()
                .filter(TrackedSign::isRealSign)
                .map(s -> s.signBlock)
                .collect(StreamUtil.toUnmodifiableList());
    }

    /**
     * @deprecated Only works with real sign blocks
     */
    @Deprecated
    public boolean containsSign(Block signblock) {
        TrackedSign sign = activeSignsByKey.get(signblock);
        return sign != null && sign.isRealSign();
    }

    /**
     * Removes an active sign
     *
     * @param signBlock to remove
     * @return True if the Block was removed, False if not
     * @deprecated Only works with real sign blocks
     */
    @Deprecated
    public boolean removeSign(Block signBlock) {
        TrackedSign removed = activeSignsByKey.remove(signBlock);
        if (removed != null && removed.isRealSign()) {
            activeSigns.remove(removed);
            onSignChange(removed, false);
            return true;
        } else {
            activeSignsByKey.put(signBlock, removed);
            return false;
        }
    }
}
