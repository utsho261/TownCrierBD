# বিনা API খরচে Voice — নতুন setup

**Voice থেকে item বাছাইয়ের জন্য Firebase Blaze, Cloud Functions deploy, Speech-to-Text API, billing account বা API key লাগবে না।** আগের paid voice setup-এর নির্দেশনা এই সংস্করণে বাতিল। Microphone ও File—দুই উৎসের আসল audio এখন ফোনের CPU-তে sherpa-onnx দিয়ে শনাক্ত হবে। বাংলা default; English-ও আছে।

এটি source update। এই পরিবেশে নতুন APK তৈরি বা বাস্তব কণ্ঠ পরীক্ষা হয়নি। ZIP-এর সব `PREVIOUS_BUILD.apk` পুরোনো। নতুন source build করে install করা আবশ্যক।

## Windows / Android Studio-তে ধাপ

1. ZIP extract করুন। ভিতরের `TownCrierBD` folder খুলুন—যেখানে `settings.gradle.kts`, `gradlew.bat` ও `app` আছে।
2. Android Studio-এর Gradle JDK হিসেবে **JDK 17** এবং SDK Manager-এ **Android SDK 34** রাখুন। `local.properties`-এর `sdk.dir` আপনার কম্পিউটারের SDK path হবে। আগের Firebase/Cloudinary/Maps settings বজায় রাখুন। Voice-এর জন্য নতুন key লাগবে না।
3. প্রথম build-এ Internet রাখুন, Gradle Offline mode বন্ধ রাখুন। Wi-Fi ব্যবহার করতে পারেন। Voice engine ও বাংলা/English model মিলিয়ে প্রায় **190 MB** download হবে; Android/Gradle-এর সাধারণ dependency download আলাদা। Download বিনামূল্যে, তবে আপনার Internet/data charge থাকলে সেটি প্রযোজ্য।
4. Android Studio Terminal-এ চালান:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

   Gradle নিজেই `prepareOfflineVoice` চালিয়ে official model/runtime নামাবে, binary hash/size পরীক্ষা করবে এবং APK-তে model যোগ করবে। আলাদা Python, Node, Firebase CLI, NDK বা paid account লাগবে না। দ্বিতীয় build-এ valid cache আবার download হবে না; Clean Project করলেও cache থাকে।
5. নতুন APK হবে `app/build/outputs/apk/debug/app-debug.apk`। সেটি ফোনে install করুন। Version: **1.1-offline-voice**, versionCode 2। পুরোনো release-signed APK থাকলে একই signing configuration দিয়ে release build ব্যবহার করুন (`.\gradlew.bat :app:assembleRelease`); শুধু signature mismatch মেটাতে app data মুছে ফেলবেন না।
6. Add post/request → Audio → **Microphone** অথবা **File**। সঠিক audio language বাছুন। Microphone হলে কথা বলে Stop চাপুন। শনাক্তকরণের progress এবং শেষে transcript/selected items দেখাবে। Next-এ সংশ্লিষ্ট item selected থাকবে; ভুল থাকলে ঠিক করতে পারবেন।

Download মাঝপথে ব্যর্থ হলে Internet ঠিক করে একই build command আবার চালান। অসম্পূর্ণ/ভুল binary ব্যবহার করা হবে না। চাইলে manifest check: `java tools/PrepareOfflineVoice.java . --verify-manifest`; শুধু প্রস্তুতি/download: `java tools/PrepareOfflineVoice.java .`। Android Studio build-এ এগুলো আলাদা করে চালানো জরুরি নয়।

## Voice পরীক্ষা

