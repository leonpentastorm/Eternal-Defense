package dev.createarsenal.beacon;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.*;

/**
 * Tiny line format shared by the exchange offers and the support cost overrides:
 * <pre>
 * # comment
 * minecraft:diamond = 40          one diamond (or one unit)
 * minecraft:arrow x16 = 20        sixteen arrows
 * </pre>
 * Pure Java so it can be unit tested without Minecraft.
 */
final class TextTable {
    private TextTable(){}
    record Row(String id,int count,int value){}
    record Result(List<Row> rows,List<String> problems){}
    private static final Pattern LINE=Pattern.compile("^([a-z0-9_.-]+:[a-z0-9_./-]+)\\s*(?:x\\s*(\\d+))?\\s*=\\s*(\\d+)\\s*$");

    static Result parse(List<String> lines,Predicate<String> exists,int maxCount,int maxValue){
        var rows=new ArrayList<Row>();var problems=new ArrayList<String>();
        for(int i=0;i<lines.size();i++){
            String line=lines.get(i).trim();
            if(line.isEmpty()||line.startsWith("#")||line.startsWith("//"))continue;
            Matcher m=LINE.matcher(line.toLowerCase(Locale.ROOT));
            if(!m.matches()){problems.add("line "+(i+1)+": expected 'item_id [x count] = cost' but found '"+line+"'");continue;}
            String id=m.group(1);int count=m.group(2)==null?1:Integer.parseInt(m.group(2));long value=Long.parseLong(m.group(3));
            if(!exists.test(id)){problems.add("line "+(i+1)+": unknown item '"+id+"'");continue;}
            if(count<1||count>maxCount){problems.add("line "+(i+1)+": count must be 1-"+maxCount);continue;}
            if(value<1||value>maxValue){problems.add("line "+(i+1)+": cost must be 1-"+maxValue);continue;}
            rows.add(new Row(id,count,(int)value));
        }
        return new Result(rows,problems);
    }
}
