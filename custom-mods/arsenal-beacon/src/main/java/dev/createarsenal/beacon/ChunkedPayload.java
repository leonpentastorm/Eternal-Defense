package dev.createarsenal.beacon;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Bounded transport; no catalogue entry is applied until the complete transfer is verified. */
public final class ChunkedPayload {
    public static final int CHUNK_BYTES=256*1024, MAX_BYTES=32*1024*1024;
    public record Part(UUID transfer,int rawBytes,int packedBytes,int index,byte[] bytes){}
    public static List<Part> split(byte[] raw){
        if(raw.length<1||raw.length>MAX_BYTES)throw new IllegalArgumentException("Gun catalogue exceeds transfer limit");
        try{
            var out=new ByteArrayOutputStream();try(var zip=new GZIPOutputStream(out)){zip.write(raw);}
            byte[] packed=out.toByteArray();if(packed.length>MAX_BYTES)throw new IllegalArgumentException("Compressed catalogue exceeds transfer limit");
            var id=UUID.randomUUID();var parts=new ArrayList<Part>();
            for(int offset=0;offset<packed.length;offset+=CHUNK_BYTES)parts.add(new Part(id,raw.length,packed.length,offset/CHUNK_BYTES,Arrays.copyOfRange(packed,offset,Math.min(packed.length,offset+CHUNK_BYTES))));
            return List.copyOf(parts);
        }catch(IOException e){throw new IllegalArgumentException("Cannot compress gun catalogue",e);}
    }
    public static void validate(Part p){
        if(p.transfer()==null||p.rawBytes()<1||p.rawBytes()>MAX_BYTES||p.packedBytes()<1||p.packedBytes()>MAX_BYTES||p.index()<0||p.index()>=(p.packedBytes()+CHUNK_BYTES-1)/CHUNK_BYTES)
            throw new IllegalArgumentException("Invalid gun catalogue chunk header");
        int expected=Math.min(CHUNK_BYTES,p.packedBytes()-p.index()*CHUNK_BYTES);
        if(p.bytes()==null||p.bytes().length!=expected)throw new IllegalArgumentException("Invalid gun catalogue chunk length");
    }
    public static final class Assembler {
        private UUID transfer;private byte[] packed;private int rawBytes,next,offset;
        public byte[] accept(Part p){
            validate(p);
            if(p.index()==0){transfer=p.transfer();packed=new byte[p.packedBytes()];rawBytes=p.rawBytes();next=offset=0;}
            if(!p.transfer().equals(transfer)||p.index()!=next||p.packedBytes()!=packed.length||p.rawBytes()!=rawBytes)
                throw new IllegalArgumentException("Out-of-order or mismatched gun catalogue transfer");
            System.arraycopy(p.bytes(),0,packed,offset,p.bytes().length);offset+=p.bytes().length;next++;
            if(offset<packed.length)return null;
            try(var zip=new GZIPInputStream(new ByteArrayInputStream(packed))){
                // A bounded read also rejects a decompression bomb or false raw-length header.
                byte[] raw=zip.readNBytes(rawBytes+1);
                if(raw.length!=rawBytes||zip.read()!=-1)throw new IllegalArgumentException("Gun catalogue length mismatch");
                return raw;
            }catch(IOException e){throw new IllegalArgumentException("Corrupt gun catalogue transfer",e);}
            finally{clear();}
        }
        public void clear(){transfer=null;packed=null;rawBytes=next=offset=0;}
    }
}