- নতুন অ্যাপে login করে post screen খুলুন, তারপর Internet বন্ধ করুন। বাংলা ভাষায় Microphone দিয়ে “আলু পেঁয়াজ রসুন” রেকর্ড করে Stop দিন। Potato/Onion/Garlic নির্বাচন দেখুন। Recognition-এর জন্য Internet লাগবে না; post publish-এর জন্য লাগবে।
- একই কথার MP3/M4A file অথবা PCM16/float32 WAV file বাছুন। ফাইলের আসল কথা শনাক্ত হচ্ছে কি না দেখুন। ফাইলের নাম থেকে item নির্বাচন হয় না। অন্য format Android-এর installed decoder support-এর ওপর নির্ভরশীল।
- English নির্বাচন করে “potatoes and onions” পরীক্ষা করুন। interface English হলেও বাংলা voice-এর জন্য audio language বাংলা রাখুন।
- Audio শুনুন; Delete চাপুন: ওই audio-র automatic items মুছবে, আগের manually selected items থাকবে। অন্য recording দিলে আগের automatic selection জমবে না।
- শনাক্তকরণ চলার সময় Delete/replace করুন; পুরোনো result নতুন audio-তে বসবে না। Long file-এর শুরু ও শেষের item দুটোই পরীক্ষা করুন।
- Original recording/file আগের মতো post-এর audio হিসেবে upload হবে; general user সেটিই শুনবেন। Offline transcript আলাদা synthetic audio নয়।

Model ও phone-ভেদে গতি/নির্ভুলতা বদলাবে; noisy audio, regional accent বা অজানা নাম ভুল হতে পারে। Transcript ও selection দেখে সংশোধন করার সুযোগ আছে। এই project-এর device-এ বাংলা item-recognition accuracy এখনও যাচাই হয়নি।

## Voice ছাড়া আগের cloud features

আগের request accept/deliver/receive, server notifications, offer ও tracking workflow এখনও Firebase Cloud Functions-নির্ভর। এই পরিবর্তনে সেগুলো বিনা খরচে host করার ব্যবস্থা যোগ হয়নি। **শুধু voice ঠিক করার জন্য billing চালু করবেন না বা functions deploy করবেন না।** `scripts/deploy-functions.mjs` ওই আলাদা backend-এর জন্য, voice-এর জন্য নয়। No live backend, billing বা rules এখানে বদলানো হয়নি।

নতুন source `transcribeAudio` export করে না, app-ও সেটি ডাকে না। আগে থেকে কোনো paid function deploy করা থাকলে code update সেটি নিজে delete/disable করে না; এই নতুন APK recognition-এর জন্য সেখানে request পাঠাবে না।

## আগের request workflow-এর Database rules

Original deployed rules প্যাকেজে ছিল না। তাই `database.workflow.rules.json` একটি **merge fragment**, সম্পূর্ণ rules file নয়; এটি সরাসরি বর্তমান সব rules-এর জায়গায় বসাবেন না। Firebase Console থেকে বর্তমান rules export করে নিচের paths-এর নতুন নিয়ম merge করুন, test করুন, তারপর publish করুন:

- `announcements`: authenticated read, announcer-এর নিজের announcement ছাড়া client write নয়। সব request create/edit/status পরিবর্তন callable দিয়ে হবে। Index: `userId`, `acceptedByUid`, `expireAt`। নতুন/সক্রিয় announcer post-এ HTTPS image ও audio বাধ্যতামূলক। পুরোনো post deactivate করা যায়।
- `requestTracking/<requestId>`: শুধু ওই request-এর buyer, accepted/delivered ও active থাকা অবস্থায় read করতে পারবেন; client write নিষিদ্ধ।
- `requestNotifications/<uid>`: নিজের notifications পড়া এবং শুধু `read=true` করা যাবে; event লেখা server-এর কাজ। Index: `read`, `timestamp`।
- `postInteractions`: নিজের Star post index পড়া যায়; লেখা server-এর কাজ।
- `broadcasts`: server-only। `speechUsage` পুরোনো server-only path; নতুন offline voice এটি ব্যবহার করে না।

Root বা parent-level broad `.write: true`/`.read: true` রাখা যাবে না—parent permission থাকলে child restriction কার্যকর হয় না। `users`, `chats`, `categories`, `phone_to_email`-এর বর্তমান প্রয়োজনীয় নিয়ম আলাদাভাবে বজায় রাখুন। এই merge fragment-এ সেগুলো নেই। আগে থেকেই broad rules থাকলে সেগুলো পরীক্ষা ছাড়া এই workflow production-এ নিরাপদ ধরে নেবেন না।


