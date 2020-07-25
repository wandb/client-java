package com.wandb.client;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JSONItem {

    static public List<JSONItem> fromJson(JSONObject json) {
        List<JSONItem> items = new ArrayList<>();
        for (String key: json.keySet()) {
            items.add(new JSONItem(key, json.get(key)));
        }
        return items;
    }

    private String key;
    private String value;
    private Set<String> nested;

    private JSONItem(String key, Object value) {
        this.nested = new HashSet<>();
        this.value = value.toString();
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
