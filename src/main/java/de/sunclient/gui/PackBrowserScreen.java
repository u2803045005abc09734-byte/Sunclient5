package de.sunclient.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.sunclient.SunConfig;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_10799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_4185;
import net.minecraft.class_437;

/** Modrinth-Resource-Packs suchen, mit Vorschaubildern anzeigen und installieren. */
public class PackBrowserScreen extends SunScreen {
	private static final String MC_VERSION = "1.21.11";
	private static final int MAX_RESULTS = 5;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private record Hit(String id, String title, String author, long downloads, String iconUrl) {}
	private record IconInfo(class_2960 texture, int width, int height) {}
	private static final Map<String, IconInfo> ICONS = new ConcurrentHashMap<>();
	private static final Set<String> ICONS_REQUESTED = ConcurrentHashMap.newKeySet();
	private static String lastQuery = "";

	private class_342 searchBox;
	private final List<class_4185> resultButtons = new ArrayList<>();
	private volatile List<Hit> hits = List.of();
	private volatile int version = 0;
	private int shownVersion = -1;
	private volatile String status = "Suchbegriff eingeben und auf Suchen klicken.";

	public PackBrowserScreen(class_437 parent) {
		super("Resource Packs von Modrinth", parent);
	}

	@Override
	protected void method_25426() {
		resultButtons.clear();
		searchBox = new class_342(this.field_22793, this.field_22789 / 2 - 150, 36, 200, 20,
			class_2561.method_43470("Pack suchen"));
		searchBox.method_1852(lastQuery);
		method_37063(searchBox);
		addButton("Suchen", this.field_22789 / 2 + 55, 36, 95, () -> search(searchBox.method_1882()));

		for (int i = 0; i < MAX_RESULTS; i++) {
			final int index = i;
			class_4185 button = addButton("", this.field_22789 / 2 - 150, 66 + i * 22, 300, () -> {
				List<Hit> current = hits;
				if (index < current.size()) download(current.get(index));
			});
			button.field_22764 = false;
			resultButtons.add(button);
		}
		addButton("Zurück", this.field_22789 / 2 - 100, this.field_22790 - 28, 200, this::method_25419);

		shownVersion = -1;
		if (hits.isEmpty()) search(lastQuery);
	}

	private static String formatNumber(long n) {
		if (n >= 1_000_000) return (n / 100_000) / 10.0 + "M";
		if (n >= 1_000) return (n / 100) / 10.0 + "k";
		return String.valueOf(n);
	}

	private static String shortText(String text, int maxChars) {
		if (text == null) return "";
		return text.length() <= maxChars ? text : text.substring(0, Math.max(0, maxChars - 1)) + "…";
	}

	private void search(String query) {
		lastQuery = query == null ? "" : query.trim();
		final String q = lastQuery;
		status = "Suche auf Modrinth …";
		CompletableFuture.runAsync(() -> {
			try {
				String facets = "[[\"project_type:resourcepack\"],[\"versions:" + MC_VERSION + "\"]]";
				String url = "https://api.modrinth.com/v2/search?query="
					+ URLEncoder.encode(q, StandardCharsets.UTF_8)
					+ "&facets=" + URLEncoder.encode(facets, StandardCharsets.UTF_8)
					+ "&limit=" + MAX_RESULTS + (q.isEmpty() ? "&index=downloads" : "");
				JsonObject root = JsonParser.parseString(get(url)).getAsJsonObject();
				JsonArray arr = root.getAsJsonArray("hits");
				List<Hit> result = new ArrayList<>();
				for (JsonElement el : arr) {
					JsonObject o = el.getAsJsonObject();
					String icon = o.has("icon_url") && !o.get("icon_url").isJsonNull()
						? o.get("icon_url").getAsString() : "";
					Hit hit = new Hit(
						o.get("project_id").getAsString(),
						o.get("title").getAsString(),
						o.has("author") ? o.get("author").getAsString() : "Unbekannt",
						o.has("downloads") ? o.get("downloads").getAsLong() : 0L,
						icon);
					result.add(hit);
					loadIcon(hit);
				}
				hits = result;
				version++;
				status = result.isEmpty() ? "Keine Resource Packs für Minecraft " + MC_VERSION + " gefunden."
					: "Pack anklicken, um es herunterzuladen.";
			} catch (Exception e) {
				status = "Suche fehlgeschlagen: " + safeError(e);
			}
		});
	}

