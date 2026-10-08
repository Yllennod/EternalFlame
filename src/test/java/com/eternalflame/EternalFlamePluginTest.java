package com.eternalflame;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class EternalFlamePluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(EternalFlamePlugin.class);
        RuneLite.main(args);
    }
}
