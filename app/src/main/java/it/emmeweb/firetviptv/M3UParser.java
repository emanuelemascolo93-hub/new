package it.emmeweb.firetviptv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser M3U/M3U8 extended: #EXTINF con attributi tvg-*, group-title e #EXTVLCOPT. */
public class M3UParser {

    private static final Pattern ATTR = Pattern.compile("([A-Za-z0-9\\-_]+)=\"([^\"]*)\"");

    public static List<Channel> parse(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader, 64 * 1024);
        List<Channel> out = new ArrayList<>();
        String line;
        String name = null, group = null, logo = null, ua = null, ref = null;
        boolean pending = false;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF")) {
                pending = true;
                name = null; group = null; logo = null; ua = null; ref = null;
                int comma = line.lastIndexOf(',');
                String attrs = comma >= 0 ? line.substring(0, comma) : line;
                name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                Matcher m = ATTR.matcher(attrs);
                while (m.find()) {
                    String k = m.group(1).toLowerCase();
                    String v = m.group(2);
                    switch (k) {
                        case "group-title": group = v; break;
                        case "tvg-logo": logo = v; break;
                        case "tvg-name": if (name.isEmpty()) name = v; break;
                        case "http-user-agent": ua = v; break;
                    }
                }
            } else if (line.startsWith("#EXTVLCOPT:")) {
                String opt = line.substring("#EXTVLCOPT:".length());
                int eq = opt.indexOf('=');
                if (eq > 0) {
                    String k = opt.substring(0, eq).trim().toLowerCase();
                    String v = opt.substring(eq + 1).trim();
                    if (k.equals("http-user-agent")) ua = v;
                    else if (k.equals("http-referrer")) ref = v;
                }
            } else if (line.startsWith("#EXTGRP:")) {
                group = line.substring("#EXTGRP:".length()).trim();
            } else if (line.startsWith("#")) {
                // altre direttive ignorate
            } else if (pending) {
                String url = line;
                // supporto sintassi url|User-Agent=...&Referer=...
                int pipe = url.indexOf('|');
                if (pipe > 0) {
                    String extra = url.substring(pipe + 1);
                    url = url.substring(0, pipe);
                    for (String kv : extra.split("&")) {
                        int eq = kv.indexOf('=');
                        if (eq <= 0) continue;
                        String k = kv.substring(0, eq).trim().toLowerCase();
                        String v = kv.substring(eq + 1).trim();
                        if (k.equals("user-agent")) ua = v;
                        else if (k.equals("referer") || k.equals("referrer")) ref = v;
                    }
                }
                if (name == null || name.isEmpty()) name = url;
                if (group == null || group.isEmpty()) group = "Senza gruppo";
                out.add(new Channel(name, url, group, logo, ua, ref));
                pending = false;
            }
        }
        return out;
    }
}
