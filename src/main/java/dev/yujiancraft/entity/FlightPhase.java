package dev.yujiancraft.entity;

public enum FlightPhase {
    DOCKED,
    CLEAR_PLAYER,
    RISE,
    HOMING,
    FOLLOW_THROUGH,
    RETURN_RALLY,
    RETURN_APPROACH,
    DOCKING,
    RELENTLESS_ARC,
    MANUAL_GUIDANCE,
    RIDE_SUPPORT,
    SWEEP,
    SWORD_ARRAY,
    TOOL_APPROACH,
    TOOL_WORK,
    FISHING_APPROACH,
    FISHING_WAIT,
    /** Temporarily driven by the player-level Yujian combo coordinator. */
    COMBO
}
