package it.emmeweb.tvlauncher;

import org.json.JSONArray;
import org.json.JSONObject;

/** Meteo Caivano (NA) da Open-Meteo, senza chiave. */
public class Weather {
    public static final double LAT = 40.957, LON = 14.306;

    public static class Info {
        public String icon, desc; public double temp, tmax, tmin; public int code;
    }

    public static Info fetch() {
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + LAT + "&longitude=" + LON
                    + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=Europe%2FRome&forecast_days=1";
            byte[] b = Net.get(url, null, null);
            if (b == null) return null;
            JSONObject j = new JSONObject(new String(b, "UTF-8"));
            JSONObject cur = j.getJSONObject("current");
            JSONObject daily = j.getJSONObject("daily");
            Info i = new Info();
            i.temp = cur.getDouble("temperature_2m");
            i.code = cur.getInt("weather_code");
            boolean day = cur.optInt("is_day", 1) == 1;
            JSONArray mx = daily.getJSONArray("temperature_2m_max"), mn = daily.getJSONArray("temperature_2m_min");
            i.tmax = mx.getDouble(0); i.tmin = mn.getDouble(0);
            int dayCode = daily.getJSONArray("weather_code").getInt(0);
            i.icon = icon(i.code, day);
            i.desc = describe(dayCode);
            return i;
        } catch (Exception e) { return null; }
    }

    static String icon(int c, boolean day) {
        if (c == 0) return day ? "☀️" : "🌙";
        if (c <= 2) return day ? "🌤️" : "☁️";
        if (c == 3) return "☁️";
        if (c <= 48) return "🌫️";
        if (c <= 57) return "🌦️";
        if (c <= 67) return "🌧️";
        if (c <= 77) return "🌨️";
        if (c <= 82) return "🌧️";
        if (c <= 86) return "🌨️";
        return "⛈️";
    }

    static String describe(int c) {
        if (c == 0) return "Sereno";
        if (c == 1) return "Poco nuvoloso";
        if (c == 2) return "Parzialmente nuvoloso";
        if (c == 3) return "Coperto";
        if (c <= 48) return "Nebbia";
        if (c <= 57) return "Pioviggine";
        if (c <= 67) return "Pioggia";
        if (c <= 77) return "Neve";
        if (c <= 82) return "Rovesci";
        if (c <= 86) return "Rovesci di neve";
        return "Temporale";
    }
}
