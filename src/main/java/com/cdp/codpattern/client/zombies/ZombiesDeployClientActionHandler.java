package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployDraft;
import com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesDeployMapScreen;
import com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesDeployToolScreen;
import com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesNpcDeployScreen;
import com.phasetranscrystal.fpsmatch.common.packet.zombies.OpenZombiesDeployToolScreenS2CPacket;
import net.minecraft.client.Minecraft;

/** Holds the collapsed editor as well as routing responses to both pages. */
public final class ZombiesDeployClientActionHandler {
    private static ZombiesDeployToolScreen editor;
    private static Object connection;
    private static ZombiesNpcDeployScreen npcEditor;
    private ZombiesDeployClientActionHandler() { }

    public static void setEditor(ZombiesDeployToolScreen screen) { editor = screen; }
    public static void clearEditor(ZombiesDeployToolScreen screen) {
        if (editor == screen) { editor = null; }
    }

    public static void openNpcEditor(ZombiesDeployToolScreen parent, ZombiesDeployDraft draft) {
        npcEditor = new ZombiesNpcDeployScreen(parent, draft);
        Minecraft.getInstance().setScreen(npcEditor);
        npcEditor.load();
    }
    public static void clearNpcEditor(ZombiesNpcDeployScreen screen) { if (npcEditor == screen) npcEditor = null; }

    public static void handle(Object payload) {
        if (!(payload instanceof OpenZombiesDeployToolScreenS2CPacket packet)) {
            throw new IllegalArgumentException("Unexpected Zombies deploy screen payload: " + payload);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (connection != minecraft.getConnection()) {
            editor = null;
            npcEditor = null;
            com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesScenarioScreen.disconnected();
            connection = minecraft.getConnection();
            ZombiesDeployClientState.reset();
        }
        if (packet.responseAction() == com.phasetranscrystal.fpsmatch.common.packet.zombies.ZombiesDeployToolActionC2SPacket.Action.NPC_EDITOR) {
            if (npcEditor != null) npcEditor.accept(packet);
            return;
        }
        if(packet.openScreen()&&com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesScenarioScreen.reopenIfSuspended(packet.snapshot().selectedMap()))return;
        if (packet.openScreen() && npcEditor != null && npcEditor.suspended()
                && npcEditor.mapName().equals(packet.snapshot().selectedMap())) {
            npcEditor.reopen();
            return;
        }
        if (packet.openScreen()) {
            if (editor == null) {
                editor = ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(packet.snapshot().workspaceStage())
                        ? new ZombiesDeployMapScreen(packet) : new ZombiesDeployToolScreen(packet);
                ZombiesDeployClientState.update(packet.snapshot());
                minecraft.setScreen(editor);
            } else { editor.reopen(packet); }
        } else if (editor != null) {
            editor.applyData(packet);
        } else {
            ZombiesDeployClientState.update(packet.snapshot());
        }
    }
}
