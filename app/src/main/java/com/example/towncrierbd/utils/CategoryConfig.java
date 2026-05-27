package com.example.towncrierbd.utils;

import android.content.Context;
import java.util.*;

/**
 * CategoryConfig — Bilingual version.
 *
 * All category names, subcategory groups, and item names are available
 * in both English and Bangla. Call CategoryConfig.localized(context) to
 * get the version matching the user's current language setting.
 *
 * The internal data (stored in Firebase, used for filtering) always uses
 * the ENGLISH keys — translation is only for display.
 */
public class CategoryConfig {

    public static final String CAT_ALL    = "All";
    public static final String CAT_ALL_BN = "সব";

    // ── English master list (used as DB keys) ─────────────────────────────
    public static final List<String> MAIN = Arrays.asList(
            "Vegetable Seller",
            "Fish Seller",
            "Meat Seller",
            "Fruit Seller",
            "Bread / Bakery Van",
            "Scrap Buyer",
            "Others"
    );

    // ── Bangla display names for MAIN ─────────────────────────────────────
    public static final Map<String, String> MAIN_BN = new LinkedHashMap<>();
    static {
        MAIN_BN.put("Vegetable Seller",  "সবজি বিক্রেতা");
        MAIN_BN.put("Fish Seller",       "মাছ বিক্রেতা");
        MAIN_BN.put("Meat Seller",       "মাংস বিক্রেতা");
        MAIN_BN.put("Fruit Seller",      "ফল বিক্রেতা");
        MAIN_BN.put("Bread / Bakery Van","রুটি / বেকারি ভ্যান");
        MAIN_BN.put("Scrap Buyer",       "ভাঙারি ক্রেতা");
        MAIN_BN.put("Others",            "অন্যান্য");
    }

    // ── English subcategories (DB keys) ──────────────────────────────────
    public static final Map<String, List<String>> SUB = new LinkedHashMap<>();
    static {
        SUB.put("Vegetable Seller", Arrays.asList(
                "Leafy Vegetables","Common Vegetables","Seasonal Vegetables",
                "Spicy & Cooking Items","Beans & Pods","Other"));
        SUB.put("Fish Seller", Arrays.asList(
                "Fresh Water Fish","Premium Fish","Small Fish","Sea Fish",
                "Processed Fish","Other"));
        SUB.put("Meat Seller", Arrays.asList(
                "Beef Items","Chicken Items","Mutton Items","Duck & Bird",
                "Ready-to-Cook","Other"));
        SUB.put("Fruit Seller", Arrays.asList(
                "Local Fruits","Seasonal Fruits","Imported Fruits",
                "Cut Fruits","Juice Fruits","Other"));
        SUB.put("Bread / Bakery Van", Arrays.asList(
                "Bread Items","Cakes","Biscuits & Snacks","Sweet Bakery",
                "Dairy & Related","Other"));
        SUB.put("Scrap Buyer", Arrays.asList(
                "Metal Scrap","Plastic Items","Electronics Scrap",
                "Paper & Books","Home Scrap","Other"));
    }

    // ── Bangla subcategory names (display only) ──────────────────────────
    public static final Map<String, String> SUB_BN = new LinkedHashMap<>();
    static {
        // Vegetable Seller
        SUB_BN.put("Leafy Vegetables",      "পাতাজাতীয় সবজি");
        SUB_BN.put("Common Vegetables",     "সাধারণ সবজি");
        SUB_BN.put("Seasonal Vegetables",   "মৌসুমী সবজি");
        SUB_BN.put("Spicy & Cooking Items", "মশলা ও রান্নার উপাদান");
        SUB_BN.put("Beans & Pods",          "শিম ও বীজ");
        // Fish Seller
        SUB_BN.put("Fresh Water Fish",      "মিঠাপানির মাছ");
        SUB_BN.put("Premium Fish",          "দামি মাছ");
        SUB_BN.put("Small Fish",            "ছোট মাছ");
        SUB_BN.put("Sea Fish",              "সামুদ্রিক মাছ");
        SUB_BN.put("Processed Fish",        "প্রক্রিয়াজাত মাছ");
        // Meat Seller
        SUB_BN.put("Beef Items",            "গরুর মাংস");
        SUB_BN.put("Chicken Items",         "মুরগির মাংস");
        SUB_BN.put("Mutton Items",          "খাসির মাংস");
        SUB_BN.put("Duck & Bird",           "হাঁস ও পাখি");
        SUB_BN.put("Ready-to-Cook",         "রান্নার জন্য প্রস্তুত");
        // Fruit Seller
        SUB_BN.put("Local Fruits",          "দেশীয় ফল");
        SUB_BN.put("Seasonal Fruits",       "মৌসুমী ফল");
        SUB_BN.put("Imported Fruits",       "আমদানিকৃত ফল");
        SUB_BN.put("Cut Fruits",            "কাটা ফল");
        SUB_BN.put("Juice Fruits",          "জুসের ফল");
        // Bread / Bakery Van
        SUB_BN.put("Bread Items",           "রুটির আইটেম");
        SUB_BN.put("Cakes",                 "কেক");
        SUB_BN.put("Biscuits & Snacks",     "বিস্কুট ও স্ন্যাকস");
        SUB_BN.put("Sweet Bakery",          "মিষ্টি বেকারি");
        SUB_BN.put("Dairy & Related",       "দুগ্ধজাত ও সংশ্লিষ্ট");
        // Scrap Buyer
        SUB_BN.put("Metal Scrap",           "ধাতব ভাঙারি");
        SUB_BN.put("Plastic Items",         "প্লাস্টিক সামগ্রী");
        SUB_BN.put("Electronics Scrap",     "ইলেকট্রনিক্স ভাঙারি");
        SUB_BN.put("Paper & Books",         "কাগজ ও বই");
        SUB_BN.put("Home Scrap",            "গৃহস্থালি ভাঙারি");
        // Generic
        SUB_BN.put("Other",                 "অন্যান্য");
    }

