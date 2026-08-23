package io.github.schntgaispock.gastronomicon;

import io.github.schntgaispock.gastronomicon.util.StringUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Applica la localizzazione italiana dai file YAML modificabili del plugin. */
final class ItalianTranslations {

    private static final Field ITEM_STACK_TEMPLATE = itemStackTemplateField();
    private static final Field ITEM_STACK_LOCKED = itemStackLockedField();

    private static final List<String> FILES = Arrays.asList(
        "basic_machines.yml", "electric_machines.yml", "food.yml", "raw_ingredients.yml", "tools.yml"
    );

    private ItalianTranslations() {}

    static void apply(Gastronomicon plugin) {
        Map<String, Translation> translations = new HashMap<>();
        for (String file : FILES) load(plugin, file, translations);

        int applied = 0;
        for (Map.Entry<String, Translation> entry : translations.entrySet()) {
            SlimefunItem slimefunItem = SlimefunItem.getById(entry.getKey());
            if (slimefunItem != null && translate(slimefunItem, entry.getValue())) applied++;
        }
        Gastronomicon.info("Traduzione italiana configurabile: " + applied + " oggetti tradotti.");
    }

    private static void load(Gastronomicon plugin, String file, Map<String, Translation> translations) {
        String resourcePath = "translations/it/Gastronomicon/" + file;
        File externalFile = new File(plugin.getDataFolder(), resourcePath);
        if (!externalFile.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        try (InputStream stream = new FileInputStream(externalFile)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                Translation translation = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.matches("  [A-Z0-9_]+:")) {
                        String id = line.substring(2, line.length() - 1);
                        translation = new Translation();
                        translations.put(id, translation);
                    } else if (translation != null && line.startsWith("    name: ")) {
                        translation.name = scalar(line.substring(10));
                    } else if (translation != null && line.startsWith("    - ")) {
                        translation.lore.add(scalar(line.substring(6)));
                    }
                }
            }
        } catch (IOException ex) {
            Gastronomicon.error("Impossibile leggere la traduzione configurabile " + externalFile + ": " + ex.getMessage());
        }
    }

    private static String scalar(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'"))
            return value.substring(1, value.length() - 1).replace("''", "'");
        return value;
    }

    private static boolean translate(SlimefunItem slimefunItem, Translation translation) {
        if (translation.name == null) return false;
        ItemStack item = null;
        try {
            item = (ItemStack) ITEM_STACK_TEMPLATE.get(slimefunItem);
            if (item instanceof SlimefunItemStack) {
                ITEM_STACK_LOCKED.setBoolean(item, false);
            }
            return translate(item, translation);
        } catch (IllegalAccessException ex) {
            return false;
        } finally {
            try {
                if (item instanceof SlimefunItemStack) {
                    ITEM_STACK_LOCKED.setBoolean(item, true);
                }
            } catch (IllegalAccessException ignored) {
                // Il modello deve comunque rimanere utilizzabile anche se il riblocco fallisce.
            }
        }
    }

    private static boolean translate(ItemStack item, Translation translation) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.setDisplayName(StringUtil.formatColors(translation.name));
        if (!translation.lore.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : translation.lore) lore.add(StringUtil.formatColors(line));
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return true;
    }

    private static Field itemStackTemplateField() {
        try {
            Field field = SlimefunItem.class.getDeclaredField("itemStackTemplate");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static Field itemStackLockedField() {
        try {
            Field field = SlimefunItemStack.class.getDeclaredField("locked");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static final class Translation {
        private String name;
        private final List<String> lore = new ArrayList<>();
    }
}
