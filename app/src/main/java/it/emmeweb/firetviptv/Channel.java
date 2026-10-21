package it.emmeweb.firetviptv;

import java.io.Serializable;

public class Channel implements Serializable {
    public final String name;
    public final String url;
    public final String group;
    public final String logo;
    public final String userAgent;
    public final String referer;

    public Channel(String name, String url, String group, String logo, String userAgent, String referer) {
        this.name = name;
        this.url = url;
        this.group = group;
        this.logo = logo;
        this.userAgent = userAgent;
        this.referer = referer;
    }
}
