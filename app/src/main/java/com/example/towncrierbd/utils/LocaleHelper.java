package com.example.towncrierbd.utils;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {
    public static Context setLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration cfg = new Configuration(context.getResources().getConfiguration());
        cfg.setLocale(locale);
        return context.createConfigurationContext(cfg);
    }
}
