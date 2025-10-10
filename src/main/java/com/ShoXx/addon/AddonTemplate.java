package com.ShoXx.addon;

import com.ShoXx.addon.modules.AutoPlatformMiner;
import com.ShoXx.addon.modules.WallBuilder;
import com.ShoXx.addon.commands.CommandExample;
import com.ShoXx.addon.hud.ImageDisplayHud;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("TNT");
    public static final HudGroup HUD_GROUP = new HudGroup("TNT");

    @Override
    public void onInitialize() {
        LOG.info("Initializing TNT Addon");

        // Modules
        Modules.get().add(new WallBuilder());
        Modules.get().add(new AutoPlatformMiner());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(ImageDisplayHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.ShoXx.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
