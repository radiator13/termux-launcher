package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiShellPlannerTest {

    @Test
    public void updatePackagesPlan_requiresConfirmationAndAvoidsUnattendedFlags() throws Exception {
        JSONObject plan = new TaiShellPlanner().plan("update packages", false);

        assertEquals(true, plan.getBoolean("ok"));
        assertEquals(false, plan.getBoolean("autoExecute"));
        assertEquals("confirmation_required", plan.getJSONObject("safety").getString("level"));
        JSONArray commands = plan.getJSONArray("commands");
        assertEquals(3, commands.length());
        assertTrue(commands.getJSONObject(1).getBoolean("confirmationRequired"));
        assertFalse(commands.getJSONObject(1).getString("command").contains("-y"));
        assertFalse(commands.getJSONObject(2).getString("command").contains("--noconfirm"));
    }

    @Test
    public void neovimConfigPlan_isReadOnly() throws Exception {
        JSONObject plan = new TaiShellPlanner().plan("where is the config files for neovim?", false);

        assertEquals("safe", plan.getJSONObject("safety").getString("level"));
        JSONObject command = plan.getJSONArray("commands").getJSONObject(0);
        assertFalse(command.getBoolean("destructive"));
        assertFalse(command.getBoolean("confirmationRequired"));
        assertTrue(command.getString("command").contains("$HOME/.config/nvim"));
    }

    @Test
    public void fishConfigPlan_searchesCommonAndMistypedNames() throws Exception {
        JSONObject plan = new TaiShellPlanner().plan("find me the file fish.config", false);
        String command = plan.getJSONArray("commands").getJSONObject(0).getString("command");

        assertTrue(command.contains("config.fish"));
        assertTrue(command.contains("fish.config"));
        assertFalse(command.contains("-delete"));
    }

    @Test
    public void safetyPolicy_detectsDestructiveCommands() {
        assertTrue(TaiSafetyPolicy.isDestructiveCommand("rm -rf \"$HOME/tmp\""));
        assertTrue(TaiSafetyPolicy.isDestructiveCommand("find . -type f -delete"));
        assertTrue(TaiSafetyPolicy.requiresConfirmation("pkg update && pkg upgrade"));
    }
}
