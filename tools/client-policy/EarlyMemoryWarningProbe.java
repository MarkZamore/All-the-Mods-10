import com.cupboard.config.CupboardConfig;
import com.memorysettings.Memory;
import com.memorysettings.config.CommonConfiguration;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/// Checks the native early recommendation branches without opening a dialog.
public class EarlyMemoryWarningProbe {
    public static void main(String[] args) throws Exception {
        Path fixture = Path.of("").toAbsolutePath();
        Path config = fixture.resolve("config/memorysettings.json");
        require(!Files.exists(fixture.resolve("mods")) && !Files.exists(config), "Use a fresh component test directory");
        require(ClassLoader.getSystemResource("org/lwjgl/util/tinyfd/TinyFileDialogs.class") == null,
            "Omit only LWJGL TinyFileDialogs so a dialog attempt can be observed without opening a window");

        Files.createDirectories(config.getParent());
        Files.copy(Path.of(args[0]), config.resolveSibling("author-memorysettings.json"));
        Files.copy(Path.of(args[1]), config);
        FMLPaths.loadAbsolutePaths(fixture);
        var active = CommonConfiguration.config.getCommonConfig();
        var author = new CupboardConfig<>("author-memorysettings", new CommonConfiguration()).getCommonConfig();
        byte[] loaded = Files.readAllBytes(config);
        require(author.minimumClient == 6000 && active.minimumClient == 0, "ATM10 client minimum differs from the expected change");
        require(!active.disableWarnings, "The early architecture check would also be disabled");
        author.minimumClient = 0;
        require(author.serialize().equals(active.serialize()), "Unrelated native config values changed");

        String architecture = System.getProperty("sun.arch.data.model");
        int heap = Memory.heapSetting;
        int total = Memory.systemMemory;
        require("64".equals(architecture), "Use the 64-bit portable Java runtime");
        Memory.heapSetting = 5120;
        Memory.systemMemory = 16384;
        try {
            active.minimumClient = 6000;
            requireDialogAttempt();
            active.minimumClient = 0;
            Memory.doEarlyWarnings();
            System.setProperty("sun.arch.data.model", "32");
            requireDialogAttempt();
        } finally {
            System.setProperty("sun.arch.data.model", architecture);
            active.minimumClient = 0;
            Memory.heapSetting = heap;
            Memory.systemMemory = total;
        }
        require(Arrays.equals(loaded, Files.readAllBytes(config)), "Warning logic rewrote the loaded config");
        System.out.println("PASS native author 6000 MiB warns at 5120 MiB, prepared 0 MiB does not, architecture check still warns");
    }

    private static void requireDialogAttempt() {
        try {
            Memory.doEarlyWarnings();
        } catch (NoClassDefFoundError error) {
            if ("org/lwjgl/util/tinyfd/TinyFileDialogs".equals(error.getMessage())) return;
            throw error;
        }
        throw new AssertionError("Native dialog call was not reached");
    }

    private static void require(boolean observed, String message) {
        if (!observed) throw new AssertionError(message);
    }
}
