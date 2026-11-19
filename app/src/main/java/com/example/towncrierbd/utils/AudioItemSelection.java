package com.example.towncrierbd.utils;

import java.util.LinkedHashSet;
import java.util.Set;

/** Tracks only choices introduced by the current audio, preserving manual choices. */
public final class AudioItemSelection {
    private final Set<String> categories = new LinkedHashSet<>();
    private final Set<String> products = new LinkedHashSet<>();

    public void apply(Set<String> selectedCategories, Set<String> selectedProducts,
                      Set<String> detectedCategories, Set<String> detectedProducts) {
        clear(selectedCategories, selectedProducts);
        for (String category : detectedCategories) {
            if (selectedCategories.add(category)) categories.add(category);
        }
        for (String product : detectedProducts) {
            if (selectedProducts.add(product)) products.add(product);
        }
    }

    public void keepCategory(String category) { categories.remove(category); }
    public void keepProduct(String product) { products.remove(product); }

    public void clear(Set<String> selectedCategories, Set<String> selectedProducts) {
        selectedCategories.removeAll(categories);
        selectedProducts.removeAll(products);
        categories.clear();
        products.clear();
    }
}
