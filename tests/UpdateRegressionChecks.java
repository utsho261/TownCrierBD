import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.AudioItemSelection;
import com.example.towncrierbd.utils.ProfileValidation;
import com.example.towncrierbd.utils.RequestLifecycle;
import com.example.towncrierbd.utils.SellerMovement;
import com.example.towncrierbd.utils.RequestMode;
import com.example.towncrierbd.utils.VoiceKeywordMatcher;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class UpdateRegressionChecks {
    private static int passed;
    private static Set<String> set(String... items) {
        return new LinkedHashSet<>(Arrays.asList(items));
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
        System.out.println("PASS: " + name);
    }
    public static void main(String[] args) {
        VoiceKeywordMatcher.MatchResult bangla = VoiceKeywordMatcher.match("আজ তাজা আলু, পেঁয়াজ আর রসুন আছে");
        check(bangla.matchedProducts.containsAll(set("Potato", "Onion", "Garlic")), "Bangla speech selects multiple items");
        VoiceKeywordMatcher.MatchResult english = VoiceKeywordMatcher.match("Fresh potatoes and onions for sale");
        check(english.matchedProducts.containsAll(set("Potato", "Onion")), "English speech selects plural items");
        check(VoiceKeywordMatcher.match("পদ্মার ইলিশ মাছ").matchedProducts.contains("Ilish"), "Bangla fish keyword");
        check(VoiceKeywordMatcher.match("Mango and banana").matchedProducts.containsAll(set("Mango", "Banana")), "English fruit keywords");
        check(!VoiceKeywordMatcher.match("আমি এখন যাব").matchedProducts.contains("Mango"), "Bangla pronoun does not select mango");
        check(!VoiceKeywordMatcher.containsKeyword("carton", "art"), "Partial word does not select custom item");
        check(VoiceKeywordMatcher.match(null).matchedProducts.isEmpty(), "Missing transcript selects nothing");
        check(VoiceKeywordMatcher.match("   ").matchedProducts.isEmpty(), "Silence selects nothing");

        Set<String> categories = set("Fruit Seller");
        Set<String> products = set("Mango");
        AudioItemSelection audio = new AudioItemSelection();
        audio.apply(categories, products, bangla.matchedCategories, bangla.matchedProducts);
        check(products.containsAll(set("Mango", "Potato", "Onion", "Garlic")), "Audio adds items alongside manual choices");
        audio.clear(categories, products);
        check(products.equals(set("Mango")), "Deleting audio removes only its automatic items");
        check(categories.equals(set("Fruit Seller")), "Deleting audio preserves manually selected category");

        VoiceKeywordMatcher.MatchResult fish = VoiceKeywordMatcher.match("রুই মাছ");
        audio.apply(categories, products, bangla.matchedCategories, bangla.matchedProducts);
        audio.apply(categories, products, fish.matchedCategories, fish.matchedProducts);
        check(products.equals(set("Mango", "Rui")), "Replacing audio does not accumulate old keywords");
        audio.apply(categories, products, fish.matchedCategories, fish.matchedProducts);
        check(products.equals(set("Mango", "Rui")), "Retrying recognition does not duplicate selections");
        audio.keepProduct("Rui");
        audio.keepCategory("Fish Seller");
        audio.clear(categories, products);
        check(products.equals(set("Mango", "Rui")) && categories.contains("Fish Seller"), "Explicit manual choice survives audio deletion");
        audio.apply(categories, products, fish.matchedCategories, fish.matchedProducts);
        audio.clear(categories, products);
        check(products.contains("Rui"), "Preselected matching item is not treated as audio-owned");

        Announcement request = new Announcement();
        request.setPostType("request");
        check(RequestMode.usesAvailableItems(request.getPostType(), request.getRequestMode()), "Existing requests keep active-post filtering");
        request.setRequestMode(RequestMode.INDEPENDENT);
        check(!RequestMode.usesAvailableItems(request.getPostType(), request.getRequestMode()), "Independent request does not require active seller items");
        request.setSelectedCategories(Arrays.asList("Others"));
        request.setSelectedProducts(Arrays.asList("Flower pot"));
        check(request.getRequestMode().equals(RequestMode.INDEPENDENT) && request.getSelectedProducts().contains("Flower pot"), "Independent mode and custom item survive model storage");
        request.setRequestMode(null);
        check(request.getRequestMode().equals(RequestMode.ACTIVE_POSTS), "Legacy missing mode remains compatible");
        check(!RequestMode.usesAvailableItems("announcement", RequestMode.ACTIVE_POSTS), "Announcer posts are not restricted by request availability");
        for(String name : new String[]{"Nibir -- &&", "12345", "A", "Name<script>", "Name..Other", "---", "A & B"})
            check(!ProfileValidation.validName(name), "Reject invalid name: " + name);
        for(String name : new String[]{"Md. Rahman", "নিবিড় হাসান", "O'Connor", "Jean-Luc", "A. B. Rahman", "D’Souza"})
            check(ProfileValidation.validName(name), "Accept valid name: " + name);
        check(ProfileValidation.name("  Nibir   Hasan  ").equals("Nibir Hasan"), "Normalize name whitespace");
        check(!ProfileValidation.validPhone("01700000"), "Reject the screenshot's incomplete mobile number");
        check(ProfileValidation.validPhone("+880 1700-000000"), "Accept Bangladesh international mobile form");
        check(VoiceKeywordMatcher.match("কলমি শাক").matchedProducts.contains("Kolmi shak"), "Screenshot item Kolmi shak matches Bangla");
        check(VoiceKeywordMatcher.match("kolmi shak").matchedProducts.contains("Kolmi shak"), "Screenshot item Kolmi shak matches English");
        check(VoiceKeywordMatcher.match("গরুর   মাংস").matchedProducts.contains("Beef meat"), "Transcript whitespace does not break multiword keywords");
        long now=1000000;
        request.setUserId("buyer");request.setStatus("open");request.setActive(true);request.setExpireAt(now+1000);request.setSourceAnnouncerUid("seller");
        check(RequestLifecycle.canAccept(request,"seller","ANNOUNCER",now), "Target seller can accept");
        check(!RequestLifecycle.canAccept(request,"other","ANNOUNCER",now), "Other sellers cannot accept targeted request");
        request.setAcceptedByUid("seller");request.setStatus("accepted");request.setExpireAt(now-1);
        check(RequestLifecycle.live(request,now), "Accepted request stays live past listing expiry");
        check(RequestLifecycle.canDeliver(request,"seller"), "Assigned seller can mark delivered");
        check(!RequestLifecycle.canDeliver(request,"other"), "Other sellers cannot mark delivered");
        check(RequestLifecycle.visibleToSeller(request,"seller",now), "Assigned request remains in seller feed");
        check(!RequestLifecycle.visibleToSeller(request,"other",now), "Assigned request leaves other seller feeds");
        check(RequestLifecycle.canCancel(request,"buyer")&&RequestLifecycle.canCancel(request,"seller"), "Both parties can cancel an accepted request");
        request.setStatus("received");request.setActive(false);
        check(!RequestLifecycle.visibleToSeller(request,"seller",now), "Received request disappears from seller feed");
        request.setStatus("cancelled");
        check(!RequestLifecycle.visibleToSeller(request,"seller",now), "Cancelled request disappears from seller feed");
        Announcement fresh=new Announcement();fresh.setTime(1);fresh.setActive(true);fresh.setExpireAt(now+5000);request.setTime(now);
        check(RequestLifecycle.compareProfile(fresh,request,now)<0, "Active post sorts before newer inactive post");
        Announcement newest=new Announcement();newest.setTime(now);newest.setExpireAt(now+5000);
        check(RequestLifecycle.compareProfile(newest,fresh,now)<0, "Newest active post sorts first");
        check(SellerMovement.direction(23.8,90.4,23.81,90.4,23.805,90.4,now-10000,now,now)==-1, "Seller moving closer is towards buyer");
        check(SellerMovement.direction(23.8,90.4,23.805,90.4,23.81,90.4,now-10000,now,now)==1, "Seller moving farther is away from buyer");
        check(SellerMovement.direction(23.8,90.4,23.81,90.4,23.805,90.4,now-150000,now-130000,now)==2, "Stale GPS does not claim movement");
        System.out.println("Passed " + passed + " regression checks.");
    }
}
