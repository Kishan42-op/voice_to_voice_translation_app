package com.example.indicpipeline.language;

import com.example.indicpipeline.LangConfig;
import com.example.indicpipeline.models.PreferredLanguage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LanguageCatalog {
    private static final List<LangConfig> SUPPORTED_LANGUAGES;

    static {
        List<LangConfig> languages = new ArrayList<>();
        languages.add(new LangConfig("Hindi", "hi", "hin_Deva", "hin"));
        languages.add(new LangConfig("Gujarati", "gu", "guj_Gujr", "guj"));
        languages.add(new LangConfig("Marathi", "mr", "mar_Deva", "mar"));
        languages.add(new LangConfig("Bengali", "bn", "ben_Beng", "ben"));
        languages.add(new LangConfig("Tamil", "ta", "tam_Taml", "tam"));
        languages.add(new LangConfig("Telugu", "te", "tel_Telu", "tel"));
        languages.add(new LangConfig("Kannada", "kn", "kan_Knda", "kan"));
        languages.add(new LangConfig("Malayalam", "ml", "mal_Mlym", "mal"));
        languages.add(new LangConfig("Odia", "or", "ory_Orya", "ory"));
        languages.add(new LangConfig("Punjabi", "pa", "pan_Guru", "pan"));
        languages.add(new LangConfig("Assamese", "as", "asm_Beng", "asm"));
        SUPPORTED_LANGUAGES = Collections.unmodifiableList(languages);
    }

    private LanguageCatalog() {
    }

    public static List<LangConfig> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public static LangConfig findByCode(String code) {
        if (code == null) {
            return null;
        }
        for (LangConfig language : SUPPORTED_LANGUAGES) {
            if (code.equalsIgnoreCase(language.asrCode) || code.equalsIgnoreCase(language.transCode)) {
                return language;
            }
        }
        return null;
    }

    public static LangConfig findByName(String name) {
        if (name == null) {
            return null;
        }
        for (LangConfig language : SUPPORTED_LANGUAGES) {
            if (name.equalsIgnoreCase(language.name)) {
                return language;
            }
        }
        return null;
    }

    public static boolean isSupported(String name, String code) {
        LangConfig language = findByName(name);
        return language != null && code != null && (
                code.equalsIgnoreCase(language.asrCode) || code.equalsIgnoreCase(language.transCode)
        );
    }

    public static boolean isSupported(PreferredLanguage preferredLanguage) {
        if (preferredLanguage == null) {
            return false;
        }
        return isSupported(preferredLanguage.getName(), preferredLanguage.getCode());
    }

    public static PreferredLanguage toPreferredLanguage(LangConfig language) {
        if (language == null) {
            return null;
        }
        return new PreferredLanguage(language.name, language.asrCode);
    }
}


