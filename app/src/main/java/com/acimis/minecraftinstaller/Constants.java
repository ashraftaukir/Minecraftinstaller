package com.acimis.minecraftinstaller;

public class Constants {
    // Minecraft folder paths
    public static final String MINECRAFT_BASE_PATH = "Android/data/com.mojang.minecraftpe/files/games/com.mojang/";
    public static final String WORLDS_PATH = MINECRAFT_BASE_PATH + "minecraftWorlds/";
    public static final String RESOURCE_PACKS_PATH = MINECRAFT_BASE_PATH + "resource_packs/";
    public static final String BEHAVIOR_PACKS_PATH = MINECRAFT_BASE_PATH + "behavior_packs/";

    // File types
    public static final String MCWORLD_EXTENSION = ".mcworld";
    public static final String MCPACK_EXTENSION = ".mcpack";

    // Request codes
    public static final int REQUEST_CODE_PICK_FILE = 1001;
    public static final int REQUEST_CODE_SAF_PERMISSION = 1002;

    // Intent actions
    public static final String ACTION_INSTALL_COMPLETE = "com.minecraftinstaller.INSTALL_COMPLETE";
    public static final String ACTION_INSTALL_ERROR = "com.minecraftinstaller.INSTALL_ERROR";

    // Supported file types for validation
    public static final String[] SUPPORTED_EXTENSIONS = {MCWORLD_EXTENSION, MCPACK_EXTENSION};

    // Animation durations
    public static final int ANIMATION_DURATION_SHORT = 500;
    public static final int ANIMATION_DURATION_LONG = 1000;
}