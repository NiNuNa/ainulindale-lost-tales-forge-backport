package com.ninuna.losttales.config;

import cpw.mods.fml.relauncher.FMLInjectionData;
import java.io.File;
import java.lang.reflect.Field;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A config file holds only what each option is set to, so a screen read
 * from it alone would restore whatever was saved last. Dressed in the
 * options' definitions it restores what the mod ships, and what each
 * option is set to stays as it was.
 */
public final class LostTalesConfigDefinitionsTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aScreenReadFromTheFileRestoresTheShippedDefault() throws Exception {
        Configuration definitions = new Configuration();
        definitions.getInt("radius", "chat", 32, 8, 128, "How far a line carries.");
        definitions.getString("align", "client", "LEFT", "Which edge.",
                new String[] {"LEFT", "RIGHT"});
        definitions.getBoolean("pings", "client", true, "Whether to ping.");
        definitions.getStringList("names", "client", new String[] {"a", "b"},
                "Some names.");

        initializeForgeHome(this.folder.getRoot());
        File file = this.folder.newFile("options.cfg");
        Configuration saved = new Configuration(file);
        saved.get("chat", "radius", 64);
        saved.get("client", "align", "RIGHT");
        saved.get("client", "pings", false);
        saved.get("client", "names", new String[] {"c"});
        saved.save();
        Configuration screen = new Configuration(file);
        Property radius = screen.getCategory("chat").get("radius");
        // Read alone, the file takes what was saved for the default.
        assertEquals("64", radius.getDefault());

        LostTalesConfigDefinitions.apply(definitions, screen);

        assertEquals(64, radius.getInt());
        assertEquals("32", radius.getDefault());
        assertEquals("8", radius.getMinValue());
        assertEquals("128", radius.getMaxValue());
        assertTrue(radius.comment.contains("default: 32"));
        Property align = screen.getCategory("client").get("align");
        assertEquals("RIGHT", align.getString());
        assertEquals("LEFT", align.getDefault());
        assertArrayEquals(new String[] {"LEFT", "RIGHT"}, align.getValidValues());
        Property pings = screen.getCategory("client").get("pings");
        assertFalse(pings.getBoolean(true));
        assertEquals("true", pings.getDefault());
        Property names = screen.getCategory("client").get("names");
        assertArrayEquals(new String[] {"c"}, names.getStringList());
        assertArrayEquals(new String[] {"a", "b"}, names.getDefaults());
    }

    @Test
    public void whatTheDefinitionsDoNotHoldIsLeftAsItIs() {
        Configuration definitions = new Configuration();
        definitions.get("client", "offset", 25.0D, "Where it stands.", 0.0D, 100.0D);
        Configuration target = new Configuration();
        // An option of another type — a file from before it changed — and
        // one the definitions do not know.
        Property earlier = target.get("client", "offset", 30);
        Property unknown = target.get("client", "extra", "kept", "Its own comment.");
        LostTalesConfigDefinitions.apply(definitions, target);
        assertEquals(Property.Type.INTEGER, earlier.getType());
        assertEquals("30", earlier.getDefault());
        assertEquals("kept", unknown.getDefault());
        assertEquals("Its own comment.", unknown.comment);
        LostTalesConfigDefinitions.apply(null, target);
        LostTalesConfigDefinitions.apply(definitions, null);
    }

    @Test
    public void theModsOptionsAreDefinedAsTheyShip() {
        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        ConfigCategory client = definitions.getCategory(LostTalesConfig.CATEGORY_CLIENT);
        Property alignment = client.get("chatFeedAlignment");
        assertEquals("CENTRE", alignment.getDefault());
        assertTrue(alignment.comment.length() > 0);
        Property history = client.get("chatHistoryLines");
        assertEquals("1000", history.getDefault());
        assertEquals("100", history.getMinValue());
        assertEquals("5000", history.getMaxValue());
        assertTrue(history.comment.contains("default: 1000"));
        // Put right as they are read, the charge tiers keep their comments.
        Property tierTwo = definitions.getCategory(
                LostTalesConfig.CATEGORY_RANGED_COMBAT).get("chargeTierTwoTicks");
        assertTrue(tierTwo.comment.contains("range"));
    }

    @Test
    public void aSavedChoiceStillRestoresTheOneTheModShips() throws Exception {
        initializeForgeHome(this.folder.getRoot());
        File file = this.folder.newFile("client.cfg");
        Configuration saved = new Configuration(file);
        saved.get(LostTalesConfig.CATEGORY_CLIENT, "chatFeedAlignment", "RIGHT");
        saved.save();
        Configuration screen = new Configuration(file);
        Property alignment = screen.getCategory(LostTalesConfig.CATEGORY_CLIENT)
                .get("chatFeedAlignment");
        assertEquals("RIGHT", alignment.getDefault());

        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        LostTalesConfigDefinitions.apply(definitions, screen);

        assertEquals("RIGHT", alignment.getString());
        assertEquals("CENTRE", alignment.getDefault());
    }

    private static void initializeForgeHome(File directory) throws Exception {
        Field minecraftHome = FMLInjectionData.class
                .getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, directory);
    }
}
