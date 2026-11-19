package com.example.towncrierbd;

import org.junit.Assert;
import org.junit.Test;

import com.example.towncrierbd.utils.VoiceKeywordMatcher;

public class VoiceKeywordMatcherTest {

    @Test
    public void testBengaliVegetableSpeech() {
        String speech = "আজকে তাজা আলু, পেঁয়াজ আর রসুন পাওয়া যাচ্ছে";
        VoiceKeywordMatcher.MatchResult res = VoiceKeywordMatcher.match(speech);

        Assert.assertTrue("Should match Vegetable Seller category", res.matchedCategories.contains("Vegetable Seller"));
        Assert.assertTrue("Should match Potato", res.matchedProducts.contains("Potato"));
        Assert.assertTrue("Should match Onion", res.matchedProducts.contains("Onion"));
        Assert.assertTrue("Should match Garlic", res.matchedProducts.contains("Garlic"));
        Assert.assertTrue("Display names should not be empty", res.matchedDisplayNames.size() >= 3);
    }

    @Test
    public void testEnglishVegetableSpeech() {
        String speech = "Fresh potato and onion for sale";
        VoiceKeywordMatcher.MatchResult res = VoiceKeywordMatcher.match(speech);

        Assert.assertTrue("Should match Vegetable Seller category", res.matchedCategories.contains("Vegetable Seller"));
        Assert.assertTrue("Should match Potato", res.matchedProducts.contains("Potato"));
        Assert.assertTrue("Should match Onion", res.matchedProducts.contains("Onion"));
    }

    @Test
    public void testFishSpeech() {
        String speech = "পদ্মার তাজা রুই মাছ এবং ইলিশ মাছ";
        VoiceKeywordMatcher.MatchResult res = VoiceKeywordMatcher.match(speech);

        Assert.assertTrue("Should match Fish Seller category", res.matchedCategories.contains("Fish Seller"));
        Assert.assertTrue("Should match Rui or Ilish", res.matchedProducts.contains("Rui") || res.matchedProducts.contains("Ilish"));
    }

    @Test
    public void testMeatSpeech() {
        String speech = "তাজা মুরগি আর গরুর মাংস";
        VoiceKeywordMatcher.MatchResult res = VoiceKeywordMatcher.match(speech);

        Assert.assertTrue("Should match Meat Seller category", res.matchedCategories.contains("Meat Seller"));
        Assert.assertTrue("Should match Chicken product key", res.matchedProducts.contains("Broiler chicken") || res.matchedProducts.contains("Sonali chicken"));
        Assert.assertTrue("Should match Beef product key", res.matchedProducts.contains("Beef meat"));
    }

    @Test
    public void testFruitSpeech() {
        String speech = "মিষ্টি পাকা আম আর লিচু";
        VoiceKeywordMatcher.MatchResult res = VoiceKeywordMatcher.match(speech);

        Assert.assertTrue("Should match Fruit Seller category", res.matchedCategories.contains("Fruit Seller"));
        Assert.assertTrue("Should match Mango", res.matchedProducts.contains("Mango"));
        Assert.assertTrue("Should match Litchi", res.matchedProducts.contains("Litchi"));
    }

    @Test
    public void testBakerySpeech() {
        String speech = "গরম গরম পাউরুটি ও বিস্কুট";
        VoiceKeywordMatcher.MatchResult res = VoiceKeywordMatcher.match(speech);

        Assert.assertTrue("Should match Bread / Bakery Van category", res.matchedCategories.contains("Bread / Bakery Van"));
        Assert.assertTrue("Should match White bread or Biscuit", res.matchedProducts.contains("White bread") || res.matchedProducts.contains("Biscuit"));
    }

    @Test
    public void testScrapSpeech() {
        String speech = "পুরানো লোহা ও প্লাস্টিক বিক্রি";
        VoiceKeywordMatcher.MatchResult res = VoiceKeywordMatcher.match(speech);

        Assert.assertTrue("Should match Scrap Buyer category", res.matchedCategories.contains("Scrap Buyer"));
        Assert.assertTrue("Should match Iron", res.matchedProducts.contains("Iron"));
        Assert.assertTrue("Should match Plastic bottles", res.matchedProducts.contains("Plastic bottles"));
    }

    @Test
    public void testEmptyOrNullSpeech() {
        VoiceKeywordMatcher.MatchResult resNull = VoiceKeywordMatcher.match(null);
        Assert.assertTrue(resNull.matchedCategories.isEmpty());
        Assert.assertTrue(resNull.matchedProducts.isEmpty());

        VoiceKeywordMatcher.MatchResult resEmpty = VoiceKeywordMatcher.match("   ");
        Assert.assertTrue(resEmpty.matchedCategories.isEmpty());
        Assert.assertTrue(resEmpty.matchedProducts.isEmpty());
    }
    @Test public void pronounsMustNotSelectMango() {
        Assert.assertFalse(VoiceKeywordMatcher.match("আমি এখন যাব").matchedProducts.contains("Mango"));
    }
    @Test public void bengaliSuffixesStillMatchItems() {
        Assert.assertTrue(VoiceKeywordMatcher.match("আলুর দাম কত").matchedProducts.contains("Potato"));
    }
    @Test public void customKeywordsMustMatchCompleteWords() {
        Assert.assertFalse(VoiceKeywordMatcher.containsKeyword("carton","art"));
        Assert.assertTrue(VoiceKeywordMatcher.containsKeyword("two flower pots","flower pot"));
    }
}