    /** Get display name for a subcategory group based on current language */
    public static String getSubDisplayName(Context ctx, String englishKey) {
        if (LanguageManager.isEnglish(ctx)) return englishKey;
        String bn = SUB_BN.get(englishKey);
        return (bn != null) ? bn : englishKey;
    }

    /** Get display name for a main category based on current language */
    public static String getCatDisplayName(Context ctx, String englishKey) {
        if (LanguageManager.isEnglish(ctx)) return englishKey;
        String bn = MAIN_BN.get(englishKey);
        return (bn != null) ? bn : englishKey;
    }

    public static List<String> getSubcategories(String category) {
        if (category == null) return Collections.emptyList();
        List<String> list = SUB.get(category);
        return (list == null) ? Collections.emptyList() : list;
    }

    // ── Hawker detail items ───────────────────────────────────────────────
    public static final List<HawkerCategory> HAWKER_CATEGORIES = new ArrayList<>();

    static {
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Vegetable Seller", "সবজি বিক্রেতা", "🥦",
                Arrays.asList(
                        new SubGroup("Leafy Vegetables",      "পাতাজাতীয় সবজি",
                                Arrays.asList("Spinach (Palong)", "Lal shak", "Pui shak", "Kolmi shak"),
                                Arrays.asList("পালং শাক", "লাল শাক", "পুঁই শাক", "কলমি শাক")),
                        new SubGroup("Common Vegetables",     "সাধারণ সবজি",
                                Arrays.asList("Potato", "Onion", "Tomato", "Brinjal", "Cucumber"),
                                Arrays.asList("আলু", "পেঁয়াজ", "টমেটো", "বেগুন", "শসা")),
                        new SubGroup("Seasonal Vegetables",   "মৌসুমী সবজি",
                                Arrays.asList("Cauliflower", "Cabbage", "Pumpkin", "Bottle gourd", "Radish"),
                                Arrays.asList("ফুলকপি", "বাঁধাকপি", "কুমড়া", "লাউ", "মুলা")),
                        new SubGroup("Spicy & Cooking Items", "মশলা ও রান্নার উপাদান",
                                Arrays.asList("Green chili", "Garlic", "Ginger", "Lemon", "Coriander leaf"),
                                Arrays.asList("কাঁচা মরিচ", "রসুন", "আদা", "লেবু", "ধনেপাতা")),
                        new SubGroup("Beans & Pods",          "শিম ও বীজ",
                                Arrays.asList("Bean", "Peas", "Okra", "Yardlong bean"),
                                Arrays.asList("শিম", "মটরশুঁটি", "ঢেঁড়স", "বরবটি"))
                )
        ));
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Fish Seller", "মাছ বিক্রেতা", "🐟",
                Arrays.asList(
                        new SubGroup("Fresh Water Fish", "মিঠাপানির মাছ",
                                Arrays.asList("Rui", "Katla", "Mrigal", "Pangash", "Tilapia"),
                                Arrays.asList("রুই", "কাতলা", "মৃগেল", "পাঙ্গাশ", "তেলাপিয়া")),
                        new SubGroup("Premium Fish",     "দামি মাছ",
                                Arrays.asList("Ilish", "Chingri (Shrimp)", "Boal", "Koi"),
                                Arrays.asList("ইলিশ", "চিংড়ি", "বোয়াল", "কই")),
                        new SubGroup("Small Fish",       "ছোট মাছ",
                                Arrays.asList("Puti", "Mola", "Kachki", "Tengra"),
                                Arrays.asList("পুঁটি", "মলা", "কাচকি", "টেংরা")),
                        new SubGroup("Sea Fish",         "সামুদ্রিক মাছ",
                                Arrays.asList("Tuna", "Rupchanda", "Loitta"),
                                Arrays.asList("টুনা", "রূপচাঁদা", "লইট্টা")),
                        new SubGroup("Processed Fish",   "প্রক্রিয়াজাত মাছ",
                                Arrays.asList("Dry fish (Shutki)", "Cut fish pieces", "Cleaned fish"),
                                Arrays.asList("শুঁটকি মাছ", "মাছের টুকরা", "পরিষ্কার মাছ"))
                )
        ));
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Meat Seller", "মাংস বিক্রেতা", "🍖",
                Arrays.asList(
                        new SubGroup("Beef Items",    "গরুর মাংস",
                                Arrays.asList("Beef meat", "Beef bone", "Beef liver", "Beef fat"),
                                Arrays.asList("গরুর মাংস", "গরুর হাড়", "গরুর কলিজা", "গরুর চর্বি")),
                        new SubGroup("Chicken Items", "মুরগির মাংস",
                                Arrays.asList("Broiler chicken", "Sonali chicken", "Deshi chicken"),
                                Arrays.asList("ব্রয়লার মুরগি", "সোনালি মুরগি", "দেশি মুরগি")),
                        new SubGroup("Mutton Items",  "খাসির মাংস",
                                Arrays.asList("Goat meat", "Goat liver", "Goat leg"),
                                Arrays.asList("খাসির মাংস", "খাসির কলিজা", "খাসির পা")),
                        new SubGroup("Duck & Bird",   "হাঁস ও পাখি",
                                Arrays.asList("Duck", "Pigeon", "Quail bird"),
                                Arrays.asList("হাঁস", "কবুতর", "কোয়েল পাখি")),
                        new SubGroup("Ready-to-Cook", "রান্নার জন্য প্রস্তুত",
                                Arrays.asList("Minced meat", "Marinated meat", "BBQ chicken"),
                                Arrays.asList("কিমা মাংস", "মেরিনেট মাংস", "বিবিকিউ মুরগি"))
                )
        ));
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Fruit Seller", "ফল বিক্রেতা", "🍉",
                Arrays.asList(
                        new SubGroup("Local Fruits",    "দেশীয় ফল",
                                Arrays.asList("Mango", "Banana", "Guava", "Jackfruit"),
                                Arrays.asList("আম", "কলা", "পেয়ারা", "কাঁঠাল")),
                        new SubGroup("Seasonal Fruits", "মৌসুমী ফল",
                                Arrays.asList("Watermelon", "Litchi", "Orange", "Pineapple"),
                                Arrays.asList("তরমুজ", "লিচু", "কমলা", "আনারস")),
                        new SubGroup("Imported Fruits", "আমদানিকৃত ফল",
                                Arrays.asList("Apple", "Grapes", "Dragon fruit", "Kiwi"),
                                Arrays.asList("আপেল", "আঙুর", "ড্রাগন ফ্রুট", "কিউই")),
                        new SubGroup("Cut Fruits",      "কাটা ফল",
                                Arrays.asList("Fruit mix", "Ready fruit box"),
                                Arrays.asList("মিশ্র ফল", "রেডি ফ্রুট বক্স")),
                        new SubGroup("Juice Fruits",    "জুসের ফল",
                                Arrays.asList("Lemon", "Malta", "Sugarcane", "Papaya"),
                                Arrays.asList("লেবু", "মাল্টা", "আখ", "পেঁপে"))
                )
        ));
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Bread / Bakery Van", "রুটি / বেকারি ভ্যান", "🍞",
                Arrays.asList(
                        new SubGroup("Bread Items",       "রুটির আইটেম",
                                Arrays.asList("White bread", "Brown bread", "Bun", "Roti"),
                                Arrays.asList("সাদা রুটি", "বাদামি রুটি", "বান", "রুটি")),
                        new SubGroup("Cakes",             "কেক",
                                Arrays.asList("Birthday cake", "Cupcake", "Pastry", "Cream roll"),
                                Arrays.asList("জন্মদিনের কেক", "কাপকেক", "পেস্ট্রি", "ক্রিম রোল")),
                        new SubGroup("Biscuits & Snacks", "বিস্কুট ও স্ন্যাকস",
                                Arrays.asList("Biscuit", "Toast", "Chanachur", "Chips"),
                                Arrays.asList("বিস্কুট", "টোস্ট", "চানাচুর", "চিপস")),
                        new SubGroup("Sweet Bakery",      "মিষ্টি বেকারি",
                                Arrays.asList("Donut", "Muffin", "Cookies"),
                                Arrays.asList("ডোনাট", "মাফিন", "কুকিজ")),
                        new SubGroup("Dairy & Related",   "দুগ্ধজাত ও সংশ্লিষ্ট",
                                Arrays.asList("Butter", "Cheese", "Jam", "Eggs"),
                                Arrays.asList("মাখন", "পনির", "জ্যাম", "ডিম"))
                )
        ));
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Scrap Buyer", "ভাঙারি ক্রেতা", "♻️",
                Arrays.asList(
                        new SubGroup("Metal Scrap",       "ধাতব ভাঙারি",
                                Arrays.asList("Iron", "Steel", "Aluminum", "Copper"),
                                Arrays.asList("লোহা", "স্টিল", "অ্যালুমিনিয়াম", "তামা")),
                        new SubGroup("Plastic Items",     "প্লাস্টিক সামগ্রী",
                                Arrays.asList("Plastic bottles", "Plastic containers", "Broken buckets"),
                                Arrays.asList("প্লাস্টিক বোতল", "প্লাস্টিক পাত্র", "ভাঙা বালতি")),
                        new SubGroup("Electronics Scrap", "ইলেকট্রনিক্স ভাঙারি",
                                Arrays.asList("Old mobile", "Broken TV", "Computer parts", "Fan/motor"),
                                Arrays.asList("পুরনো মোবাইল", "ভাঙা টিভি", "কম্পিউটার পার্টস", "ফ্যান/মোটর")),
                        new SubGroup("Paper & Books",     "কাগজ ও বই",
                                Arrays.asList("Newspaper", "Old books", "Cartons"),
                                Arrays.asList("পত্রিকা", "পুরনো বই", "কার্টন")),
                        new SubGroup("Home Scrap",        "গৃহস্থালি ভাঙারি",
                                Arrays.asList("Old furniture", "Broken chair", "Old utensils"),
                                Arrays.asList("পুরনো আসবাবপত্র", "ভাঙা চেয়ার", "পুরনো বাসনপত্র"))
                )
        ));
        HAWKER_CATEGORIES.add(new HawkerCategory(
                "Others", "অন্যান্য", "📦",
                new ArrayList<>()
        ));
    }

    // ── Model classes ──────────────────────────────────────────────────────

    public static class HawkerCategory {
        public final String name;      // English — DB key
        public final String nameBn;    // Bangla — display only
        public final String emoji;
        public final List<SubGroup> subGroups;

        public HawkerCategory(String name, String nameBn, String emoji, List<SubGroup> subGroups) {
            this.name      = name;
            this.nameBn    = nameBn;
            this.emoji     = emoji;
            this.subGroups = subGroups;
        }

        /** Display name based on app language */
        public String displayName(Context ctx) {
            return LanguageManager.isEnglish(ctx) ? name : nameBn;
        }

        public List<String> allItems() {
            List<String> all = new ArrayList<>();
            for (SubGroup g : subGroups) all.addAll(g.items);
            return all;
        }

        public List<String> allItemsBn() {
            List<String> all = new ArrayList<>();
            for (SubGroup g : subGroups) all.addAll(g.itemsBn);
            return all;
        }

        /** Get display items based on language */
        public List<String> displayItems(Context ctx) {
            return LanguageManager.isEnglish(ctx) ? allItems() : allItemsBn();
        }
    }

    public static class SubGroup {
        public final String       groupName;   // English — DB key
        public final String       groupNameBn; // Bangla — display
        public final List<String> items;       // English — DB keys
        public final List<String> itemsBn;     // Bangla — display

        public SubGroup(String groupName, String groupNameBn,
                        List<String> items, List<String> itemsBn) {
            this.groupName   = groupName;
            this.groupNameBn = groupNameBn;
            this.items       = items;
            this.itemsBn     = itemsBn;
        }

        /** Display name based on app language */
        public String displayName(Context ctx) {
            return LanguageManager.isEnglish(ctx) ? groupName : groupNameBn;
        }

        /** Display items based on app language */
        public List<String> displayItems(Context ctx) {
            return LanguageManager.isEnglish(ctx) ? items : itemsBn;
        }

        /**
         * Given a display item (possibly Bangla), return the English DB key.
         * Used when saving selections to Firebase.
         */
        public String toEnglishKey(String displayItem) {
            // If already English
            int idx = items.indexOf(displayItem);
            if (idx >= 0) return items.get(idx);
            // Try Bangla lookup
            int idxBn = itemsBn.indexOf(displayItem);
            if (idxBn >= 0) return items.get(idxBn);
            return displayItem; // fallback
        }
    }
}