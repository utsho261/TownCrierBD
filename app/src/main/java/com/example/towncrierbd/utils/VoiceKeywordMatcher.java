package com.example.towncrierbd.utils;

import java.util.*;

/**
 * Intelligent Keyword Matcher for Voice Speech-to-Text and Announcement text.
 * Analyzes spoken voice or written text in Bengali and English to detect
 * matching categories, subcategories, and specific product items.
 */
public class VoiceKeywordMatcher {

    public static class MatchResult {
        public final Set<String> matchedCategories    = new LinkedHashSet<>();
        public final Set<String> matchedSubcategories = new LinkedHashSet<>();
        public final Set<String> matchedProducts      = new LinkedHashSet<>();
        public final List<String> matchedDisplayNames = new ArrayList<>();

        public boolean hasMatches() {
            return !matchedProducts.isEmpty() || !matchedCategories.isEmpty();
        }
    }

    private static class KeywordRule {
        final List<String> triggers;
        final String categoryName;
        final String subGroupName;
        final String productKey;
        final String displayBn;

        KeywordRule(String categoryName, String subGroupName, String productKey, String displayBn, String... triggers) {
            this.categoryName = categoryName;
            this.subGroupName = subGroupName;
            this.productKey   = productKey;
            this.displayBn    = displayBn;
            this.triggers     = Arrays.asList(triggers);
        }
    }

    private static final List<KeywordRule> RULES = new ArrayList<>();

