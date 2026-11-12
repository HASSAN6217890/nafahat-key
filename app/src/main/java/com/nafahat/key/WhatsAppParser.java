package com.nafahat.key;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java parser for exported WhatsApp .txt transcripts, in-memory only. */
public final class WhatsAppParser {
    private static final Pattern ANDROID=Pattern.compile(
        "^\\d{1,2}[/.]\\d{1,2}[/.]\\d{2,4},?\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:[AaPp]\\.?[Mm]\\.?)?\\s*[-–]\\s*([^:]{1,90}):\\s*(.*)$");
    private static final Pattern IOS=Pattern.compile(
        "^\\[\\d{1,2}[/.]\\d{1,2}[/.]\\d{2,4},?\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:[AaPp]\\.?[Mm]\\.?)?\\]\\s*([^:]{1,90}):\\s*(.*)$");
    private WhatsAppParser(){}
    public static Map<String,StringBuilder> bySender(String exported){
        Map<String,StringBuilder> result=new LinkedHashMap<>();
        String author=null;
        for(String line:exported.split("\\r?\\n",-1)){
            Matcher m=ANDROID.matcher(line);
            if(!m.matches())m=IOS.matcher(line);
            if(m.matches()){
                author=m.group(1).trim();
                String message=m.group(2);
                if(!author.isEmpty()&&!message.startsWith("<Media omitted>")){
                    result.computeIfAbsent(author,k->new StringBuilder()).append(message).append('\n');
                }
            }else if(author!=null&&!line.startsWith("Messages to this chat")&&!line.startsWith("تم تشفير الرسائل")){
                result.computeIfAbsent(author,k->new StringBuilder()).append(line).append('\n');
            }
        }
        return result;
    }
}
