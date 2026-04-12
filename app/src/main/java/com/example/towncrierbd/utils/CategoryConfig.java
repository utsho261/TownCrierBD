package com.example.towncrierbd.utils;

import java.util.*;

public class CategoryConfig {

    public static final String CAT_ALL = "All";

    public static final List<String> MAIN = Arrays.asList(
            "Food & Snacks",
            "Daily Market",
            "Clothing & Accessories",
            "Household Items",
            "Mobile & Electronics",
            "Repair & Services",
            "Utility Services",
            "Miscellaneous",
            "Emergency / Notice",
            "Others"
    );

    // predefined subcategories (except Others)
    public static final Map<String, List<String>> SUB = new HashMap<>();

    static {
        SUB.put("Food & Snacks", Arrays.asList(
                "Fuchka / Chotpoti", "Jhalmuri / Chanachur", "Tea Stall (Cha)", "Coffee Cart",
                "Singara / Samosa", "Ice Cream / Kulfi", "Juice / Sugarcane Juice",
                "Fruits Seller (seasonal)", "Street Fast Food", "Other"
        ));

        SUB.put("Daily Market", Arrays.asList(
                "Vegetable Seller", "Fish Seller", "Egg Seller", "Milk Seller", "Rice / Grain seller", "Other"
        ));

        SUB.put("Clothing & Accessories", Arrays.asList(
                "Readymade clothes", "Winter clothes", "Hijab / Scarf", "Shoes / Sandals",
                "Bags / Belts", "Sunglasses / Watches", "Other"
        ));

        SUB.put("Household Items", Arrays.asList(
                "Plastic items", "Kitchen tools", "Broom / Mop", "Buckets / Containers",
                "Stationery hawker", "Other"
        ));

        SUB.put("Mobile & Electronics", Arrays.asList(
                "Mobile cover / glass", "Charger / earphone", "Mobile repair", "Electronics parts", "Other"
        ));

        SUB.put("Repair & Services", Arrays.asList(
                "Shoe repair", "Umbrella repair", "Key maker", "Knife/Scissor sharpener",
                "Cycle repair", "Plumbing/Electrician", "Other"
        ));

        SUB.put("Utility Services", Arrays.asList(
                "Waste collector", "Water delivery", "Gas cylinder", "Home cleaning helper", "Other"
        ));

        SUB.put("Miscellaneous", Arrays.asList(
                "Book seller", "Toy seller", "Balloon seller", "Flower seller", "Religious items", "Other"
        ));

        SUB.put("Emergency / Notice", Arrays.asList(
                "Missing person", "Lost & found", "Blood needed", "Road blockage", "Local warning", "Other"
        ));
    }

    public static List<String> getSubcategories(String category) {
        if (category == null) return Collections.emptyList();
        List<String> list = SUB.get(category);
        return (list == null) ? Collections.emptyList() : list;
    }
}
