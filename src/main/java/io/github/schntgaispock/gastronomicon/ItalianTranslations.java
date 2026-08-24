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
public final class ItalianTranslations {

    private static final Field ITEM_STACK_TEMPLATE = field(SlimefunItem.class, "itemStackTemplate");
    private static final Field RECIPE_OUTPUT = field(SlimefunItem.class, "recipeOutput");
    private static final Field ITEM_STACK_LOCKED = field(SlimefunItemStack.class, "locked");

    private static final String[] FILES = {
        "categories-v2.yml", "basic_machines-v2.yml", "electric_machines-v2.yml",
        "food-v2.yml", "raw_ingredients-v2.yml", "tools-v2.yml"
    };

    private static final Map<String, Translation> TRANSLATIONS = new HashMap<>();

    /*
     * Il catalogo incorporato viene caricato durante l'inizializzazione della classe,
     * prima che GastroStacks possa costruire anche un solo oggetto. Non dipende da
     * JavaPlugin.onEnable() e quindi non esiste più una finestra in cui nascono copie inglesi.
     */
    static {
        loadEmbeddedCatalog();
    }

    private ItalianTranslations() {}

    /** Viene richiamato dai costruttori: ogni nuovo stack nasce già localizzato. */
    public static <T extends SlimefunItemStack> T translateNewItem(T stack) {
        Translation translation = TRANSLATIONS.get(stack.getItemId());
        if (translation != null) translateUnlocked(stack, translation, false);
        return stack;
    }

    static void load(Gastronomicon plugin) {
        // Il catalogo incorporato è la base certa; i file esterni sovrascrivono solo le voci scelte.
        for (String file : FILES) loadExternalFile(plugin, file);
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

    private static void loadEmbeddedCatalog() {
        ClassLoader loader = ItalianTranslations.class.getClassLoader();
        for (String fileName : FILES) {
            String resourcePath = "translations/it/Gastronomicon/" + fileName;
            try (InputStream stream = loader.getResourceAsStream(resourcePath)) {
                if (stream == null) continue;
                YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
                loadSection(bundled.getConfigurationSection("translations"), fileName, false);
            } catch (Exception ex) {
                throw new ExceptionInInitializerError("Catalogo italiano non leggibile: " + fileName);
            }
        }
    }

    private static void loadExternalFile(Gastronomicon plugin, String fileName) {
        String resourcePath = "translations/it/Gastronomicon/" + fileName;
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) plugin.saveResource(resourcePath, false);

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