	private void loadIcon(Hit hit) {
		if (hit.iconUrl().isBlank() || ICONS.containsKey(hit.id()) || !ICONS_REQUESTED.add(hit.id())) return;
		CompletableFuture.runAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(hit.iconUrl()))
					.header("User-Agent", "SunClient/1.1 (Fabric mod)")
					.timeout(Duration.ofSeconds(8)).GET().build();
				HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
				byte[] bytes = response.body();
				if (response.statusCode() != 200 || bytes.length == 0 || bytes.length > 1_000_000) return;
				class_310 mc = class_310.method_1551();
				mc.execute(() -> {
					try {
						class_1011 image = class_1011.method_4309(new ByteArrayInputStream(bytes));
						int width = image.method_4307();
						int height = image.method_4323();
						if (width <= 0 || height <= 0 || width > 512 || height > 512) {
							image.close();
						return;
						}
						String textureName = "sunclient_" + hit.id().replaceAll("[^a-zA-Z0-9_/-]", "_");
						class_1043 texture = new class_1043(() -> textureName, image);
						class_2960 id = mc.method_1531().method_4617("sunclient_pack", texture);
						ICONS.put(hit.id(), new IconInfo(id, width, height));
					} catch (Exception ignored) {
						// Ein einzelnes ungültiges Projekt-Icon soll die Packliste nicht stören.
					}
				});
			} catch (Exception ignored) {
				// Manche Modrinth-Projekte haben kein Icon oder sind vorübergehend nicht erreichbar.
			}
		});
	}

	private void download(Hit hit) {
		status = "Lade „" + hit.title() + "“ …";
		CompletableFuture.runAsync(() -> {
			try {
				String gv = URLEncoder.encode("[\"" + MC_VERSION + "\"]", StandardCharsets.UTF_8);
				String body = get("https://api.modrinth.com/v2/project/" + hit.id() + "/version?game_versions=" + gv);
				JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
				if (versions.isEmpty()) {
					status = "Keine Version für Minecraft " + MC_VERSION + " gefunden.";
					return;
				}
				JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
				JsonObject chosen = null;
				for (JsonElement el : files) {
					JsonObject f = el.getAsJsonObject();
					if (chosen == null) chosen = f;
					if (f.has("primary") && f.get("primary").getAsBoolean()) { chosen = f; break; }
				}
				if (chosen == null) { status = "Keine Datei für dieses Pack gefunden."; return; }
				String fileUrl = chosen.get("url").getAsString();
				String fileName = chosen.get("filename").getAsString().replaceAll("[\\\\/:*?\"<>|]", "_");
				Path dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
				Files.createDirectories(dir);
				Path target = dir.resolve(fileName);
				Path tmp = dir.resolve(fileName + ".part");
				HttpRequest req = HttpRequest.newBuilder(URI.create(fileUrl))
					.header("User-Agent", "SunClient/1.1 (Fabric mod)")
					.timeout(Duration.ofMinutes(5)).GET().build();
				HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
				if (resp.statusCode() != 200) {
					Files.deleteIfExists(tmp);
					status = "Download fehlgeschlagen (HTTP " + resp.statusCode() + ").";
					return;
				}
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				status = "Installiert: " + fileName + " · Ressourcenpakete neu laden/aktivieren.";
				class_310 mc = class_310.method_1551();
				mc.execute(() -> mc.method_1520().method_14445());
			} catch (Exception e) {
				status = "Download fehlgeschlagen: " + safeError(e);
			}
		});
	}

	private static String safeError(Exception e) {
		String message = e.getMessage();
		if (message == null || message.isBlank()) return e.getClass().getSimpleName();
		return shortText(message.replace('\n', ' '), 90);
	}

	private static String get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.header("User-Agent", "SunClient/1.1 (Fabric mod)")
			.timeout(Duration.ofSeconds(15)).GET().build();
		HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
		return resp.body();
	}

	@Override
	public void method_25394(class_332 g, int mouseX, int mouseY, float delta) {
		if (shownVersion != version) {
			List<Hit> current = hits;
			for (int i = 0; i < resultButtons.size(); i++) {
				class_4185 button = resultButtons.get(i);
				button.field_22764 = i < current.size();
				button.method_25355(class_2561.method_43470(""));
			}
			shownVersion = version;
		}
		super.method_25394(g, mouseX, mouseY, delta);

		int x = this.field_22789 / 2 - 150;
		List<Hit> current = hits;
		for (int i = 0; i < Math.min(current.size(), MAX_RESULTS); i++) {
			Hit hit = current.get(i);
			int y = 66 + i * 22;
			g.method_25294(x + 2, y + 2, x + 21, y + 21, 0xFF2C3340);
			IconInfo icon = ICONS.get(hit.id());
			if (icon != null) {
				g.method_25290(class_10799.field_56883, icon.texture(), x + 2, y + 2,
					0.0f, 0.0f, 18, 18, icon.width(), icon.height());
			} else {
				g.method_25294(x + 5, y + 5, x + 18, y + 18, 0xFF596579);
			}
			String title = shortText(hit.title(), 33);
			String meta = shortText(hit.author(), 24) + "  ·  " + formatNumber(hit.downloads()) + " Downloads";
			g.method_51433(mc().field_1772, title, x + 25, y + 3, 0xFFFFFFFF, false);
			g.method_51433(mc().field_1772, shortText(meta, 40), x + 25, y + 12, 0xFFB8C1D1, false);
		}

		g.method_27534(this.field_22793, class_2561.method_43470(status), this.field_22789 / 2,
			this.field_22790 - 45, 0xFFFFFFFF);
	}
}
