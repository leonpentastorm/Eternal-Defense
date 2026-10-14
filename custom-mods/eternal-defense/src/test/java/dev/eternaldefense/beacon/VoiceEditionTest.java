package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** The no-voice edition replaces every spoken line, and only those, with a game sound. */
final class VoiceEditionTest {
    static final Pattern EVENT=Pattern.compile("\"([a-z0-9_.]+)\"\\s*:\\s*\\{\\s*\"sounds\"\\s*:\\s*\\[([^\\]]*)\\]");
    @Test void everySpokenLineHasAGameSound() throws Exception{
        String sounds=Files.readString(Path.of("src/main/resources/assets/eternal_defense/sounds.json"));
        String map=Files.readString(Path.of("src/novoice/voice-replacements.json"));
        var spoken=new TreeSet<String>();var m=EVENT.matcher(sounds);
        while(m.find())if(m.group(2).contains("chatter"))spoken.add(m.group(1));
        assertEquals(15,spoken.size(),"six fire support, two supply, three recall and four seller lines: "+spoken);
        var mapped=new TreeSet<String>();var k=Pattern.compile("\"([a-z0-9_.]+)\"\\s*:\\s*\\{\\s*\"name\"\\s*:\\s*\"(minecraft:[a-z0-9_.]+)\"").matcher(map);
        while(k.find())mapped.add(k.group(1));
        assertEquals(spoken,mapped,"every spoken line, and nothing else, gets a game sound");
        assertFalse(map.contains("eternal_defense:"),"only game sounds");
    }
}
