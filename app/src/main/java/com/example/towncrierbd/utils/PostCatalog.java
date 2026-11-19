package com.example.towncrierbd.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.*;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.*;

/** Shared flat category/item catalog; existing English keys remain compatible. */
public final class PostCatalog {
    private PostCatalog() {}
    public static String text(Context c, String en, String bn) {
        return LanguageManager.isEnglish(c) ? en : bn;
    }
    public static List<CategoryConfig.HawkerCategory> sorted(Context c) {
        List<CategoryConfig.HawkerCategory> result = new ArrayList<>(CategoryConfig.HAWKER_CATEGORIES);
        java.text.Collator order = java.text.Collator.getInstance(
                LanguageManager.isEnglish(c) ? Locale.ENGLISH : new Locale("bn"));
        result.sort((a,b) -> order.compare(label(c,a.name), label(c,b.name)));
        return result;
    }
    public static String label(Context c, String name) {
        return LanguageManager.isEnglish(c) ? name : CategoryConfig.MAIN_BN.getOrDefault(name,name);
    }
    public static List<String> items(String category) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (CategoryConfig.HawkerCategory c : CategoryConfig.HAWKER_CATEGORIES)
            if (c.name.equals(category)) for (CategoryConfig.SubGroup g : c.subGroups) items.addAll(g.items);
        return new ArrayList<>(items);
    }
    public static String itemLabel(Context c, String item) {
        if (!LanguageManager.isEnglish(c)) {
            for (CategoryConfig.HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES)
                for (CategoryConfig.SubGroup g : cat.subGroups) {
                    int i = g.items.indexOf(item);
                    if (i >= 0 && i < g.itemsBn.size()) return g.itemsBn.get(i);
                }
        }
        return item;
    }
    public static void load(Context c, Runnable done) {
        FirebaseDatabase.getInstance().getReference("categories").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot data) {
                for (DataSnapshot child : data.getChildren()) {
                    String name = child.child("name").getValue(String.class);
                    List<String> items = new ArrayList<>();
                    for (DataSnapshot item : child.child("items").getChildren()) {
                        String value = item.getValue(String.class);
                        if (value != null) items.add(value);
                    }
                    if (name == null || CategoryConfig.MAIN.contains(name)) continue;
                    CategoryConfig.HAWKER_CATEGORIES.removeIf(cat -> cat.name.equals(name));
                    CategoryConfig.HAWKER_CATEGORIES.add(new CategoryConfig.HawkerCategory(name,name,"📦",
                        Collections.singletonList(new CategoryConfig.SubGroup("","",items,items))));
                }
                done.run();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.w("PostCatalog", "Could not load custom categories: " + error.getMessage());
                done.run();
            }
        });
    }
    public static void addDialog(Context c, java.util.function.Consumer<String> done) {
        LinearLayout fields = new LinearLayout(c); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(32,16,32,0);
        EditText name = new EditText(c); name.setHint(text(c,"Category name","ক্যাটাগরির নাম")); fields.addView(name);
        EditText items = new EditText(c); items.setHint(text(c,"Items, separated by commas","কমা দিয়ে পণ্যের নাম লিখুন")); fields.addView(items);
        AlertDialog dialog = new AlertDialog.Builder(c).setTitle(text(c,"Add category","ক্যাটাগরি যোগ করুন"))
                .setView(fields).setPositiveButton(text(c,"Add","যোগ করুন"),null)
                .setNegativeButton(AppStrings.get(c).cancel(),null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            String n = name.getText().toString().trim();
            List<String> values = new ArrayList<>();
            for (String value : items.getText().toString().split("[,\\n]")) if (!value.trim().isEmpty()) values.add(value.trim());
            if (n.isEmpty() || values.isEmpty()) { name.setError(text(c,"Enter a category and items","ক্যাটাগরি ও পণ্য লিখুন")); return; }
            for (CategoryConfig.HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) if (cat.name.equalsIgnoreCase(n)) {
                name.setError(text(c,"Category already exists","ক্যাটাগরি আগে থেকেই আছে")); return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            Map<String,Object> data = new HashMap<>(); data.put("name",n); data.put("items",values);
            FirebaseFunctions.getInstance().getHttpsCallable("addCategory").call(data)
                .addOnSuccessListener(result -> load(c,() -> { dialog.dismiss(); done.accept(n); }))
                .addOnFailureListener(e -> { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); Toast.makeText(c,e.getMessage(),Toast.LENGTH_LONG).show(); });
        }));
        dialog.show();
    }
}
