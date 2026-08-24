package io.github.schntgaispock.gastronomicon;

import io.github.schntgaispock.gastronomicon.core.slimefun.GastroStacks;
import io.github.schntgaispock.gastronomicon.util.StringUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Carica e applica la localizzazione italiana configurabile prima e dopo la registrazione. */
final class ItalianTranslations {

    private static final Field ITEM_STACK_TEMPLATE = field(SlimefunItem.class, "itemStackTemplate");
    private static final Field RECIPE_OUTPUT = field(SlimefunItem.class, "recipeOutput");
    private static final Field ITEM_STACK_LOCKED = field(SlimefunItemStack.class, "locked");

    private static final String[] FILES = {
        "categories.yml", "basic_machines.yml", "electric_machines.yml",
        "food.yml", "raw_ingredients.yml", "tools.yml"
    };

    private static final Map<String, Translation> TRANSLATIONS = new HashMap<>();

    private ItalianTranslations() {}

    static void load(Gastronomicon plugin) {
        TRANSLATIONS.clear();
        for (String file : FILES) loadFile(plugin, file);
        Gastronomicon.info("Caricate " + TRANSLATIONS.size() + " traduzioni italiane configurabili.");
    }

    /** Traduce gli oggetti statici prima che ricette e output ne creino copie. */
    static void applyTemplates() {
        int applied = 0;
        for (Field field : GastroStacks.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !ItemStack.class.isAssignableFrom(field.getType())) continue;
            try {
                ItemStack stack = (ItemStack) field.get(null);
                String key = stack instanceof SlimefunItemStack sfStack
                    ? sfStack.getItemId()
                    : "CATEGORY_" + field.getName().replace("GUIDE_ITEM_", "");
                Translation translation = TRANSLATIONS.get(key);
                if (translation != null && translateUnlocked(stack, translation, false)) applied++;
            } catch (IllegalAccessException ex) {
                Gastronomicon.warn("Impossibile tradurre il modello " + field.getName());
            }
        }
        Gastronomicon.info("Traduzione preventiva applicata a " + applied + " modelli e categorie.");
    }

    /** Completa gli oggetti dinamici e le versioni Perfect, poi aggiorna gli output registrati. */
    static void applyRegistered() {
        int applied = 0;
        int missing = 0;
        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (item.getAddon() != Gastronomicon.getInstance()) continue;
            Translation translation = TRANSLATIONS.get(item.getId());
            if (translation == null) {
                missing++;
                Gastronomicon.warn("Traduzione italiana mancante per l'oggetto " + item.getId());
            } else if (translateRegistered(item, translation)) {
                applied++;
            }
        }
        Gastronomicon.info("Traduzione finale applicata a " + applied + " oggetti; mancanti: " + missing + '.');
    }

    private static void loadFile(Gastronomicon plugin, String fileName) {
        String resourcePath = "translations/it/Gastronomicon/" + fileName;
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) plugin.saveResource(resourcePath, false);

        // Prima carica le chiavi incluse nel JAR, poi sovrascrive con il file esterno modificabile.
        // In questo modo gli aggiornamenti aggiungono le nuove voci senza cancellare le modifiche locali.
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream != null) {
                YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
                loadSection(bundled.getConfigurationSection("translations"), fileName, false);
            }
        } catch (Exception ex) {
            Gastronomicon.warn("Impossibile leggere le traduzioni incluse in " + fileName);
        }

        YamlConfiguration external = YamlConfiguration.loadConfiguration(file);
        loadSection(external.getConfigurationSection("translations"), fileName, true);
    }

    private static void loadSection(ConfigurationSection section, String fileName, boolean external) {
        if (section == null) {
            Gastronomicon.warn("Sezione translations mancante in " + fileName);
            return;
        }

        for (String id : section.getKeys(false)) {
            String name = section.getString(id + ".name");
            if (name == null || name.isBlank()) {
                Gastronomicon.warn("Nome mancante per " + id + " in " + fileName);
                continue;
            }
            Translation translation = new Translation(name, section.getStringList(id + ".lore"));
            if (external) TRANSLATIONS.put(id, translation);
            else TRANSLATIONS.putIfAbsent(id, translation);
        }
    }

    private static boolean translateRegistered(SlimefunItem slimefunItem, Translation translation) {
        try {
            ItemStack template = (ItemStack) ITEM_STACK_TEMPLATE.get(slimefunItem);
            boolean translated = translateUnlocked(template, translation, true);
            ItemStack output = (ItemStack) RECIPE_OUTPUT.get(slimefunItem);
            if (output != null && output != template) translated |= translateUnlocked(output, translation, true);
            return translated;
        } catch (IllegalAccessException ex) {
            Gastronomicon.warn("Impossibile tradurre l'oggetto registrato " + slimefunItem.getId());
            return false;
        }
    }

    private static boolean translateUnlocked(ItemStack stack, Translation translation, boolean relock) {
        if (stack == null) return false;
        boolean slimefunStack = stack instanceof SlimefunItemStack;
        try {
            if (slimefunStack) ITEM_STACK_LOCKED.setBoolean(stack, false);
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return false;
            meta.setDisplayName(StringUtil.formatColors(translation.name));
            if (!translation.lore.isEmpty()) {
                List<String> lore = new ArrayList<>(translation.lore.size());
                for (String line : translation.lore) lore.add(StringUtil.formatColors(line));
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
            return true;
        } catch (IllegalAccessException ex) {
            return false;
        } finally {
            if (slimefunStack && relock) {
                try {
                    ITEM_STACK_LOCKED.setBoolean(stack, true);
                } catch (IllegalAccessException ignored) {
                    // Slimefun gestirà comunque il proprio ciclo di vita.
                }
            }
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static final class Translation {
        private final String name;
        private final List<String> lore;

        private Translation(String name, List<String> lore) {
            this.name = name;
            this.lore = List.copyOf(lore);
        }
    }
}
