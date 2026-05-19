package com.example.indicpipeline.models;

import androidx.annotation.Keep;

import com.google.firebase.firestore.IgnoreExtraProperties;

@Keep
@IgnoreExtraProperties
public class PreferredLanguage {
    private String name;
    private String code;

    public PreferredLanguage() {
    }

    public PreferredLanguage(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