## অন্য বিদ্যমান feature-এর device check

1. Announcer: নতুন post-এ ছবি ছাড়া অথবা audio ছাড়া Next/Publish আটকে যাবে। General user-এর নিজের request-এ এগুলো optional।
2. Microphone: “আলু পেঁয়াজ রসুন” বলে Stop → নিজের recording শুনুন → transcript/selected item দেখুন → Next-এ Potato/Onion/Garlic নির্বাচিত কি না দেখুন।
3. File: একই কথার M4A/MP3/WAV supported file বাছুন → preview → transcript → Next-এ একই নির্বাচন। ভাষা English করলে “potatoes and onions” পরীক্ষা করুন।
4. Delete audio: preview ও ওই audio থেকে আসা automatic selection মুছবে; আগের manual selection থাকবে। নতুন audio দিলে আগের automatic keyword জমে থাকবে না। Recognition ব্যর্থ হলে raw exception toast দেখাবে না; audio রেখে retry status দেখাবে।
5. Active post থেকে request: একটি নির্দিষ্ট seller post বাছুন। সেই seller-এর feed-এ request আসবে। নিজের request তৈরি করলে কাছের উপযুক্ত sellers নিতে পারবেন।
6. Accept: শুধু target/যোগ্য seller; একবার assign হলে দ্বিতীয় seller নিতে পারবেন না। Delivered: assigned seller; Received: শুধু buyer এবং delivery-এর পরে। Received/Cancel-এ request inactive হয়ে announcer feed থেকে সরে যাবে। Original seller announcement বন্ধ করা হবে না।
7. Buyer/assigned seller Cancel: ৩–৫০০ অক্ষরের কারণ; counterpart-এর chat-এ কারণসহ message ও request notification।
8. Chat → Request offer: active seller post-এর প্রকৃত recorded audio chat-এ আসবে। একাধিক active post থাকলে বাছাই হবে। ★/related post থাকবে যে request/post নিয়ে কথা হচ্ছে সেটি; offered source post-কে ওই context-এর জায়গায় বসানো হবে না। একই chat-এ অন্য post নিয়ে কথা বলতে Change post ব্যবহার করুন।
9. Accept-এর পরে buyer-এর Track seller: দুইটি location sample পেলে towards/away/approximately unchanged। এটি সরলরেখার দূরত্ব, road route বা ETA নয়। ২ মিনিটের বেশি পুরোনো GPS বা অনির্ভরযোগ্য accuracy হলে live movement দাবি করবে না। Seller-এর location permission প্রয়োজন। Foreground location notification থাকবে; received/cancelled/logout হলে sharing service বন্ধ হবে। Android app বন্ধ করে দিলে/OS service বন্ধ করলে announcer Feed খুলে sharing resume করুন।
10. Profile: avatar বা EDIT → Change profile photo, upload/remove; active/newest posts আগে, inactive পরে। `Md. Rahman`, বাংলা নাম, O'Connor গ্রহণ করবে; `Nibir -- &&` বা incomplete mobile number reject করবে। Header inbox খুলবে; language control আলাদা row-তে থাকবে।


## যাচাইয়ের ফল

- আগের 54টি pure Java regression check এবং নতুন 36টি offline PCM/WAV/model-integrity check: pass।
- 42টি Node workflow/handler test: pass; paid speech endpoint export না থাকার check অন্তর্ভুক্ত।
- 59টি Android Java source syntax ও 53টি XML parse: pass।
- Gradle build প্রয়োজনীয় distribution download-এর সময় `Network is unreachable`-এ থেমেছে। Android type compilation, AAR native loading, real speech accuracy, playback/upload/GPS/FCM এবং UI device test সম্পন্ন হয়নি।

Official reference: [sherpa-onnx Android](https://k2-fsa.github.io/sherpa/onnx/android/index.html), [Bengali model](https://huggingface.co/alphacep/vosk-model-small-streaming-bn), [official Bengali decoding configuration](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html), [pinned Android runtime](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1)।
