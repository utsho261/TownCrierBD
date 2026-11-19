package com.example.towncrierbd.utils;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.*;
import com.example.towncrierbd.models.UserModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import java.util.*;

public final class NotificationSettings {
    public static void show(Context c, UserModel user){
        if(user==null)return;
        PostCatalog.load(c,()->{
            LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,16,32,16);
            TextView distance=new TextView(c);distance.setText(PostCatalog.text(c,"Notification distance","নোটিফিকেশনের দূরত্ব"));box.addView(distance);
            Spinner radius=new Spinner(c);double[] values={0.1,0.5,1,2,3};
            radius.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item,new String[]{"100 m","500 m","1 km","2 km","3 km"}));
            radius.setSelection(2);for(int i=0;i<values.length;i++)if(Math.abs(user.getNotificationRadiusKm()-values[i])<0.001)radius.setSelection(i);box.addView(radius);
            TextView categories=new TextView(c);categories.setText(PostCatalog.text(c,"Categories (select to receive notifications)","ক্যাটাগরি (নোটিফিকেশন পেতে নির্বাচন করুন)"));box.addView(categories);
            Map<CheckBox,String> selected=new LinkedHashMap<>();
            for(CategoryConfig.HawkerCategory cat:PostCatalog.sorted(c)){
                CheckBox check=new CheckBox(c);check.setText(PostCatalog.label(c,cat.name));check.setChecked(user.getNotificationCategories()!=null && user.getNotificationCategories().contains(cat.name));box.addView(check);selected.put(check,cat.name);
            }
            ScrollView scroll=new ScrollView(c);scroll.addView(box);
            AlertDialog dialog=new AlertDialog.Builder(c).setTitle(PostCatalog.text(c,"Notification settings","নোটিফিকেশন সেটিংস")).setView(scroll).setPositiveButton(AppStrings.get(c).save(),null).setNegativeButton(AppStrings.get(c).cancel(),null).create();
            dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
                String uid=FirebaseAuth.getInstance().getUid();if(uid==null)return;
                List<String> cats=new ArrayList<>();for(Map.Entry<CheckBox,String> entry:selected.entrySet())if(entry.getKey().isChecked())cats.add(entry.getValue());
                double km=values[radius.getSelectedItemPosition()];boolean configured=!cats.isEmpty();Map<String,Object> updates=new HashMap<>();updates.put("notificationRadiusKm",km);updates.put("notificationCategories",cats);updates.put("notificationConfigured",configured);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(uid).updateChildren(updates).addOnSuccessListener(r->{user.setNotificationRadiusKm(km);user.setNotificationCategories(cats);dialog.dismiss();Toast.makeText(c,configured?PostCatalog.text(c,"Notification settings saved","নোটিফিকেশন সেটিংস সংরক্ষিত হয়েছে"):PostCatalog.text(c,"No categories selected; notifications disabled","কোনো ক্যাটাগরি নির্বাচিত হয়নি; নোটিফিকেশন বন্ধ থাকবে"),Toast.LENGTH_SHORT).show();})
                    .addOnFailureListener(e->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);Toast.makeText(c,e.getMessage(),Toast.LENGTH_LONG).show();});
            }));dialog.show();
        });
    }
}
