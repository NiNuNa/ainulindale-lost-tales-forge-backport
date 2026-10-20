package com.ninuna.losttales.progression;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.progression.skill.SkillFileParser;
import com.ninuna.losttales.progression.skill.SkillFileValidator;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Where the progression's data files lie and how the skills are read
 * (PROG-01): bundled under {@code assets/losttales/skills/}, a server's own
 * in {@code config/losttales/skills/}. The races' starting skills are in
 * the race files ({@code CharacterRaceFiles}, PROG-05).
 */
public final class ProgressionFiles {
    public static final String SKILLS_INDEX = "skills/index.json";
    public static final String SKILLS_FOLDER = "losttales/skills";

    private static File configDirectory;

    private ProgressionFiles() {}

    /** Forge's config directory; set once as the mod loads. */
    public static synchronized void configure(File modConfigurationDirectory) {
        configDirectory = modConfigurationDirectory;
    }

    public static synchronized File folder(String name) {
        return configDirectory == null ? null : new File(configDirectory, name);
    }

    public static final DataFiles.Kind<SkillDefinition> SKILLS =
            new DataFiles.Kind<SkillDefinition>() {
                @Override
                public SkillDefinition parse(Reader reader, String file, List<String> said) {
                    return SkillFileParser.parse(reader, file, said);
                }

                @Override
                public String idOf(SkillDefinition skill) {
                    return skill.getId();
                }

                @Override
                public List<String> warnings(SkillDefinition skill) {
                    return SkillFileValidator.warningsOf(skill);
                }
            };

    /** The bundled files as the server reads them: from the mod's own classpath. */
    public static final DataFiles.Source CLASSPATH = new DataFiles.Source() {
        @Override
        public Reader open(String path) {
            InputStream stream = ProgressionFiles.class.getClassLoader()
                    .getResourceAsStream("assets/" + LostTalesMetaData.MOD_ID + "/" + path);
            return stream == null ? null
                    : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    };
}
