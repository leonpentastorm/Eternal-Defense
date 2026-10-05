package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ChunkedPayloadTest {
    static byte[] noise(){var raw=new byte[3*1024*1024+17];new Random(123).nextBytes(raw);return raw;}
    @Test void largeIncompressiblePayloadReassemblesExactly(){
        byte[] raw=noise();var parts=ChunkedPayload.split(raw);assertTrue(parts.size()>4);var assembler=new ChunkedPayload.Assembler();byte[] result=null;
        for(int i=0;i<parts.size();i++){assertTrue(parts.get(i).bytes().length<=ChunkedPayload.CHUNK_BYTES);result=assembler.accept(parts.get(i));if(i<parts.size()-1)assertNull(result);}
        assertArrayEquals(raw,result);
    }
    @Test void smallAndReplacementTransfersDoNotKeepOldState(){
        var assembler=new ChunkedPayload.Assembler();assembler.accept(ChunkedPayload.split(noise()).get(0));byte[] raw="replacement catalogue".getBytes();assertArrayEquals(raw,assembler.accept(ChunkedPayload.split(raw).get(0)));
        assertArrayEquals(raw,assembler.accept(ChunkedPayload.split(raw).get(0)));
    }
    @Test void missingDuplicateAndForeignPiecesAreRejected(){
        var parts=ChunkedPayload.split(noise());var assembler=new ChunkedPayload.Assembler();assertThrows(IllegalArgumentException.class,()->assembler.accept(parts.get(1)));assembler.accept(parts.get(0));assertThrows(IllegalArgumentException.class,()->assembler.accept(parts.get(2)));
        var other=ChunkedPayload.split(noise());assertThrows(IllegalArgumentException.class,()->assembler.accept(other.get(1)));
        assembler.accept(parts.get(1));assertThrows(IllegalArgumentException.class,()->assembler.accept(parts.get(1)));
    }
    @Test void invalidLengthsAndCorruptionAreRejected(){
        var p=ChunkedPayload.split("valid".getBytes()).get(0);
        assertThrows(IllegalArgumentException.class,()->ChunkedPayload.validate(new ChunkedPayload.Part(p.transfer(),Integer.MAX_VALUE,p.packedBytes(),0,p.bytes())));
        assertThrows(IllegalArgumentException.class,()->ChunkedPayload.validate(new ChunkedPayload.Part(p.transfer(),p.rawBytes(),p.packedBytes(),0,new byte[1])));
        assertThrows(IllegalArgumentException.class,()->new ChunkedPayload.Assembler().accept(new ChunkedPayload.Part(p.transfer(),1,p.packedBytes(),0,p.bytes())));
        byte[] bad=p.bytes().clone();bad[bad.length-6]^=1;assertThrows(IllegalArgumentException.class,()->new ChunkedPayload.Assembler().accept(new ChunkedPayload.Part(p.transfer(),p.rawBytes(),p.packedBytes(),0,bad)));
    }
}
