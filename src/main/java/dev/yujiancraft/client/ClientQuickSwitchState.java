package dev.yujiancraft.client;

import dev.yujiancraft.combat.combo.ComboStyle;
import dev.yujiancraft.combat.technique.TechniqueMode;
import dev.yujiancraft.formation.FormationMode;
import dev.yujiancraft.network.ModNetwork;

/** Server-confirmed selections displayed by the direct-access quick switch. */
public final class ClientQuickSwitchState {
    private static FormationMode formation;
    private static TechniqueMode technique;
    private static ComboStyle comboStyle = ComboStyle.FLOWING_BALANCE;

    private ClientQuickSwitchState() { }

    public static void accept(ModNetwork.QuickSwitchStatePacket packet) {
        formation = packet.formation() >= 0 && packet.formation() < FormationMode.values().length
                ? FormationMode.values()[packet.formation()] : null;
        technique = packet.technique() >= 0 && packet.technique() < TechniqueMode.values().length
                ? TechniqueMode.values()[packet.technique()] : TechniqueMode.PIERCE;
        comboStyle = ComboStyle.byId(packet.comboStyleId());
    }

    public static FormationMode formation() {
        return formation;
    }

    public static TechniqueMode technique() {
        return technique == null ? ClientSettingsState.get().techniqueMode() : technique;
    }

    public static ComboStyle comboStyle() {
        ComboStyle live = ClientComboState.isLocalActive() ? ClientComboState.localStyle() : null;
        return live == null ? comboStyle : live;
    }

    public static void request() {
        send(ModNetwork.QuickSwitchActionPacket.REQUEST_STATE, 0);
    }

    public static void selectFormation(FormationMode selected) {
        formation = selected;
        send(ModNetwork.QuickSwitchActionPacket.SELECT_FORMATION, selected.ordinal());
    }

    public static void selectTechnique(TechniqueMode selected) {
        technique = selected;
        send(ModNetwork.QuickSwitchActionPacket.SELECT_TECHNIQUE, selected.ordinal());
    }

    public static void toggleCombo() {
        send(ModNetwork.QuickSwitchActionPacket.TOGGLE_COMBO, 0);
    }

    public static void selectComboStyle(ComboStyle selected) {
        comboStyle = selected;
        send(ModNetwork.QuickSwitchActionPacket.SELECT_COMBO_STYLE, selected.ordinal());
    }

    private static void send(int action, int value) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.QuickSwitchActionPacket(action, value));
    }

    public static void clear() {
        formation = null;
        technique = null;
        comboStyle = ComboStyle.FLOWING_BALANCE;
    }
}
