package com.example.towncrierbd.utils;

import android.content.Context;

/**
 * AppStrings — Centralised bilingual string table for Town Crier BD.
 * Every user-visible string comes from here so language can be switched at runtime.
 *
 * Usage:  AppStrings s = AppStrings.get(context);
 *         tvTitle.setText(s.feedTitle());
 */
public class AppStrings {

    private final boolean en; // true = English, false = Bangla

    private AppStrings(Context context) {
        this.en = LanguageManager.isEnglish(context);
    }

    public static AppStrings get(Context context) {
        return new AppStrings(context);
    }

    private String p(String engStr, String bnStr) {
        return en ? engStr : bnStr;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Common
    // ══════════════════════════════════════════════════════════════════════

    public String appName()          { return p("Town Crier BD", "টাউন ক্রায়ার বিডি"); }
    public String ok()               { return p("OK", "ঠিক আছে"); }
    public String cancel()           { return p("Cancel", "বাতিল"); }
    public String save()             { return p("Save", "সংরক্ষণ"); }
    public String yes()              { return p("Yes", "হ্যাঁ"); }
    public String no()               { return p("No", "না"); }
    public String loading()          { return p("Loading...", "লোড হচ্ছে..."); }
    public String pleaseWait()       { return p("Please wait...", "অনুগ্রহ করে অপেক্ষা করুন..."); }
    public String error()            { return p("Error", "ত্রুটি"); }
    public String success()          { return p("Success", "সফল"); }
    public String back()             { return p("Back", "পেছনে"); }
    public String close()            { return p("Close", "বন্ধ"); }
    public String submit()           { return p("Submit", "জমা দিন"); }
    public String delete()           { return p("Delete", "মুছুন"); }
    public String edit()             { return p("Edit", "সম্পাদনা"); }
    public String details()          { return p("Details", "বিস্তারিত"); }
    public String nearby()           { return p("Nearby", "কাছাকাছি"); }
    public String justNow()          { return p("Just now", "এইমাত্র"); }
    public String today()            { return p("Today", "আজ"); }

    // ══════════════════════════════════════════════════════════════════════
    // Auth — Login
    // ══════════════════════════════════════════════════════════════════════

    public String loginTitle()           { return p("Town Crier BD", "টাউন ক্রায়ার বিডি"); }
    public String loginSubtitle()        { return p("Connect with your neighborhood", "আপনার এলাকার সাথে যুক্ত হন"); }
    public String loginHintEmailPhone()  { return p("Number or Email", "নম্বর অথবা ইমেইল"); }
    public String loginHintPassword()    { return p("Password", "পাসওয়ার্ড"); }
    public String loginBtn()             { return p("Login", "লগইন"); }
    public String loginGoSignup()        { return p("Don't have an account? Sign Up", "অ্যাকাউন্ট নেই? সাইন আপ করুন"); }
    public String loginForgot()          { return p("Forgot Password?", "পাসওয়ার্ড ভুলে গেছেন?"); }
    public String loginWrongCredential() { return p("Wrong email or password", "ভুল ইমেইল বা পাসওয়ার্ড"); }
    public String loginPhoneNotFound()   { return p("Phone number not found", "ফোন নম্বর পাওয়া যায়নি"); }
    public String loginFailed()          { return p("Login failed", "লগইন ব্যর্থ হয়েছে"); }
    public String loginRequiredFields()  { return p("Email/Phone and Password required", "ইমেইল/ফোন এবং পাসওয়ার্ড দিন"); }

    // ══════════════════════════════════════════════════════════════════════
    // Auth — Signup
    // ══════════════════════════════════════════════════════════════════════

    public String signupTitle()           { return p("Town Crier BD", "টাউন ক্রায়ার বিডি"); }
    public String signupSubtitle()        { return p("Connect with your neighborhood", "আপনার এলাকার সাথে যুক্ত হন"); }
    public String signupHintName()        { return p("Enter your name", "আপনার নাম লিখুন"); }
    public String signupHintPhone()       { return p("+880 1XXX-XXXXXX", "+880 1XXX-XXXXXX"); }
    public String signupHintEmail()       { return p("example@email.com", "example@email.com"); }
    public String signupHintDob()         { return p("Select Date of Birth", "জন্ম তারিখ নির্বাচন করুন"); }
    public String signupHintPassword()    { return p("Password", "পাসওয়ার্ড"); }
    public String signupIAm()             { return p("I am a", "আমি একজন"); }
    public String signupRoleGeneral()     { return p("General User", "সাধারণ ব্যবহারকারী"); }
    public String signupRoleAnnouncer()   { return p("Announcer", "বিক্রেতা/ঘোষক"); }
    public String signupBtn()             { return p("Sign Up", "সাইন আপ"); }
    public String signupCreating()        { return p("Creating account...", "অ্যাকাউন্ট তৈরি হচ্ছে..."); }
    public String signupGoLogin()         { return p("Already have an account? Login", "ইতিমধ্যে অ্যাকাউন্ট আছে? লগইন করুন"); }
    public String signupRequiredFields()  { return p("Name, Email, Password required", "নাম, ইমেইল, পাসওয়ার্ড দিন"); }
    public String signupPasswordShort()   { return p("Password must be at least 6 characters", "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে"); }
    public String signupInvalidEmail()    { return p("Please enter a valid email address", "সঠিক ইমেইল ঠিকানা দিন"); }
    public String signupEmailInUse()      { return p("This email is already registered. Please login.", "এই ইমেইল ইতিমধ্যে নিবন্ধিত। লগইন করুন।"); }
    public String signupWelcome(String name) { return p("Welcome, " + name + "! 🎉", "স্বাগতম, " + name + "! 🎉"); }
    public String signupSelectCategories()   { return p("Please select your selling categories to complete signup", "সাইন আপ সম্পন্ন করতে আপনার বিক্রির ক্যাটাগরি নির্বাচন করুন"); }
    public String signupSaveProfileFailed(String msg) { return p("Failed to save profile: " + msg, "প্রোফাইল সংরক্ষণ ব্যর্থ: " + msg); }

    // ══════════════════════════════════════════════════════════════════════
    // Forgot Password
    // ══════════════════════════════════════════════════════════════════════

    public String forgotTitle()       { return p("Forgot Password?", "পাসওয়ার্ড ভুলে গেছেন?"); }
    public String forgotSubtitle()    { return p("Enter your email to receive a reset link", "রিসেট লিংক পেতে আপনার ইমেইল দিন"); }
    public String forgotHint()        { return p("example@email.com", "example@email.com"); }
    public String forgotSendBtn()     { return p("Send Reset Link", "রিসেট লিংক পাঠান"); }
    public String forgotSending()     { return p("Sending...", "পাঠানো হচ্ছে..."); }
    public String forgotBackToLogin() { return p("Back to Login", "লগইনে ফিরুন"); }
    public String forgotEnterEmail()  { return p("Please enter your email", "অনুগ্রহ করে ইমেইল দিন"); }
    public String forgotInvalidEmail(){ return p("Enter a valid email", "সঠিক ইমেইল দিন"); }
    public String forgotSent()        { return p("Reset link sent! Check your email ✅", "রিসেট লিংক পাঠানো হয়েছে! ইমেইল চেক করুন ✅"); }
    public String forgotFailed(String msg) { return p("Failed: " + msg, "ব্যর্থ: " + msg); }

    // ══════════════════════════════════════════════════════════════════════
    // Feed — General & Announcer
    // ══════════════════════════════════════════════════════════════════════

    public String feedGoodDay()           { return p("Good day!", "শুভ দিন!"); }
    public String feedWelcomeBack() { return p("Welcome Back, ", "স্বাগতম, "); }
    public String feedHello()  { return p("Hello, ", "হ্যালো, "); }
    public String feedSeeNearby()         { return p("See what's happening nearby", "কাছাকাছি কী হচ্ছে দেখুন"); }
    public String feedNearbyPosts()       { return p("Nearby Posts", "কাছাকাছি পোস্ট"); }
    public String feedYourLocation()      { return p("YOUR LOCATION", "আপনার অবস্থান"); }
    public String feedDetecting()         { return p("Detecting location...", "অবস্থান শনাক্ত হচ্ছে..."); }
    public String feedWithinKm(int km)    { return p("within " + km + " km  ▾", km + " কিমি-এর মধ্যে  ▾"); }
    public String feedSearchHint()        { return p("Search announcements...", "পোস্ট খুঁজুন..."); }
    public String feedSearchHintAnn()     { return p("Search by title, category...", "শিরোনাম, ক্যাটাগরি দিয়ে খুঁজুন..."); }
    public String feedFilterCategory()    { return p("Filter by Category", "ক্যাটাগরি অনুযায়ী ফিল্টার"); }
    public String feedShowFilter()        { return p("Show", "দেখান"); }
    public String feedHideFilter()        { return p("Hide", "লুকান"); }
    public String feedEmptyTitle()        { return p("No announcements nearby", "কাছাকাছি কোনো ঘোষণা নেই"); }
    public String feedEmptySubtitle()     { return p("Try expanding your search area", "অনুসন্ধান এলাকা বাড়ান"); }
    public String feedEmptyAnnouncerTitle() { return p("No posts nearby", "কাছাকাছি কোনো পোস্ট নেই"); }
    public String feedEmptyAnnouncerSub() { return p("General users nearby will appear here", "কাছাকাছি সাধারণ ব্যবহারকারীরা এখানে দেখাবে"); }
    public String feedTurnOnGps()         { return p("Turn ON GPS", "GPS চালু করুন"); }
    public String feedPermissionDenied()  { return p("Location permission denied", "লোকেশন অনুমতি নাকচ"); }
    public String feedLoadFailed() { return p("Failed to load feed: ", "ফিড লোড ব্যর্থ: "); }
    public String feedProfileFailed()     { return p("Failed to load profile. Check internet.", "প্রোফাইল লোড ব্যর্থ। ইন্টারনেট চেক করুন।"); }
    public String feedSelectRadius()      { return p("Select Radius", "রেডিয়াস নির্বাচন করুন"); }
    public String feedNoInternet()        { return p("⚠️  No internet connection", "⚠️  ইন্টারনেট সংযোগ নেই"); }
    public String feedWithinKmBanner(double km) {
        return p("within " + (int) km + " km  ▾", (int) km + " কিমি-এর মধ্যে  ▾");
    }

    /** Radius options shown in dialog */
    public String[] feedRadiusOptions() {
        return en
                ? new String[]{"1 km", "3 km", "5 km", "10 km"}
                : new String[]{"১ কিমি", "৩ কিমি", "৫ কিমি", "১০ কিমি"};
    }

    // ══════════════════════════════════════════════════════════════════════
    // Feed Item Card
    // ══════════════════════════════════════════════════════════════════════

    public String cardListen()       { return p("🔊 Listen", "🔊 শুনুন"); }
    public String cardChat()         { return p("💬 Chat", "💬 চ্যাট"); }
    public String cardCall()         { return p("📞 Call", "📞 কল করুন"); }
    public String cardDirection()    { return p("🧭 Direction", "🧭 দিকনির্দেশনা"); }
    public String cardEdit()         { return p("Edit", "সম্পাদনা"); }
    public String cardDelete()       { return p("Delete", "মুছুন"); }
    public String cardDetails()      { return p("Details", "বিস্তারিত"); }
    public String cardKmAway(String dist) { return p(dist + " km away", dist + " কিমি দূরে"); }
    public String cardNoPhone()      { return p("No phone number", "ফোন নম্বর নেই"); }
    public String cardNoChatSelf()   { return p("Cannot chat with yourself", "নিজের সাথে চ্যাট করা যাবে না"); }
    public String cardNoChatEmpty()  { return p("Cannot start chat", "চ্যাট শুরু করা যাচ্ছে না"); }
    public String cardDeleteConfirmTitle() { return p("Delete Post", "পোস্ট মুছুন"); }
    public String cardDeleteConfirmMsg()   { return p("Are you sure you want to delete this post?", "আপনি কি এই পোস্টটি মুছতে চান?"); }
    public String cardEditTitle()    { return p("Edit Post", "পোস্ট সম্পাদনা"); }
    public String cardEditLabelTitle()  { return p("Title", "শিরোনাম"); }
    public String cardEditLabelDesc()   { return p("Description", "বিবরণ"); }
    public String cardTitleEmpty()   { return p("Title cannot be empty", "শিরোনাম খালি রাখা যাবে না"); }
    public String cardUpdated()      { return p("Updated ✅", "আপডেট হয়েছে ✅"); }
    public String cardDeleted()      { return p("Deleted ✅", "মুছে ফেলা হয়েছে ✅"); }
    public String cardDeleteFailed() { return p("Delete failed", "মুছতে ব্যর্থ"); }
    public String cardAudioBadge()   { return p("🔊 Audio", "🔊 অডিও"); }
    public String cardTranslateToBn(){ return p("🌐 বাংলা", "🌐 বাংলা"); }
    public String cardTranslateToEn(){ return p("🌐 English", "🌐 ইংরেজি"); }
    public String cardTranslating()  { return p("⏳ Translating...", "⏳ অনুবাদ হচ্ছে..."); }
    public String cardTranslateFail(){ return p("Translation failed", "অনুবাদ ব্যর্থ"); }

    // Expiry text on card
    public String expiryDays(long days)   { return p("⏳ Expires in " + days + " day" + (days > 1 ? "s" : ""), "⏳ " + days + " দিনে মেয়াদ শেষ"); }
    public String expiryHours(long hrs)   { return p("⏳ Expires in " + hrs + " hr" + (hrs > 1 ? "s" : ""), "⏳ " + hrs + " ঘণ্টায় মেয়াদ শেষ"); }
    public String expiryMinutes(long min) { return p("⏳ Expires in " + min + " min", "⏳ " + min + " মিনিটে মেয়াদ শেষ"); }
    public String expirySoon()            { return p("⏳ Expiring soon", "⏳ শীঘ্রই মেয়াদ শেষ"); }
    public String expired()               { return p("Expired", "মেয়াদ শেষ"); }

    // Relative time
    public String timeMinAgo(long m)  { return p(m + " min ago", m + " মিনিট আগে"); }
    public String timeHrAgo(long h)   { return p(h + " hr ago", h + " ঘণ্টা আগে"); }
    public String timeDayAgo(long d)  { return p(d + " day" + (d > 1 ? "s" : "") + " ago", d + " দিন আগে"); }

    // ══════════════════════════════════════════════════════════════════════
    // Add Announcement Wizard
    // ══════════════════════════════════════════════════════════════════════

    public String addHeaderAnnouncement() { return p("📢 Create Announcement", "📢 ঘোষণা তৈরি করুন"); }
    public String addHeaderRequest()      { return p("📋 Create a Request", "📋 অনুরোধ তৈরি করুন"); }
    public String addStepOf(int s, int t) { return p("Step " + s + " of " + t, "ধাপ " + s + " / " + t); }

    // Step 1
    public String addStep1Title()         { return p("Select Categories", "ক্যাটাগরি নির্বাচন করুন"); }
    public String addStep1Subtitle()      { return p("Choose all that apply", "প্রযোজ্য সব নির্বাচন করুন"); }
    public String addStep1Next()          { return p("Next: Choose Products →", "পরবর্তী: পণ্য বাছুন →"); }
    public String addStep1SelectAtLeast() { return p("Please select at least one category", "অন্তত একটি ক্যাটাগরি নির্বাচন করুন"); }
    public String addStep1EditCatHint()   { return p("💡 To add more categories, go to Profile → Edit Categories", "💡 আরও ক্যাটাগরি যোগ করতে, প্রোফাইল → ক্যাটাগরি সম্পাদনা করুন"); }
    public String addStep1Items(int n)    { return p(n + " items", n + " পণ্য"); }

    // Step 2
    public String addStep2Title()         { return p("Select Products", "পণ্য নির্বাচন করুন"); }
    public String addStep2Subtitle()      { return p("Buyers can filter by specific items (optional)", "ক্রেতারা নির্দিষ্ট পণ্য দিয়ে ফিল্টার করতে পারবেন (ঐচ্ছিক)"); }
    public String addStep2CustomHint()    { return p("Describe your 'Others' category (e.g. Flower pot seller)", "'অন্যান্য' ক্যাটাগরি বর্ণনা করুন (যেমন: ফুলের টব বিক্রেতা)"); }
    public String addStep2SelectAll(String cat) { return p("✓ Select all from " + cat, "✓ " + cat + " থেকে সব নির্বাচন"); }
    public String addStep2NeedDescribe(String pt) {
        return p("Describe what you " + ("request".equals(pt) ? "need" : "sell") + " in the field above.",
                "উপরের ঘরে আপনি কী " + ("request".equals(pt) ? "চান" : "বিক্রি করেন") + " তা লিখুন।");
    }
    public String addStep2NoProducts()    { return p("No specific products for selected categories. Proceed to next step.", "নির্বাচিত ক্যাটাগরির জন্য কোনো পণ্য নেই। পরবর্তী ধাপে যান।"); }
    public String addStep2Back()          { return p("← Back", "← পেছনে"); }
    public String addStep2Next()          { return p("Next: Post Details →", "পরবর্তী: পোস্টের বিস্তারিত →"); }

    // Step 3
    public String addStep3LabelTitle()    { return p("Title *", "শিরোনাম *"); }
    public String addStep3HintTitle()     { return p("e.g. Fresh vegetables available today", "যেমন: আজ তাজা সবজি পাওয়া যাচ্ছে"); }
    public String addStep3LabelDesc()     { return p("Description *", "বিবরণ *"); }
    public String addStep3HintDesc()      { return p("Add details about your post...", "পোস্টের বিস্তারিত লিখুন..."); }
    public String addStep3AddImage()      { return p("Add Image", "ছবি যোগ করুন"); }
    public String addStep3AddAudio()      { return p("Add Audio", "অডিও যোগ করুন"); }
    public String addStep3ExpiryLabel()   { return p("Post Expiry *", "পোস্টের মেয়াদ *"); }
    public String addStep3ExpiryMax()     { return p("max 7 days", "সর্বোচ্চ ৭ দিন"); }
    public String addStep3ExpiryInfo()    { return p("Post disappears automatically after this time", "এই সময়ের পরে পোস্টটি স্বয়ংক্রিয়ভাবে সরে যাবে"); }
    public String addStep3ExpiryHint()    { return p("e.g. 2", "যেমন: ২"); }
    public String addStep3ExpiryUnit0()   { return p("Minutes", "মিনিট"); }
    public String addStep3ExpiryUnit1()   { return p("Hours", "ঘণ্টা"); }
    public String addStep3ExpiryUnit2()   { return p("Days", "দিন"); }
    public String[] addStep3ExpiryUnits() { return new String[]{ addStep3ExpiryUnit0(), addStep3ExpiryUnit1(), addStep3ExpiryUnit2() }; }
    public String addStep3ExpiryPreview(String readable) { return p("⏳ Post will expire in " + readable, "⏳ পোস্টটি " + readable + " পরে মেয়াদ শেষ হবে"); }
    public String addStep3ExpiryOver()    { return p("⚠️ Maximum 7 days allowed", "⚠️ সর্বোচ্চ ৭ দিন অনুমোদিত"); }
    public String addStep3Back()          { return p("← Back", "← পেছনে"); }
    public String addStep3Publish()       { return p("Publish ✅", "প্রকাশ করুন ✅"); }

    // Expiry human readable
    public String expiryHumanDay(long d)  { return p(d + (d == 1 ? " day" : " days"), d + " দিন"); }
    public String expiryHumanHour(long h) { return p(h + (h == 1 ? " hour" : " hours"), h + " ঘণ্টা"); }
    public String expiryHumanMin(long m)  { return p(m + (m == 1 ? " minute" : " minutes"), m + " মিনিট"); }

    // Publish messages
    public String addPublishNoTitle()     { return p("Title is required", "শিরোনাম আবশ্যক"); }
    public String addPublishNoDesc()      { return p("Description is required", "বিবরণ আবশ্যক"); }
    public String addPublishNoExpiry()    { return p("Please enter expiry time (e.g. 2 Hours)", "মেয়াদ উল্লেখ করুন (যেমন: ২ ঘণ্টা)"); }
    public String addPublishExpiryOver()  { return p("Maximum expiry is 7 days", "সর্বোচ্চ মেয়াদ ৭ দিন"); }
    public String addPublishNoLoc()       { return p("Location not detected. Open Feed once first.", "অবস্থান শনাক্ত হয়নি। প্রথমে ফিড খুলুন।"); }
    public String addPublishUserNotFound(){ return p("User not found", "ব্যবহারকারী পাওয়া যায়নি"); }
    public String addPublishImgUploading(){ return p("Uploading image...", "ছবি আপলোড হচ্ছে..."); }
    public String addPublishAudioUploading() { return p("Uploading audio...", "অডিও আপলোড হচ্ছে..."); }
    public String addPublishAudioPct(int pct){ return p("Uploading audio... " + pct + "%", "অডিও আপলোড... " + pct + "%"); }
    public String addPublishImgFailed()   { return p("Image upload failed, posting without image", "ছবি আপলোড ব্যর্থ, ছবি ছাড়াই পোস্ট হবে"); }
    public String addPublishAudioFailed() { return p("Audio upload failed, posting without audio", "অডিও আপলোড ব্যর্থ, অডিও ছাড়াই পোস্ট হবে"); }
    public String addPublishing()         { return p("Publishing...", "প্রকাশ হচ্ছে..."); }
    public String addPublishSuccess(String pt) {
        return "request".equals(pt) ? p("Request posted ✅", "অনুরোধ পোস্ট হয়েছে ✅")
                : p("Published ✅", "প্রকাশিত ✅");
    }
    public String addPublishFailed(String msg){ return p("Save failed: " + msg, "সংরক্ষণ ব্যর্থ: " + msg); }
    public String addStopRecordFirst()    { return p("Please stop recording first", "প্রথমে রেকর্ডিং বন্ধ করুন"); }

    // Audio options
    public String audioOptionRecord()    { return p("🎙️ Record Audio", "🎙️ অডিও রেকর্ড করুন"); }
    public String audioOptionFile()      { return p("📁 Select Audio File", "📁 অডিও ফাইল নির্বাচন করুন"); }
    public String audioAddTitle()        { return p("Add Audio", "অডিও যোগ করুন"); }
    public String audioRecording()       { return p("🔴 Recording... tap again to stop", "🔴 রেকর্ডিং চলছে... থামাতে আবার ট্যাপ করুন"); }
    public String audioRecorded()        { return p("✅ Audio recorded", "✅ অডিও রেকর্ড হয়েছে"); }
    public String audioFileSelected()    { return p("✅ Audio file selected", "✅ অডিও ফাইল নির্বাচিত"); }
    public String audioStopBtn()         { return p("⏹ Stop", "⏹ বন্ধ"); }
    public String audioAddBtn()          { return p("Add Audio", "অডিও যোগ করুন"); }
    public String audioLoadFailed()      { return p("Audio load failed", "অডিও লোড ব্যর্থ"); }
    public String audioCouldNotRead()    { return p("Could not read audio", "অডিও পড়া যায়নি"); }
    public String audioRecordError(String msg)  { return p("❌ Error: " + msg, "❌ ত্রুটি: " + msg); }
    public String audioStopFailed()      { return p("❌ Stop failed", "❌ বন্ধ করা যায়নি"); }

    // Image options
    public String imageAddTitle()        { return p("Add Image", "ছবি যোগ করুন"); }
    public String imageGallery()         { return p("📷 Gallery", "📷 গ্যালারি"); }
    public String imageSelected()        { return p("✅ Image selected", "✅ ছবি নির্বাচিত"); }
    public String imageReadFailed()      { return p("Image read failed", "ছবি পড়া যায়নি"); }

    // ══════════════════════════════════════════════════════════════════════
    // Announcement Detail
    // ══════════════════════════════════════════════════════════════════════

    public String detailCall()           { return p("📞 Call", "📞 কল করুন"); }
    public String detailChat()           { return p("💬 Chat", "💬 চ্যাট"); }
    public String detailDirections()     { return p("🧭 Get Directions", "🧭 দিকনির্দেশনা পান"); }
    public String detailPlayAudio()      { return p("🔊 Play Audio", "🔊 অডিও চালান"); }
    public String detailStopAudio()      { return p("⏹ Stop Audio", "⏹ অডিও বন্ধ"); }
    public String detailLoadingAudio()   { return p("Loading audio...", "অডিও লোড হচ্ছে..."); }
    public String detailPlayingAudio()   { return p("▶ Playing", "▶ চলছে"); }
    public String detailPlaybackError()  { return p("Playback error", "প্লেব্যাক ত্রুটি"); }
    public String detailCannotPlay()     { return p("Cannot play audio", "অডিও চালানো যাচ্ছে না"); }
    public String detailNoPhone()        { return p("No phone number", "ফোন নম্বর নেই"); }
    public String detailCannotChat()     { return p("Cannot start chat", "চ্যাট শুরু করা যাচ্ছে না"); }
    /** Label shown on translate button — shows target language */
    public String detailTranslateToEn()  { return p("🌐 English", "🌐 ইংরেজি"); }
    public String detailTranslateToBn()  { return p("🌐 বাংলা", "🌐 বাংলা"); }
    public String detailTranslating()    { return p("⏳ Translating...", "⏳ অনুবাদ হচ্ছে..."); }
    public String detailTranslateFail()  { return p("Translation failed", "অনুবাদ ব্যর্থ"); }

    // ══════════════════════════════════════════════════════════════════════
    // Chat
    // ══════════════════════════════════════════════════════════════════════

    public String chatTitle()            { return p("Chat", "চ্যাট"); }
    public String chatHint()             { return p("Type a message...", "একটি বার্তা লিখুন..."); }
    public String chatInvalid()          { return p("Invalid chat", "অবৈধ চ্যাট"); }
    public String chatSelfError()        { return p("Cannot chat with yourself", "নিজের সাথে চ্যাট করা যাবে না"); }
    public String chatLoadFailed(String msg) { return p("Failed to load messages: " + msg, "বার্তা লোড ব্যর্থ: " + msg); }
    public String chatSendFailed(String msg) { return p("Send failed: " + msg, "পাঠানো ব্যর্থ: " + msg); }

    // ══════════════════════════════════════════════════════════════════════
    // Inbox
    // ══════════════════════════════════════════════════════════════════════

    public String inboxTitle()           { return p("Messages", "বার্তা"); }
    public String inboxEmpty()           { return p("No messages yet", "এখনো কোনো বার্তা নেই"); }
    public String inboxEmptySubtitle()   { return p("Chat with announcers by tapping Chat on any post", "যেকোনো পোস্টে Chat ট্যাপ করে ঘোষকের সাথে কথা বলুন"); }
    public String inboxTapToChat()       { return p("Tap to chat", "চ্যাট করতে ট্যাপ করুন"); }
    public String inboxNow()             { return p("Now", "এখন"); }

    // ══════════════════════════════════════════════════════════════════════
    // Profile
    // ══════════════════════════════════════════════════════════════════════

    public String profileEditBtn()           { return p("Edit Profile", "প্রোফাইল সম্পাদনা"); }
    public String profileLogout()            { return p("Logout", "লগআউট"); }
    public String profileEditCategories()    { return p("🛒 Edit My Selling Categories", "🛒 আমার বিক্রির ক্যাটাগরি সম্পাদনা"); }
    public String profileMyPosts()           { return p("My Posts", "আমার পোস্ট"); }
    public String profileEditDialogTitle()   { return p("Edit Profile", "প্রোফাইল সম্পাদনা"); }
    public String profileLabelName()         { return p("Name", "নাম"); }
    public String profileLabelPhone()        { return p("Phone", "ফোন"); }
    public String profileNotLoaded()         { return p("Profile not loaded yet", "প্রোফাইল এখনো লোড হয়নি"); }
    public String profileNameEmpty()         { return p("Name cannot be empty", "নাম খালি রাখা যাবে না"); }
    public String profileUpdated()           { return p("Profile updated ✅", "প্রোফাইল আপডেট হয়েছে ✅"); }
    public String profileCatsUpdated()       { return p("Categories updated ✅", "ক্যাটাগরি আপডেট হয়েছে ✅"); }
    public String profileEmailPrefix()       { return p("Email: ", "ইমেইল: "); }
    public String profilePhonePrefix()       { return p("Phone: ", "ফোন: "); }
    public String profileLanguage()          { return p("Language / ভাষা", "Language / ভাষা"); }
    public String profileRoleAnnouncer()     { return p("ANNOUNCER", "বিক্রেতা/ঘোষক"); }
    public String profileRoleGeneral()       { return p("General User", "সাধারণ ব্যবহারকারী"); }
    public String profileLangToggleSection() { return p("App Language", "অ্যাপের ভাষা"); }

    // ══════════════════════════════════════════════════════════════════════
    // Map
    // ══════════════════════════════════════════════════════════════════════

    public String mapTitle()             { return p("Map View", "মানচিত্র"); }
    public String mapNearbyAnn()         { return p("Nearby Announcements", "কাছাকাছি ঘোষণা"); }
    public String mapNoLocation()        { return p("Location not ready", "অবস্থান প্রস্তুত নয়"); }
    public String mapSelectMarker()      { return p("Select a marker first", "প্রথমে একটি মার্কার নির্বাচন করুন"); }
    public String mapTurnOnGps()         { return p("Turn ON GPS", "GPS চালু করুন"); }
    public String mapPermDenied()        { return p("Location permission denied", "লোকেশন অনুমতি নাকচ"); }
    public String mapKmAway(double d)    {
        String fmt = String.format(java.util.Locale.getDefault(), "%.1f", d);
        return p(fmt + " km away", fmt + " কিমি দূরে");
    }
    public String mapYou()               { return p("You", "আপনি"); }
    public String mapViewDetails()       { return p("View Details", "বিস্তারিত দেখুন"); }
    public String mapCategory()          { return p("Category", "ক্যাটাগরি"); }
    public String mapNoPhone()           { return p("No phone number", "ফোন নম্বর নেই"); }
    public String mapCannotChat()        { return p("Cannot start chat", "চ্যাট শুরু করা যাচ্ছে না"); }
    public String mapChatSelf()          { return p("Cannot chat with yourself", "নিজের সাথে চ্যাট করা যাবে না"); }

    // ══════════════════════════════════════════════════════════════════════
    // Hawker Category Screen
    // ══════════════════════════════════════════════════════════════════════

    public String catScreenTitle()        { return p("What do you sell?", "আপনি কী বিক্রি করেন?"); }
    public String catScreenSubtitle()     { return p("Select your categories — buyers nearby will find you", "আপনার ক্যাটাগরি নির্বাচন করুন — কাছাকাছি ক্রেতারা আপনাকে খুঁজে পাবেন"); }
    public String catScreenHint()         { return p("Tap a category to select. Tap again to see and choose specific items.", "নির্বাচন করতে একটি ক্যাটাগরিতে ট্যাপ করুন। নির্দিষ্ট পণ্য দেখতে আবার ট্যাপ করুন।"); }
    public String catSelectAll(String cat){ return p("✓ Select all items from " + cat, "✓ " + cat + " থেকে সব পণ্য নির্বাচন"); }
    public String catOthersLabel()        { return p("Category name *", "ক্যাটাগরির নাম *"); }
    public String catOthersHint()         { return p("e.g. Flower pot seller", "যেমন: ফুলের টব বিক্রেতা"); }
    public String catOthersSubLabel()     { return p("Add items you sell (optional)", "আপনি যা বিক্রি করেন তা যোগ করুন (ঐচ্ছিক)"); }
    public String catOthersItemHint()     { return p("Type an item name", "একটি পণ্যের নাম লিখুন"); }
    public String catOthersAddBtn()       { return p("+ Add", "+ যোগ করুন"); }
    public String catDeselected(String cat){ return p(cat + " deselected", cat + " বাতিল"); }
    public String catSelectAtLeast()      { return p("Please select at least one category", "অন্তত একটি ক্যাটাগরি নির্বাচন করুন"); }
    public String catOthersNameRequired() { return p("Please enter a name for your 'Others' category", "'অন্যান্য' ক্যাটাগরির জন্য একটি নাম দিন"); }
    public String catDoneBtn(int cats, int subs) {
        String catText = en ? (cats + " categor" + (cats == 1 ? "y" : "ies"))
                : (cats + "টি ক্যাটাগরি");
        String subText = subs > 0
                ? (en ? (", " + subs + " item" + (subs == 1 ? "" : "s"))
                : (", " + subs + "টি পণ্য"))
                : "";
        return p("Done  (" + catText + subText + " selected) →",
                "সম্পন্ন  (" + catText + subText + " নির্বাচিত) →");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Notifications
    // ══════════════════════════════════════════════════════════════════════

    public String notifEnableTitle()    { return p("Enable Notifications", "নোটিফিকেশন চালু করুন"); }
    public String notifEnableMsgAnn()   { return p("Town Crier BD sends notifications when new announcements are nearby.", "কাছাকাছি নতুন ঘোষণা এলে টাউন ক্রায়ার বিডি নোটিফিকেশন পাঠায়।"); }
    public String notifEnableMsgReq()   { return p("Town Crier BD sends notifications when new requests match your categories.", "নতুন অনুরোধ আপনার ক্যাটাগরির সাথে মিললে টাউন ক্রায়ার বিডি নোটিফিকেশন পাঠায়।"); }
    public String notifAllow()          { return p("Allow", "অনুমোদন"); }
    public String notifNotNow()         { return p("Not now", "এখন নয়"); }

    // ══════════════════════════════════════════════════════════════════════
    // TTS (Text-to-Speech) language code
    // ══════════════════════════════════════════════════════════════════════

    /** Returns java.util.Locale appropriate for TTS based on app language */
    public java.util.Locale ttsLocale() {
        return en ? java.util.Locale.ENGLISH : new java.util.Locale("bn", "BD");
    }
}