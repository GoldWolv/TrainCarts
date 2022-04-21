package com.bergerkiller.bukkit.tc.signactions;

public enum SignActionType {
    NONE(false, true), 
    REDSTONE_CHANGE(true, false), 
    REDSTONE_ON(true, false), 
    REDSTONE_OFF(true, false),  
    MEMBER_ENTER(false, true),  
    MEMBER_MOVE(false, true),  
    MEMBER_LEAVE(false, true),  
    GROUP_ENTER(false, true),  
    GROUP_LEAVE(false, true), 
    MEMBER_UPDATE(false, false),  
    GROUP_UPDATE(false, false);
    
    
    private signActionType(boolean redstone, boolean movement) {
        this.redstone = redstone;
        this.movement = movement;
    }

    /**
     * This sign action type is redstone-related
     * 
     * @return redstone related
     */
    private final boolean redstone;

    /**
     * This sign action type was generated by train or cart movement
     * 
     * @return movement related
     */
    private final boolean movement;
    
    public boolean isRedstone() {
        return redstone;
    }
    
    public boolean isMovement() {
        return movement;
    }
}