    static {
        // ── VEGETABLES (সবজি বিক্রেতা) ─────────────────────────────────────────
        add("Vegetable Seller", "Leafy Vegetables", "Spinach (Palong)", "পালং শাক", "পালং", "পালংশাক", "spinach", "palong");
        add("Vegetable Seller", "Leafy Vegetables", "Lal shak", "লাল শাক", "লালশাক", "lal shak", "lalshak");
        add("Vegetable Seller", "Leafy Vegetables", "Pui shak", "পুঁই শাক", "পুঁই", "পুঁইশাক", "pui shak");
        add("Vegetable Seller", "Leafy Vegetables", "Kolmi shak", "কলমি শাক", "কলমি", "কলমিশাক", "kolmi");

        add("Vegetable Seller", "Common Vegetables", "Potato", "আলু", "আলু", "আলুর", "potato", "potatoes", "aloo");
        add("Vegetable Seller", "Common Vegetables", "Onion", "পেঁয়াজ", "পেঁয়াজ", "পিয়াজ", "পেঁয়াজ", "onion", "onions", "piyaj");
        add("Vegetable Seller", "Common Vegetables", "Tomato", "টমেটো", "টমেটো", "টমেটোর", "tomato", "tomatoes");
        add("Vegetable Seller", "Common Vegetables", "Brinjal", "বেগুন", "বেগুন", "বেগুনের", "brinjal", "eggplant", "begun");
        add("Vegetable Seller", "Common Vegetables", "Cucumber", "শসা", "শসা", "খিরা", "cucumber");

        add("Vegetable Seller", "Seasonal Vegetables", "Cauliflower", "ফুলকপি", "ফুলকপি", "ফুল কপি", "cauliflower");
        add("Vegetable Seller", "Seasonal Vegetables", "Cabbage", "বাঁধাকপি", "বাঁধাকপি", "বাধাকপি", "পাতাকপি", "cabbage");
        add("Vegetable Seller", "Seasonal Vegetables", "Pumpkin", "কুমড়া", "কুমড়া", "মিষ্টিকুমড়া", "মিষ্টি কুমড়া", "pumpkin");
        add("Vegetable Seller", "Seasonal Vegetables", "Bottle gourd", "লাউ", "লাউ", "কদু", "bottle gourd", "lau");
        add("Vegetable Seller", "Seasonal Vegetables", "Radish", "মুলা", "মুলা", "মূলা", "radish", "mula");

        add("Vegetable Seller", "Spicy & Cooking Items", "Green chili", "কাঁচা মরিচ", "কাঁচা মরিচ", "কাঁচামরিচ", "মরিচ", "chili", "chilli");
        add("Vegetable Seller", "Spicy & Cooking Items", "Garlic", "রসুন", "রসুন", "রসুনের", "garlic", "roshun");
        add("Vegetable Seller", "Spicy & Cooking Items", "Ginger", "আদা", "আদা", "আদার", "ginger", "ada");
        add("Vegetable Seller", "Spicy & Cooking Items", "Lemon", "লেবু", "লেবু", "লেবুর", "lemon", "lebu");
        add("Vegetable Seller", "Spicy & Cooking Items", "Coriander leaf", "ধনেপাতা", "ধনেপাতা", "ধনিয়া", "ধনে পাতা", "coriander");

        add("Vegetable Seller", "Beans & Pods", "Bean", "শিম", "শিম", "সীম", "bean", "beans", "shim");
        add("Vegetable Seller", "Beans & Pods", "Peas", "মটরশুঁটি", "মটরশুঁটি", "মটরশুটি", "কড়াইশুঁটি", "peas", "motor");
        add("Vegetable Seller", "Beans & Pods", "Okra", "ঢেঁড়স", "ঢেঁড়স", "ঢেড়শ", "ভেন্ডি", "okra", "dherosh");
        add("Vegetable Seller", "Beans & Pods", "Yardlong bean", "বরবটি", "বরবটি", "বর্বটি", "yardlong", "borboti");

        // ── FISH (মাছ বিক্রেতা) ────────────────────────────────────────────────
        add("Fish Seller", "Fresh Water Fish", "Rui", "রুই", "রুই", "রুই মাছ", "রুইমাছ", "rui");
        add("Fish Seller", "Fresh Water Fish", "Katla", "কাতলা", "কাতলা", "কাতলা মাছ", "কাতল", "katla");
        add("Fish Seller", "Fresh Water Fish", "Mrigal", "মৃগেল", "মৃগেল", "মিরগেল", "mrigal");
        add("Fish Seller", "Fresh Water Fish", "Pangash", "পাঙ্গাশ", "পাঙ্গাশ", "পাঙ্গাস", "pangash", "pangas");
        add("Fish Seller", "Fresh Water Fish", "Tilapia", "তেলাপিয়া", "তেলাপিয়া", "তেলাপিয়া", "tilapia");

        add("Fish Seller", "Premium Fish", "Ilish", "ইলিশ", "ইলিশ", "ইলিশ মাছ", "ইলিশমাছ", "ilish", "hilsa");
        add("Fish Seller", "Premium Fish", "Chingri (Shrimp)", "চিংড়ি", "চিংড়ি", "চিংড়ি", "গলদা", "বাগদা", "chingri", "shrimp", "prawn");
        add("Fish Seller", "Premium Fish", "Boal", "বোয়াল", "বোয়াল", "বোয়াল", "boal");
        add("Fish Seller", "Premium Fish", "Koi", "কই", "কই", "কই মাছ", "koi");

        add("Fish Seller", "Small Fish", "Puti", "পুঁটি", "পুঁটি", "পুটি", "puti");
        add("Fish Seller", "Small Fish", "Mola", "মলা", "মলা", "মলা মাছ", "mola");
        add("Fish Seller", "Small Fish", "Kachki", "কাচকি", "কাচকি", "কাচকি মাছ", "kachki");
        add("Fish Seller", "Small Fish", "Tengra", "টেংরা", "টেংরা", "টেংরা মাছ", "tengra");

        add("Fish Seller", "Sea Fish", "Tuna", "টুনা", "টুনা", "tuna");
        add("Fish Seller", "Sea Fish", "Rupchanda", "রূপচাঁদা", "রূপচাঁদা", "রুপচান্দা", "রূপচান্দা", "rupchanda", "pomfret");
        add("Fish Seller", "Sea Fish", "Loitta", "লইট্টা", "লইট্টা", "লইট্যা", "loitta");
        add("Fish Seller", "Processed Fish", "Dry fish (Shutki)", "শুঁটকি মাছ", "শুঁটকি", "শুটকি", "শুঁটকি মাছ", "shutki", "dry fish");

        // ── MEAT (মাংস বিক্রেতা) ────────────────────────────────────────────────
        add("Meat Seller", "Beef Items", "Beef meat", "গরুর মাংস", "গরুর মাংস", "গরু", "গরুর গোশত", "বিফ", "beef");
        add("Meat Seller", "Beef Items", "Beef bone", "গরুর হাড়", "গরুর হাড়", "হাড়", "নেহারি", "bone");
        add("Meat Seller", "Beef Items", "Beef liver", "গরুর কলিজা", "কলিজা", "গরুর কলিজা", "liver");
        add("Meat Seller", "Chicken Items", "Broiler chicken", "ব্রয়লার মুরগি", "ব্রয়লার", "মুরগি", "মুরগির মাংস", "চিকেন", "chicken", "broiler");
        add("Meat Seller", "Chicken Items", "Sonali chicken", "সোনালি মুরগি", "সোনালি", "সোনালী", "দেশি মুরগি", "sonali");
        add("Meat Seller", "Mutton Items", "Goat meat", "খাসির মাংস", "খাসির মাংস", "খাসি", "খাসির গোশত", "মটন", "mutton", "goat");
        add("Meat Seller", "Duck & Bird", "Duck", "হাঁস", "হাঁস", "হাঁসের মাংস", "duck");
        add("Meat Seller", "Duck & Bird", "Pigeon", "কবুতর", "কবুতর", "কবুতরের বাচ্চা", "pigeon");
        add("Meat Seller", "Ready-to-Cook", "Minced meat", "কিমা মাংস", "কিমা", "minced", "keema");

        // ── FRUITS (ফল বিক্রেতা) ────────────────────────────────────────────────
        add("Fruit Seller", "Local Fruits", "Mango", "আম", "আম", "আমের", "mango", "mangoes");
        add("Fruit Seller", "Local Fruits", "Banana", "কলা", "কলা", "কলার", "banana", "bananas", "kola");
        add("Fruit Seller", "Local Fruits", "Guava", "পেয়ারা", "পেয়ারা", "পেয়ারা", "guava", "peyara");
        add("Fruit Seller", "Local Fruits", "Jackfruit", "কাঁঠাল", "কাঁঠাল", "কাঠাল", "jackfruit");
        add("Fruit Seller", "Seasonal Fruits", "Watermelon", "তরমুজ", "তরমুজ", "watermelon", "tormuj");
        add("Fruit Seller", "Seasonal Fruits", "Litchi", "লিচু", "লিচু", "litchi", "lichi");
        add("Fruit Seller", "Seasonal Fruits", "Orange", "কমলা", "কমলা", "orange", "komla");
        add("Fruit Seller", "Seasonal Fruits", "Pineapple", "আনারস", "আনারস", "pineapple", "anaros");
        add("Fruit Seller", "Imported Fruits", "Apple", "আপেল", "আপেল", "apple", "apples");
        add("Fruit Seller", "Imported Fruits", "Grapes", "আঙুর", "আঙুর", "আঙ্গুর", "grape", "grapes", "angur");
        add("Fruit Seller", "Imported Fruits", "Dragon fruit", "ড্রাগন ফ্রুট", "ড্রাগন", "dragon fruit");
        add("Fruit Seller", "Juice Fruits", "Malta", "মাল্টা", "মাল্টা", "মালটা", "malta");
        add("Fruit Seller", "Juice Fruits", "Papaya", "পেঁপে", "পেঁপে", "পেপে", "papaya");

        // ── BAKERY (রুটি / বেকারি ভ্যান) ────────────────────────────────────────
        add("Bread / Bakery Van", "Bread Items", "White bread", "সাদা রুটি", "পাউরুটি", "রুটি", "ব্রেড", "bread", "roti");
        add("Bread / Bakery Van", "Bread Items", "Bun", "বান", "বান", "বান রুটি", "bun");
        add("Bread / Bakery Van", "Cakes", "Birthday cake", "কেক", "কেক", "cake", "cakes");
        add("Bread / Bakery Van", "Cakes", "Pastry", "পেস্ট্রি", "পেস্ট্রি", "pastry");
        add("Bread / Bakery Van", "Biscuits & Snacks", "Biscuit", "বিস্কুট", "বিস্কুট", "বিস্কিট", "biscuit", "biscuits");
        add("Bread / Bakery Van", "Biscuits & Snacks", "Toast", "টোস্ট", "টোস্ট", "টোস্ট বিস্কুট", "toast");
        add("Bread / Bakery Van", "Biscuits & Snacks", "Chanachur", "চানাচুর", "চানাচুর", "chanachur");
        add("Bread / Bakery Van", "Sweet Bakery", "Cookies", "কুকিজ", "কুকিজ", "cookies");
        add("Bread / Bakery Van", "Dairy & Related", "Butter", "মাখন", "মাখন", "বাটার", "butter");
        add("Bread / Bakery Van", "Dairy & Related", "Cheese", "পনির", "পনির", "চিজ", "cheese");
        add("Bread / Bakery Van", "Dairy & Related", "Eggs", "ডিম", "ডিম", "egg", "eggs", "dim");

        // ── SCRAP (ভাঙারি ক্রেতা) ──────────────────────────────────────────────
        add("Scrap Buyer", "Metal Scrap", "Iron", "লোহা", "লোহা", "লোহালক্কড়", "iron");
        add("Scrap Buyer", "Metal Scrap", "Copper", "তামা", "তামা", "পিতল", "copper");
        add("Scrap Buyer", "Plastic Items", "Plastic bottles", "প্লাস্টিক বোতল", "বোতল", "প্লাস্টিক", "প্লাস্টিকের বোতল", "bottle", "plastic");
        add("Scrap Buyer", "Electronics Scrap", "Old mobile", "পুরনো মোবাইল", "মোবাইল", "ভাঙা মোবাইল", "ফোন", "phone", "mobile");
        add("Scrap Buyer", "Electronics Scrap", "Broken TV", "ভাঙা টিভি", "টিভি", "টেলিভিশন", "tv");
        add("Scrap Buyer", "Paper & Books", "Newspaper", "পত্রিকা", "কাগজ", "পত্রিকা", "খবরের কাগজ", "newspaper", "paper");
        add("Scrap Buyer", "Paper & Books", "Old books", "পুরনো বই", "বই", "পুরাতন বই", "books");
        add("Scrap Buyer", "Paper & Books", "Cartons", "কার্টন", "কার্টুন", "কার্টন", "carton", "cartons");
    }

