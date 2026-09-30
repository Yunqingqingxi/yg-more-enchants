package com.yunxigames;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-enchants 冒烟测试：mod 描述文件合法、配置能从零生成并写回、改动能落盘再读回。
 */
@Tag("smoke")
class EnchantsSmokeTest {

	private static final Gson GSON = new Gson();

	@TempDir
	Path configDir;

	@BeforeEach
	void injectConfigDir() {
		YgConfig.configDirOverride = configDir;
	}

	@AfterEach
	void resetConfigDir() {
		YgConfig.configDirOverride = null;
	}

	@Test
	void fabricModJsonIsValidWithCorrectModId() throws Exception {
		try (var in = getClass().getResourceAsStream("/fabric.mod.json")) {
			assertNotNull(in, "fabric.mod.json 必须在 jar 资源里");
			JsonObject json = GSON.fromJson(
					new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			assertEquals("yg_enchants", json.get("id").getAsString());
			assertEquals(1, json.get("schemaVersion").getAsInt());
			assertNotNull(json.get("entrypoints").getAsJsonObject().get("main"));
		}
	}

	@Test
	void loadCreatesDefaultConfigFileOnDisk() {
		EnchantsConfig cfg = EnchantsConfig.load();
		assertTrue(Files.isRegularFile(configDir.resolve(EnchantsConfig.FILE_NAME)),
				"load() 后配置文件必须已写回磁盘");
		assertTrue(cfg.enableMagnet, "磁石默认开");
		assertTrue(cfg.enableCursedEnchantments, "诅咒附魔默认开");
		assertTrue(cfg.enableLibrarianRefresh, "图书管理员重做默认开");
	}

	@Test
	void modifiedValuesSurviveSaveLoadRoundtrip() {
		EnchantsConfig cfg = EnchantsConfig.load();
		cfg.magnetRadius = 12.5D;
		cfg.enableGreed = false;
		cfg.save();

		EnchantsConfig reloaded = EnchantsConfig.load();
		assertEquals(12.5D, reloaded.magnetRadius);
		assertFalse(reloaded.enableGreed, "写盘的 false 必须原样读回");
	}
}
