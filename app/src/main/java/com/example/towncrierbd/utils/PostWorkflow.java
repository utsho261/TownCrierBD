package com.example.towncrierbd.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.*;
import androidx.annotation.NonNull;
import com.example.towncrierbd.activities.ChatActivity;
import com.example.towncrierbd.activities.AddAnnouncementActivity;
import com.example.towncrierbd.activities.RequestTrackingActivity;
import com.example.towncrierbd.services.DeliveryLocationService;
import com.example.towncrierbd.activities.AnnouncementDetailActivity;
import com.example.towncrierbd.models.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.database.FirebaseDatabase;
import java.util.*;

public final class PostWorkflow {
    private static String roleUid="", viewerRole="";
    public static void setViewerRole(String role) { roleUid=FirebaseAuth.getInstance().getUid(); viewerRole=role == null ? "" : role; }
    public static boolean request(Announcement a) { return RequestLifecycle.request(a); }
    public static boolean live(Announcement a) { return RequestLifecycle.live(a,System.currentTimeMillis()); }
    public static boolean owns(Announcement a) { return a.getUserId() != null && a.getUserId().equals(FirebaseAuth.getInstance().getUid()); }
    public static boolean matchesCategory(Announcement a,String cat) {
        return cat == null || CategoryConfig.CAT_ALL.equals(cat) || cat.equals(a.getCategory()) || (a.getSelectedCategories() != null && a.getSelectedCategories().contains(cat));
    }
    public static String quantities(Context c,Announcement a) {
        StringBuilder text = new StringBuilder();
        if (a.getRequestedItems() != null) for (RequestedItem item : a.getRequestedItems()) {
            if (text.length()>0) text.append("\n");
            text.append(PostCatalog.itemLabel(c,item.getProduct())).append(" — ").append(item.getQuantity());
        }
        return text.toString();
    }
    public static String chatPeer(Announcement a) {
        if(!owns(a))return a.getUserId();
        if(!request(a))return "";
        String peer=RequestLifecycle.seller(a);return peer.isEmpty()?a.getSourceAnnouncerUid():peer;
    }
    public static void chat(Context c, Announcement a) {
        String peer=chatPeer(a); if(peer==null || peer.isEmpty())return;
        Intent i=new Intent(c,ChatActivity.class);i.putExtra(ChatActivity.EXTRA_OTHER_UID,peer);
        i.putExtra(ChatActivity.EXTRA_OTHER_NAME,owns(a)?a.getAcceptedByName():a.getUserName());
        i.putExtra("announcementId",a.getId());i.putExtra("voiceOffer","voice".equals(a.getContactMethod()));c.startActivity(i);
    }
    public static String statusLabel(Context c,Announcement a) {
        switch(a.getStatus()) {
            case "accepted": return PostCatalog.text(c,"Accepted","গ্রহণ করা হয়েছে");
            case "delivered": return PostCatalog.text(c,"Delivered — awaiting receipt","ডেলিভারি হয়েছে — গ্রহণের অপেক্ষায়");
            case "received": return PostCatalog.text(c,"Received · Inactive","পেয়েছেন · নিষ্ক্রিয়");
            case "cancelled": return PostCatalog.text(c,"Cancelled · Inactive","বাতিল · নিষ্ক্রিয়");
            default:
                if (!live(a)) return PostCatalog.text(c,"Expired · Inactive","সময় শেষ · নিষ্ক্রিয়");
                return PostCatalog.text(c,"Open request","নতুন রিকোয়েস্ট");
        }
    }
    public static void delivery(Context c,Announcement a,String action,Runnable done) {
        boolean accepting="accept".equals(action), cancelling="cancel".equals(action);
        if(cancelling && owns(a) && "delivered".equals(a.getStatus())) {
            Toast.makeText(c,PostCatalog.text(c,"Cannot cancel after delivery is marked as done","ডেলিভারি সম্পন্ন হওয়ার পর বাতিল করা যাবে না"),Toast.LENGTH_LONG).show();
            return;
        }
        if(accepting && c instanceof Activity && !DeliveryLocationService.hasPermission(c)) {
            androidx.core.app.ActivityCompat.requestPermissions((Activity)c,new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},728);
            Toast.makeText(c,PostCatalog.text(c,"Allow location, then tap Accept again.","লোকেশনের অনুমতি দিয়ে আবার Accept চাপুন।"),Toast.LENGTH_LONG).show();return;
        }
        if (accepting) {
            long now = System.currentTimeMillis();
            RequestBidManager.syncExpiredBidsIfNeeded(a, now);
            int maxMin = RequestBidManager.getMaxAllowedOfferMinutes(a, now);
            if (maxMin <= 0) {
                Toast.makeText(c, PostCatalog.text(c, "Request time has expired", "রিকোয়েস্টের সময় শেষ হয়ে গেছে"), Toast.LENGTH_LONG).show();
                return;
            }

            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (20 * c.getResources().getDisplayMetrics().density);
            box.setPadding(pad, pad / 2, pad, 0);

            TextView tvInfo = new TextView(c);
            tvInfo.setText(PostCatalog.text(c,
                    "Current time remaining: " + maxMin + " min\nOffer arrival time (1 to " + maxMin + " min).\nIf you don't arrive within your offered time, your acceptance will be removed automatically.",
                    "বর্তমান সময় বাকি: " + maxMin + " মিনিট\nকত মিনিটে পৌঁছাতে পারবেন তা লিখুন (১ থেকে " + maxMin + " মিনিট)।\nনির্ধারিত সময়ের মধ্যে না পৌঁছাতে পারলে আপনার অ্যাক্সেপ্ট অটোমেটিক বাতিল হয়ে যাবে।"));
            tvInfo.setTextSize(13f);
            tvInfo.setTextColor(0xFF334155);
            box.addView(tvInfo);

            EditText etMinutes = new EditText(c);
            etMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etMinutes.setHint(PostCatalog.text(c, "Minutes (e.g. " + Math.max(1, maxMin) + ")", "মিনিট (যেমন: " + Math.max(1, maxMin) + ")"));
            etMinutes.setText(String.valueOf(maxMin));
            etMinutes.setSelection(etMinutes.getText().length());
            box.addView(etMinutes);

            AlertDialog dialog = new AlertDialog.Builder(c)
                    .setTitle(PostCatalog.text(c, "⏱️ Accept & Offer Time", "⏱️ সময় অফার করে গ্রহণ করুন"))
                    .setView(box)
                    .setPositiveButton(PostCatalog.text(c, "Confirm Accept", "নিশ্চিত করুন"), null)
                    .setNegativeButton(AppStrings.get(c).cancel(), null)
                    .create();

            dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                String raw = etMinutes.getText().toString().trim();
                int offered = 0;
                try { offered = Integer.parseInt(raw); } catch (Exception ignored) {}
                int freshMax = RequestBidManager.getMaxAllowedOfferMinutes(a, System.currentTimeMillis());
                if (freshMax <= 0) {
                    Toast.makeText(c, PostCatalog.text(c, "Request time expired", "সময় শেষ হয়ে গেছে"), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return;
                }
                if (offered < 1 || offered > freshMax) {
                    etMinutes.setError(PostCatalog.text(c,
                            "Must be between 1 and " + freshMax + " min",
                            "১ থেকে " + freshMax + " মিনিটের মধ্যে হতে হবে"));
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                RequestBidManager.acceptWithOfferMinutes(c, a, offered,
                        () -> { dialog.dismiss(); if (done != null) done.run(); },
                        () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));
            }));
            dialog.show();
            return;
        }
        EditText reason=new EditText(c);reason.setHint(PostCatalog.text(c,"Cancellation reason (required)","বাতিলের কারণ লিখুন"));reason.setMaxLines(5);
        String title=cancelling?PostCatalog.text(c,"Cancel request?","রিকোয়েস্ট বাতিল করবেন?"):"delivered".equals(action)?PostCatalog.text(c,"Mark delivered?","ডেলিভারি দেওয়া হয়েছে?"):PostCatalog.text(c,"Confirm receipt","পণ্য গ্রহণ নিশ্চিত করুন");
        AlertDialog.Builder builder=new AlertDialog.Builder(c).setTitle(title).setMessage(a.getTitle())
            .setPositiveButton(PostCatalog.text(c,"Confirm","নিশ্চিত করুন"),null).setNegativeButton(AppStrings.get(c).cancel(),null);
        if(cancelling)builder.setView(reason);
        AlertDialog dialog=builder.create();String actionId=UUID.randomUUID().toString();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String why=reason.getText().toString().trim();
            if(cancelling && (why.length()<3||why.length()>500)){reason.setError(PostCatalog.text(c,"Use 3–500 characters","৩–৫০০ অক্ষরে কারণ লিখুন"));return;}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            String myUid = FirebaseAuth.getInstance().getUid();
            if (cancelling && myUid != null && !myUid.equals(a.getUserId())) {
                RequestBidManager.cancelSellerBid(c, a, myUid, why,
                        () -> { dialog.dismiss(); if (done != null) done.run(); },
                        () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));
            } else {
                sendAction(c,a,action,why,actionId,()->{dialog.dismiss();if(done!=null)done.run();},()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));
            }
        }));dialog.show();
    }
    public static void updateDelivery(Context c,Announcement a,String action,Runnable done) { sendAction(c,a,action,"",UUID.randomUUID().toString(),done,null); }
    private static void sendAction(Context c,Announcement a,String action,String reason,String actionId,Runnable done,Runnable failed) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(c, "Please log in first", Toast.LENGTH_SHORT).show();
            if (failed != null) failed.run();
            return;
        }

        if ("cancel".equals(action) && uid.equals(a.getUserId()) && "delivered".equals(a.getStatus())) {
            Toast.makeText(c, PostCatalog.text(c, "Cannot cancel after delivery is marked as done", "ডেলিভারি সম্পন্ন হওয়ার পর বাতিল করা যাবে না"), Toast.LENGTH_LONG).show();
            if (failed != null) failed.run();
            return;
        }

        Runnable performDirectDatabaseAction = () -> {
            FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(uid).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snap) {
                    String rawName = snap.child("name").getValue(String.class);
                    final String actorName = (rawName == null || rawName.trim().isEmpty()) ? "Announcer" : rawName.trim();
                    Double curLat = snap.child("lat").getValue(Double.class);
                    Double curLng = snap.child("lng").getValue(Double.class);
                    long now = System.currentTimeMillis();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("statusUpdatedAt", now);
                    updates.put("lastAction", action);
                    updates.put("lastActionId", actionId);
                    updates.put("lastActionByUid", uid);

                    if ("accept".equals(action)) {
                        updates.put("status", "accepted");
                        updates.put("acceptedByUid", uid);
                        updates.put("acceptedByName", actorName);
                        updates.put("acceptedAt", now);
                    } else if ("delivered".equals(action)) {
                        updates.put("status", "delivered");
                        updates.put("deliveredByUid", uid);
                        updates.put("deliveredByName", actorName);
                        updates.put("deliveredAt", now);
                    } else if ("cancel".equals(action)) {
                        updates.put("status", "cancelled");
                        updates.put("active", false);
                        updates.put("cancelledByUid", uid);
                        updates.put("cancelledByName", actorName);
                        updates.put("cancelledAt", now);
                        updates.put("cancellationReason", reason);
                    } else if ("received".equals(action)) {
                        updates.put("status", "received");
                        updates.put("active", false);
                        updates.put("receivedAt", now);
                    } else if ("notReceived".equals(action)) {
                        updates.put("status", "accepted");
                    }

                    FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(a.getId())
                        .updateChildren(updates).addOnSuccessListener(v -> {
                            if ("accept".equals(action)) {
                                a.setStatus("accepted");
                                a.setAcceptedByUid(uid);
                                a.setAcceptedByName(actorName);

                                Map<String, Object> track = new HashMap<>();
                                track.put("ownerUid", a.getUserId());
                                track.put("sellerUid", uid);
                                track.put("sellerName", actorName);
                                track.put("lat", curLat != null ? curLat : 0.0);
                                track.put("lng", curLng != null ? curLng : 0.0);
                                track.put("updatedAt", now);
                                FirebaseDatabase.getInstance().getReference("requestTracking").child(a.getId()).setValue(track);

                                NotificationSender.sendRequestEventNotification(
                                        a.getUserId(),
                                        a.getId(),
                                        "accepted",
                                        PostCatalog.text(c, "Request accepted", "রিকোয়েস্ট গ্রহণ করা হয়েছে"),
                                        a.getTitle() != null ? a.getTitle() : "Your request was accepted"
                                );

                                if (c instanceof Activity) {
                                    DeliveryLocationService.startFrom((Activity) c);
                                }

                                Intent mapIntent = new Intent(c, RequestTrackingActivity.class);
                                mapIntent.putExtra("announcementId", a.getId());
                                c.startActivity(mapIntent);
                            } else if ("delivered".equals(action)) {
                                a.setStatus("delivered");
                                a.setDeliveredByUid(uid);
                                a.setDeliveredByName(actorName);

                                NotificationSender.sendRequestEventNotification(
                                        a.getUserId(),
                                        a.getId(),
                                        "delivered",
                                        PostCatalog.text(c, "Delivered", "ডেলিভারি দেওয়া হয়েছে"),
                                        PostCatalog.text(c, "Please confirm Received: ", "পেয়েছেন কি না নিশ্চিত করুন: ") + (a.getTitle() != null ? a.getTitle() : "")
                                );
                            } else if ("cancel".equals(action)) {
                                a.setStatus("cancelled");
                                a.setActive(false);

                                String otherUid = uid.equals(a.getUserId())
                                        ? (a.getAcceptedByUid() != null && !a.getAcceptedByUid().isEmpty() ? a.getAcceptedByUid() : a.getSourceAnnouncerUid())
                                        : a.getUserId();
                                if (otherUid != null && !otherUid.isEmpty()) {
                                    NotificationSender.sendRequestEventNotification(
                                            otherUid,
                                            a.getId(),
                                            "cancelled",
                                            PostCatalog.text(c, "Request cancelled", "রিকোয়েস্ট বাতিল"),
                                            reason
                                    );
                                }
                            } else if ("received".equals(action)) {
                                a.setStatus("received");
                                a.setActive(false);

                                String sellerUid = RequestLifecycle.seller(a);
                                if (sellerUid != null && !sellerUid.isEmpty()) {
                                    NotificationSender.sendRequestEventNotification(
                                            sellerUid,
                                            a.getId(),
                                            "received",
                                            PostCatalog.text(c, "Received", "পণ্য গ্রহণ করেছেন"),
                                            a.getTitle() != null ? a.getTitle() : "Buyer confirmed received"
                                    );
                                }
                            } else if ("notReceived".equals(action)) {
                                a.setStatus("accepted");
                            }

                            if (done != null) done.run();
                        }).addOnFailureListener(err -> {
                            Toast.makeText(c, PostCatalog.text(c, "Could not update request: ", "রিকোয়েস্ট আপডেট হয়নি: ") + err.getMessage(), Toast.LENGTH_LONG).show();
                            if (failed != null) failed.run();
                        });
                }
                @Override
                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                    Toast.makeText(c, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    if (failed != null) failed.run();
                }
            });
        };

        Map<String,Object> data=new HashMap<>();data.put("annId",a.getId());data.put("action",action);data.put("reason",reason);data.put("actionId",actionId);
        try {
            BackendFunctions.get().getHttpsCallable("requestDelivery").call(data).addOnSuccessListener(r->{
                Object result=r.getData();Object post=result instanceof Map?((Map<?,?>)result).get("post"):null;
                if(post instanceof Map){Map<?,?> p=(Map<?,?>)post;a.setStatus(String.valueOf(p.get("status")));a.setActive(!Boolean.FALSE.equals(p.get("active")));a.setAcceptedByUid(p.get("acceptedByUid")==null?"":String.valueOf(p.get("acceptedByUid")));a.setAcceptedByName(p.get("acceptedByName")==null?"":String.valueOf(p.get("acceptedByName")));a.setCancellationReason(p.get("cancellationReason")==null?"":String.valueOf(p.get("cancellationReason")));}
                if("accept".equals(action) && c instanceof Activity)DeliveryLocationService.startFrom((Activity)c);
                if("accept".equals(action)) {
                    Intent mapIntent = new Intent(c, RequestTrackingActivity.class);
                    mapIntent.putExtra("announcementId", a.getId());
                    c.startActivity(mapIntent);
                }
                if(done!=null)done.run();
            }).addOnFailureListener(e->{
                // Fallback to direct Realtime Database update
                performDirectDatabaseAction.run();
            });
        } catch (Exception ex) {
            performDirectDatabaseAction.run();
        }
    }
    public static void receivedPopup(Activity c,Announcement a) {
        if(c.isFinishing()||c.isDestroyed())return;
        new AlertDialog.Builder(c).setTitle(PostCatalog.text(c,"Delivery confirmation","পণ্য গ্রহণ নিশ্চিত করুন"))
            .setMessage((a.getDeliveredByName()==null?"":a.getDeliveredByName())+"\n"+a.getTitle())
            .setPositiveButton(PostCatalog.text(c,"Received","পেয়েছি"),(d,w)->delivery(c,a,"received",null))
            .setNegativeButton(PostCatalog.text(c,"Not received","পাইনি"),(d,w)->updateDelivery(c,a,"notReceived",null))
            .setNeutralButton(PostCatalog.text(c,"Later","পরে"),null).show();
    }
    public static void broadcastSettings(Context c,Announcement a) {
        LinearLayout box = new LinearLayout(c); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(32,16,32,0);
        TextView label = new TextView(c); label.setText(PostCatalog.text(c,"Priority","অগ্রাধিকার")); box.addView(label);
        Spinner priority = new Spinner(c); priority.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item,
            new String[]{PostCatalog.text(c,"Normal","সাধারণ"),PostCatalog.text(c,"High","বেশি")})); priority.setSelection("high".equals(a.getPriority())?1:0); box.addView(priority);
        TextView repeat = new TextView(c); repeat.setText(PostCatalog.text(c,"Repeat while active","সক্রিয় থাকা অবস্থায় পুনরায় ঘোষণা")); box.addView(repeat);
        Spinner frequency = new Spinner(c); int[] minutes={0,15,30,60};
        frequency.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item, new String[]{PostCatalog.text(c,"Once","একবার"),"15 min","30 min","60 min"}));
        for(int i=0;i<minutes.length;i++) if(minutes[i]==a.getFrequencyMinutes()) frequency.setSelection(i); box.addView(frequency);
        new AlertDialog.Builder(c).setTitle(PostCatalog.text(c,"📢 Announcement settings","📢 ঘোষণার সেটিংস")).setView(box)
            .setPositiveButton(AppStrings.get(c).save(),(d,w)->{
                Map<String,Object> updates=new HashMap<>(); updates.put("priority",priority.getSelectedItemPosition()==1?"high":"normal"); updates.put("frequencyMinutes",minutes[frequency.getSelectedItemPosition()]);
                FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(a.getId()).updateChildren(updates)
                    .addOnSuccessListener(v->{a.setPriority((String)updates.get("priority"));a.setFrequencyMinutes((Integer)updates.get("frequencyMinutes"));})
                    .addOnFailureListener(e->Toast.makeText(c,e.getMessage(),Toast.LENGTH_LONG).show());
            }).setNegativeButton(AppStrings.get(c).cancel(),null).show();
    }
    private static void button(Context c,LinearLayout panel,String label,Runnable action) {
        com.google.android.material.button.MaterialButton b=new com.google.android.material.button.MaterialButton(c);
        b.setText(label);b.setAllCaps(false);b.setSingleLine(false);
        b.setTextSize(13f);
        b.setMinHeight((int)(44*c.getResources().getDisplayMetrics().density));
        b.setCornerRadius((int)(12*c.getResources().getDisplayMetrics().density));
        if (label.contains("রিকোয়েস্ট") || label.contains("Request") || label.contains("অর্ডার") || label.contains("Order") || label.contains("সময় অফার") || label.contains("Offer")) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2563EB));
            b.setTextColor(0xFFFFFFFF);
        } else if (label.contains("🗺️")) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF059669));
            b.setTextColor(0xFFFFFFFF);
        }
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = (int)(6*c.getResources().getDisplayMetrics().density);
        lp.bottomMargin = (int)(4*c.getResources().getDisplayMetrics().density);
        panel.addView(b,lp);
    }
    public static void bindActions(Context c,LinearLayout panel,Announcement a,Runnable changed) {
        panel.removeAllViews();panel.setTag(a);String uid=FirebaseAuth.getInstance().getUid();
        if(uid!=null && (!uid.equals(roleUid)||viewerRole.isEmpty()))FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(uid).child("role").get().addOnSuccessListener(row->{
            if(!uid.equals(FirebaseAuth.getInstance().getUid()))return;setViewerRole(row.getValue(String.class)==null?Constants.ROLE_USER:row.getValue(String.class));if(panel.getTag()==a)bindActions(c,panel,a,changed);
        });
        String quantities=quantities(c,a);if(!quantities.isEmpty()){TextView t=new TextView(c);t.setText(quantities);panel.addView(t);}
        if(request(a)) {
            long now = System.currentTimeMillis();
            RequestBidManager.syncExpiredBidsIfNeeded(a, now);

            TextView status=new TextView(c);status.setText(statusLabel(c,a));status.setTextSize(16);panel.addView(status);
            if(!a.getSourcePostTitle().isEmpty()){TextView t=new TextView(c);t.setText(PostCatalog.text(c,"Requested from: ","যে পোস্ট থেকে রিকোয়েস্ট: ")+a.getSourcePostTitle());panel.addView(t);}

            int bidCount = RequestBidManager.getActiveBidCount(a, now);
            int curMin = RequestBidManager.getMaxAllowedOfferMinutes(a, now);
            if (live(a) && !"delivered".equals(a.getStatus())) {
                if (bidCount > 0) {
                    TextView tvBids = new TextView(c);
                    String leader = a.getAcceptedByName() != null && !a.getAcceptedByName().isEmpty() ? a.getAcceptedByName() : "Announcer";
                    tvBids.setText(PostCatalog.text(c,
                            "🏆 Best offer: " + curMin + " min (" + leader + ") · " + bidCount + " accepted",
                            "🏆 সর্বনিম্ন সময়: " + curMin + " মিনিট (" + leader + ") · মোট " + bidCount + " জন গ্রহণ করেছে"));
                    tvBids.setTextSize(13f);
                    tvBids.setTextColor(0xFF1D4ED8);
                    tvBids.setTypeface(null, android.graphics.Typeface.BOLD);
                    panel.addView(tvBids);
                } else if (curMin > 0) {
                    TextView tvTimeLeft = new TextView(c);
                    tvTimeLeft.setText(PostCatalog.text(c,
                            "⏱️ Time limit: " + curMin + " min",
                            "⏱️ নির্ধারিত সময় বাকি: " + curMin + " মিনিট"));
                    tvTimeLeft.setTextSize(13f);
                    tvTimeLeft.setTextColor(0xFF0F766E);
                    tvTimeLeft.setTypeface(null, android.graphics.Typeface.BOLD);
                    panel.addView(tvTimeLeft);
                }
            }

            RequestBid myBid = RequestBidManager.getActiveBid(a, uid, now);
            if (myBid != null && !owns(a)) {
                long myRem = Math.max(1, (long) Math.ceil((myBid.getDeadlineAt() - now) / 60000.0));
                boolean amLeading = uid != null && uid.equals(a.getAcceptedByUid());
                TextView tvMine = new TextView(c);
                tvMine.setText(amLeading
                        ? PostCatalog.text(c, "✅ You are leading (" + myRem + " min left)", "✅ আপনি লিডিং বিক্রেতা (বাকি " + myRem + " মিনিট)")
                        : PostCatalog.text(c, "⏳ You accepted (" + myRem + " min left)", "⏳ আপনি গ্রহণ করেছেন (বাকি " + myRem + " মিনিট)"));
                tvMine.setTextSize(13f);
                tvMine.setTextColor(amLeading ? 0xFF059669 : 0xFF7C3AED);
                tvMine.setTypeface(null, android.graphics.Typeface.BOLD);
                panel.addView(tvMine);
            }

            if(RequestLifecycle.canAccept(a,uid,viewerRole,now)) {
                String acceptLabel = myBid != null
                        ? PostCatalog.text(c, "⚡ Offer Faster Time (≤ " + curMin + "m)", "⚡ আরও কম সময় অফার করুন (≤ " + curMin + " মি.)")
                        : PostCatalog.text(c, "✅ Accept & Offer Time (≤ " + curMin + "m)", "✅ রিকোয়েস্ট গ্রহণ করুন (≤ " + curMin + " মি.)");
                button(c, panel, acceptLabel, () -> delivery(c, a, "accept", changed));
            }
            if(!owns(a) && RequestLifecycle.hasActiveJob(a, uid, now)) {
                button(c, panel, PostCatalog.text(c, "🗺️ Track Accepted Paths", "🗺️ ট্র্যাক পাথ (সব অ্যাক্সেপ্ট রুট)"), () -> {
                    c.startActivity(new Intent(c, com.example.towncrierbd.activities.AnnouncerAcceptedMapActivity.class));
                });
            }
            if(RequestLifecycle.canDeliver(a,uid))button(c,panel,PostCatalog.text(c,"Delivered","ডেলিভারি দিয়েছি"),()->delivery(c,a,"delivered",changed));
            if(owns(a)&&live(a)&&"delivered".equals(a.getStatus())){
                button(c,panel,PostCatalog.text(c,"Received","পেয়েছি"),()->delivery(c,a,"received",changed));
                button(c,panel,PostCatalog.text(c,"Not received","পাইনি"),()->updateDelivery(c,a,"notReceived",changed));
            }
            if(owns(a)&&live(a)&&RequestLifecycle.inProgress(a))button(c,panel,PostCatalog.text(c,"Track seller on map","ম্যাপে বিক্রেতাকে দেখুন"),()->{Intent i=new Intent(c,RequestTrackingActivity.class);i.putExtra("announcementId",a.getId());c.startActivity(i);});
            if(owns(a)&&!chatPeer(a).isEmpty())button(c,panel,PostCatalog.text(c,"Message seller","বিক্রেতাকে মেসেজ"),()->chat(c,a));
            if(RequestLifecycle.canCancel(a,uid))button(c,panel,owns(a)?PostCatalog.text(c,"Cancel request","রিকোয়েস্ট বাতিল"):PostCatalog.text(c,"Cancel my accept","আমার অ্যাক্সেপ্ট বাতিল"),()->delivery(c,a,"cancel",changed));
            if(!a.getCancellationReason().isEmpty()){TextView t=new TextView(c);t.setText(PostCatalog.text(c,"Reason: ","কারণ: ")+a.getCancellationReason());panel.addView(t);}
        }else if(owns(a))button(c,panel,PostCatalog.text(c,"Announcement settings","ঘোষণার সেটিংস"),()->broadcastSettings(c,a));
        else if(live(a)&&!Constants.ROLE_ANNOUNCER.equals(viewerRole))button(c,panel,PostCatalog.text(c,"🛒 Order Now","🛒 অর্ডার করুন"),()->{
            Intent i=new Intent(c,AddAnnouncementActivity.class);i.putExtra("postType","request");i.putExtra(AddAnnouncementActivity.EXTRA_REQUEST_MODE,RequestMode.ACTIVE_POSTS);i.putExtra(AddAnnouncementActivity.EXTRA_SOURCE_ID,a.getId());c.startActivity(i);
        });
    }

    public static void detail(Context c,Announcement a) {
        Intent i=new Intent(c,AnnouncementDetailActivity.class); i.putExtra("announcementId",a.getId());
        i.putExtra(AnnouncementDetailActivity.EXTRA_TITLE,a.getTitle()); i.putExtra(AnnouncementDetailActivity.EXTRA_DESC,a.getDescription());
        i.putExtra(AnnouncementDetailActivity.EXTRA_CATEGORY,a.getCategory()); i.putExtra(AnnouncementDetailActivity.EXTRA_PHONE,a.getPhone());
        i.putExtra(AnnouncementDetailActivity.EXTRA_USER_NAME,a.getUserName()); i.putExtra(AnnouncementDetailActivity.EXTRA_OTHER_UID,a.getUserId());
        i.putExtra(AnnouncementDetailActivity.EXTRA_LAT,a.getLat()); i.putExtra(AnnouncementDetailActivity.EXTRA_LNG,a.getLng());
        i.putExtra(AnnouncementDetailActivity.EXTRA_IMAGE_URL,a.getImageUrl()); i.putExtra(AnnouncementDetailActivity.EXTRA_AUDIO_URL,a.getAudioUrl());
        if(a.getImageUrls()!=null)i.putStringArrayListExtra(AnnouncementDetailActivity.EXTRA_IMAGE_URLS,new ArrayList<>(a.getImageUrls()));
        c.startActivity(i);
    }
}