    private static void add(String cat, String sub, String prodKey, String displayBn, String... triggers) {
        RULES.add(new KeywordRule(cat, sub, prodKey, displayBn, triggers));
    }

    /**
     * Matches raw input text (speech transcript, title, description) against
     * keyword rules and returns detected categories, subcategories, and products.
     */
    public static MatchResult match(String rawText) {
        MatchResult result = new MatchResult();
        if (rawText == null || rawText.trim().isEmpty()) return result;

        String normalized = normalize(rawText);

        // General Category keywords
        if (containsAny(normalized, "সবজি", "শাকসবজি", "শাক", "তরকারি", "vegetable", "veggie")) {
            result.matchedCategories.add("Vegetable Seller");
        }
        if (containsAny(normalized, "মাছ", "মাছের", "তাজা মাছ", "fish")) {
            result.matchedCategories.add("Fish Seller");
        }
        if (containsAny(normalized, "মাংস", "গোশত", "গোশ", "meat", "মুরগি", "গরু", "খাসি")) {
            result.matchedCategories.add("Meat Seller");
        }
        if (containsAny(normalized, "ফল", "ফলমূল", "ফলফলাদি", "fruit", "fruits")) {
            result.matchedCategories.add("Fruit Seller");
        }
        if (containsAny(normalized, "বেকারি", "রুটি", "পাউরুটি", "বিস্কুট", "কেক", "bakery", "bread")) {
            result.matchedCategories.add("Bread / Bakery Van");
        }
        if (containsAny(normalized, "ভাঙারি", "ভাংরি", "ভাঙারী", "পুরাতন", "scrap")) {
            result.matchedCategories.add("Scrap Buyer");
        }

        // Product level rules
        for (KeywordRule rule : RULES) {
            for (String trigger : rule.triggers) {
                if (containsWord(normalized, normalize(trigger))) {
                    result.matchedCategories.add(rule.categoryName);
                    result.matchedSubcategories.add(rule.subGroupName);
                    result.matchedProducts.add(rule.productKey);
                    if (!result.matchedDisplayNames.contains(rule.displayBn)) {
                        result.matchedDisplayNames.add(rule.displayBn);
                    }
                    break;
                }
            }
        }

        return result;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        // Convert to lowercase and clean standard punctuation
        String text = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC).toLowerCase(java.util.Locale.ROOT).replaceAll("[.,;?!:\"'()\\-—_/]", " ");
        return text.replaceAll("[\\s\\u00a0]+", " ").trim();
    }

    private static boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (containsWord(text, w.toLowerCase())) return true;
        }
        return false;
    }

    public static boolean containsKeyword(String source,String target) {
        return containsWord(normalize(source),normalize(target));
    }
    private static boolean containsWord(String source,String target) {
        if(source.isEmpty() || target.isEmpty())return false;
        String suffix=target.matches(".*[\\u0980-\\u09FF].*")?"(?:গুলো|গুলি|টার|টির|টা|টি|দের|ের|র|ও)?":"(?:s|es)?";
        return java.util.regex.Pattern.compile("(?<![\\p{L}\\p{M}])"+java.util.regex.Pattern.quote(target)+suffix+"(?![\\p{L}\\p{M}])")
            .matcher(source).find();
    }
}
