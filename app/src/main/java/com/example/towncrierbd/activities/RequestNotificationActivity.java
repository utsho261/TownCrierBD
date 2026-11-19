package com.example.towncrierbd.activities;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
/** Resolve a notification's exact post before opening the existing detail screen. */
public class RequestNotificationActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,48,24,24);
        TextView label=new TextView(this);label.setText(PostCatalog.text(this,"Loading request…","রিকোয়েস্ট লোড হচ্ছে…"));box.addView(label);
        Button retry=new Button(this);retry.setText(PostCatalog.text(this,"Retry","আবার চেষ্টা করুন"));box.addView(retry);retry.setOnClickListener(v->load(label));setContentView(box);load(label);
    }
    private void load(TextView label){
        String uid=FirebaseAuth.getInstance().getUid(),id=getIntent().getStringExtra("announcementId"),recipient=getIntent().getStringExtra("recipientUid");
        if(uid==null||(recipient!=null&&!uid.equals(recipient))||id==null||id.isEmpty()){finish();return;}
        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(id).get().addOnSuccessListener(row->{
            if(isFinishing()||isDestroyed()||!uid.equals(FirebaseAuth.getInstance().getUid()))return;
            Announcement post=row.getValue(Announcement.class);if(post==null){label.setText(PostCatalog.text(this,"This post is no longer available.","পোস্টটি আর পাওয়া যাচ্ছে না।"));return;}
            String event=getIntent().getStringExtra("eventId");if(event!=null&&!event.isEmpty())FirebaseDatabase.getInstance().getReference("requestNotifications").child(uid).child(event).child("read").setValue(true);
            post.setId(id);PostWorkflow.detail(this,post);finish();
        }).addOnFailureListener(e->label.setText(PostCatalog.text(this,"Could not load request. Check your connection and retry.","রিকোয়েস্ট লোড হয়নি। সংযোগ দেখে আবার চেষ্টা করুন।")));
    }
}
