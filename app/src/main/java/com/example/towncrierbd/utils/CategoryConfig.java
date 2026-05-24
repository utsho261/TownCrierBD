package com.example.towncrierbd.utils;

import java.util.*;

public class CategoryConfig {

    public static final String CAT_ALL = "All";

    // ── Feed filter chips
    public static final List<String> MAIN = Arrays.asList(
            "Vegetable Seller",
            "Fish Seller",
            "Meat Seller",
            "Fruit Seller",
            "Bread / Bakery Van",
            "Scrap Buyer",
            "Others"
    );

    // ── Hawker categories
    public static final List<HawkerCategory> HAWKER_CATEGORIES = new ArrayList<>();

    static {
        // 1. Vegetable Seller
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Vegetable Seller", "🥦",
                Arrays.asList(
                        new SubGroup("Leafy Vegetables",     Arrays.asList("Spinach (Palong)", "Lal shak", "Pui shak", "Kolmi shak")),
                        new SubGroup("Common Vegetables",    Arrays.asList("Potato", "Onion", "Tomato", "Brinjal", "Cucumber")),
                        new SubGroup("Seasonal Vegetables",  Arrays.asList("Cauliflower", "Cabbage", "Pumpkin", "Bottle gourd", "Radish")),
                        new SubGroup("Spicy & Cooking Items",Arrays.asList("Green chili", "Garlic", "Ginger", "Lemon", "Coriander leaf")),
                        new SubGroup("Beans & Pods",         Arrays.asList("Bean", "Peas", "Okra", "Yardlong bean"))
                )
        ));

        // 2. Fish Seller
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Fish Seller", "🐟",
                Arrays.asList(
                        new SubGroup("Fresh Water Fish", Arrays.asList("Rui", "Katla", "Mrigal", "Pangash", "Tilapia")),
                        new SubGroup("Premium Fish",     Arrays.asList("Ilish", "Chingri (Shrimp)", "Boal", "Koi")),
                        new SubGroup("Small Fish",       Arrays.asList("Puti", "Mola", "Kachki", "Tengra")),
                        new SubGroup("Sea Fish",         Arrays.asList("Tuna", "Rupchanda", "Loitta")),
                        new SubGroup("Processed Fish",   Arrays.asList("Dry fish (Shutki)", "Cut fish pieces", "Cleaned fish"))
                )
        ));

        // 3. Meat Seller
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Meat Seller", "🍖",
                Arrays.asList(
                        new SubGroup("Beef Items",      Arrays.asList("Beef meat", "Beef bone", "Beef liver", "Beef fat")),
                        new SubGroup("Chicken Items",   Arrays.asList("Broiler chicken", "Sonali chicken", "Deshi chicken")),
                        new SubGroup("Mutton Items",    Arrays.asList("Goat meat", "Goat liver", "Goat leg")),
                        new SubGroup("Duck & Bird",     Arrays.asList("Duck", "Pigeon", "Quail bird")),
                        new SubGroup("Ready-to-Cook",   Arrays.asList("Minced meat", "Marinated meat", "BBQ chicken"))
                )
        ));

        // 4. Scrap Buyer
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Scrap Buyer", "♻️",
                Arrays.asList(
                        new SubGroup("Metal Scrap",       Arrays.asList("Iron", "Steel", "Aluminum", "Copper")),
                        new SubGroup("Plastic Items",     Arrays.asList("Plastic bottles", "Plastic containers", "Broken buckets")),
                        new SubGroup("Electronics Scrap", Arrays.asList("Old mobile", "Broken TV", "Computer parts", "Fan/motor")),
                        new SubGroup("Paper & Books",     Arrays.asList("Newspaper", "Old books", "Cartons")),
                        new SubGroup("Home Scrap",        Arrays.asList("Old furniture", "Broken chair", "Old utensils"))
                )
        ));

        // 5. Fruit Seller
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Fruit Seller", "🍉",
                Arrays.asList(
                        new SubGroup("Local Fruits",    Arrays.asList("Mango", "Banana", "Guava", "Jackfruit")),
                        new SubGroup("Seasonal Fruits", Arrays.asList("Watermelon", "Litchi", "Orange", "Pineapple")),
                        new SubGroup("Imported Fruits", Arrays.asList("Apple", "Grapes", "Dragon fruit", "Kiwi")),
                        new SubGroup("Cut Fruits",      Arrays.asList("Fruit mix", "Ready fruit box")),
                        new SubGroup("Juice Fruits",    Arrays.asList("Lemon", "Malta", "Sugarcane", "Papaya"))
                )
        ));

        // 6. Bread / Bakery Van
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Bread / Bakery Van", "🍞",
                Arrays.asList(
                        new SubGroup("Bread Items",      Arrays.asList("White bread", "Brown bread", "Bun", "Roti")),
                        new SubGroup("Cakes",            Arrays.asList("Birthday cake", "Cupcake", "Pastry", "Cream roll")),
                        new SubGroup("Biscuits & Snacks",Arrays.asList("Biscuit", "Toast", "Chanachur", "Chips")),
                        new SubGroup("Sweet Bakery",     Arrays.asList("Donut", "Muffin", "Cookies")),
                        new SubGroup("Dairy & Related",  Arrays.asList("Butter", "Cheese", "Jam", "Eggs"))
                )
        ));

        // 7. Others (special — custom input)
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Others", "📦",
                new ArrayList<>() // empty — custom input দেবে
        ));
    }

    // ── Flat subcategory list for a category (Feed spinner) ───────────────
    public static final Map<String, List<String>> SUB = new HashMap<>();

    static {
        SUB.put("Vegetable Seller", Arrays.asList(
                "Leafy Vegetables", "Common Vegetables", "Seasonal Vegetables",
                "Spicy & Cooking Items", "Beans & Pods", "Other"
        ));
        SUB.put("Fish Seller", Arrays.asList(
                "Fresh Water Fish", "Premium Fish", "Small Fish", "Sea Fish", "Processed Fish", "Other"
        ));
        SUB.put("Meat Seller", Arrays.asList(
                "Beef Items", "Chicken Items", "Mutton Items", "Duck & Bird", "Ready-to-Cook", "Other"
        ));
        SUB.put("Scrap Buyer", Arrays.asList(
                "Metal Scrap", "Plastic Items", "Electronics Scrap", "Paper & Books", "Home Scrap", "Other"
        ));
        SUB.put("Fruit Seller", Arrays.asList(
                "Local Fruits", "Seasonal Fruits", "Imported Fruits", "Cut Fruits", "Juice Fruits", "Other"
        ));
        SUB.put("Bread / Bakery Van", Arrays.asList(
                "Bread Items", "Cakes", "Biscuits & Snacks", "Sweet Bakery", "Dairy & Related", "Other"
        ));

    }

    public static List<String> getSubcategories(String category) {
        if (category == null) return Collections.emptyList();
        List<String> list = SUB.get(category);
        return (list == null) ? Collections.emptyList() : list;
    }

    // ── Nested model classes ───────────────────────────────────────────────

    public static class HawkerCategory {
        public final String name;
        public final String emoji;
        public final List<SubGroup> subGroups;

        public HawkerCategory(String name, String emoji, List<SubGroup> subGroups) {
            this.name      = name;
            this.emoji     = emoji;
            this.subGroups = subGroups;
        }

        /** All sub-items flattened */
        public List<String> allItems() {
            List<String> all = new ArrayList<>();
            for (SubGroup g : subGroups) all.addAll(g.items);
            return all;
        }
    }

    public static class SubGroup {
        public final String       groupName;
        public final List<String> items;

        public SubGroup(String groupName, List<String> items) {
            this.groupName = groupName;
            this.items     = items;
        }
    }
}