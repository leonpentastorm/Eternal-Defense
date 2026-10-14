package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Shipped textures and sounds carry their pixels and samples only, no embedded notes. */
class AssetFilesTest {
    static final List<Path> ROOTS=List.of(Path.of("src/main/resources"),Path.of("../eternal-defense-gun-guide/src/main/resources"),Path.of("../eternal-defense-displays/src/main/resources"));
    static final Set<String> IMAGE_CHUNKS=Set.of("IHDR","PLTE","tRNS","IDAT","IEND","gAMA","sRGB","pHYs","cHRM","sBIT","bKGD");

    static List<Path> files(String ending) throws Exception{
        var out=new ArrayList<Path>();
        for(var root:ROOTS)if(Files.isDirectory(root))try(Stream<Path> s=Files.walk(root)){s.filter(p->p.toString().endsWith(ending)).forEach(out::add);}
        return out;
    }

    @Test void texturesHoldImageDataOnly() throws Exception{
        var bad=new ArrayList<String>();var all=files(".png");
        for(var p:all){
            var b=ByteBuffer.wrap(Files.readAllBytes(p)).order(ByteOrder.BIG_ENDIAN);b.position(8);
            while(b.remaining()>=12){int n=b.getInt();var type=new byte[4];b.get(type);var t=new String(type,StandardCharsets.ISO_8859_1);
                if(!IMAGE_CHUNKS.contains(t))bad.add(p+": "+t);b.position(b.position()+n+4);}
        }
        assertTrue(all.size()>50,"textures found: "+all.size());
        assertTrue(bad.isEmpty(),String.join("\n",bad));
    }

    @Test void soundsNameOnlyTheirEncoder() throws Exception{
        var bad=new ArrayList<String>();var all=files(".ogg");
        for(var p:all){
            byte[] data;try(var in=Files.newInputStream(p)){data=in.readNBytes(65536);}int k=indexOf(data,"\u0003vorbis".getBytes(StandardCharsets.ISO_8859_1));if(k<0){bad.add(p+": no comment header");continue;}
            var b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);b.position(k+7);
            int vendor=b.getInt();if(vendor<0||vendor>b.remaining()-4){bad.add(p+": unreadable comment header");continue;}b.position(b.position()+vendor);int count=b.getInt();
            for(int i=0;i<count;i++){
                int n=b.remaining()>=4?b.getInt():-1;if(n<0||n>b.remaining()){bad.add(p+": unreadable comment "+i+" of "+count);break;}
                var c=new byte[n];b.get(c);var tag=new String(c,StandardCharsets.UTF_8);if(!tag.toLowerCase(Locale.ROOT).startsWith("encoder="))bad.add(p+": "+tag);
            }
        }
        assertTrue(all.size()>20,"sounds found: "+all.size());
        assertTrue(bad.isEmpty(),String.join("\n",bad));
    }

    static int indexOf(byte[] data,byte[] what){outer:for(int i=0;i+what.length<=Math.min(data.length,65536);i++){for(int j=0;j<what.length;j++)if(data[i+j]!=what[j])continue outer;return i;}return -1;}
}
