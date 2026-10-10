package de.sunclient.gui;

import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

/** Hauptmenü, standardmäßig über Rechts-Shift geöffnet. */
public class SunMenuScreen extends SunScreen {
	public SunMenuScreen(class_437 parent) {
		super("☀ Sun Client", parent);
	}

	@Override
	protected void method_25426() {
		int cx = this.field_22789 / 2 - 120;
		int y = this.field_22790 / 2 - 62;
		int step = 26;
		addButton("HUD bearbeiten", cx, y, 240, () -> mc().method_1507(new HudEditorScreen(this)));
		addButton("HUD-Module und Rüstung", cx, y + step, 240, () -> mc().method_1507(new ModulesScreen(this)));
		addButton("Funktionen und Nametag", cx, y + 2 * step, 240,
			() -> mc().method_1507(new FeaturesScreen(this)));
		addButton("Modrinth Resource Packs", cx, y + 3 * step, 240,
			() -> mc().method_1507(new PackBrowserScreen(this)));
		addButton("Schließen", cx, y + 4 * step + 8, 240, this::method_25419);
	}

	@Override
	public void method_25394(class_332 g, int mouseX, int mouseY, float delta) {
		super.method_25394(g, mouseX, mouseY, delta);
		g.method_27534(this.field_22793,
			class_2561.method_43470("Zoom: C  ·  Sprint: R  ·  Freelook: Alt"),
			this.field_22789 / 2, this.field_22790 - 12, 0xFFB8C1D1);
	}
}
