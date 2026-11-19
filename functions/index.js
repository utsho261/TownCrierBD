/* Teacher-requested announcement, delivery, chat and voice workflows. */
"use strict";
const {onValueWritten, onValueCreated} = require("firebase-functions/v2/database");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const crypto = require("node:crypto");
const {live, nearbyRecipient, transition, assignedUid, inProgress, requestDraft, offerMessage} = require("./workflow");
admin.initializeApp();
const db = admin.database();
// Must match the Realtime Database location in app/google-services.json.
const databaseRegion = process.env.DATABASE_REGION || "asia-southeast1";
const key = value => typeof value === "string" && value.length > 0 && value.length <= 200 && !/[.#$\[\]/]/.test(value);
function authenticated(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Please sign in again.");
  return request.auth.uid;
}
async function notify(uid, user, data, high = false) {
  if (!user?.fcmToken || user.notificationsEnabled === false) return;
  try {
    // Data-only messages always go through the app's session/recipient checks,
    // including when Android has put the app in the background.
    await admin.messaging().send({token: user.fcmToken,
      data: {...data, targetRole: user.role || "General User", recipientUid: uid, priority: high ? "high" : "normal"},
      android: {priority: "high", ttl: 15 * 60 * 1000}});
  } catch (e) {
    if (["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(e.code)) {
      await db.ref(`users/${uid}/fcmToken`).transaction(token => token === user.fcmToken ? null : undefined);
    } else { throw e; }
  }
}
async function broadcast(id, post) {
  if (!live(post)) return;
  const users = (await db.ref("users").get()).val() || {};
  const recipients = Object.entries(users).filter(([uid,user]) => nearbyRecipient(post,user,uid));
  // Bound concurrent requests for large neighborhoods.
  for (let start = 0; start < recipients.length; start += 50) {
    await Promise.all(recipients.slice(start,start + 50).map(([uid,user]) => notify(uid,user,{
      type: "announcement", annId: id, title: "📢 " + (post.title || "Announcement"),
      body: String(post.description || (post.selectedProducts || []).join(", ")).slice(0,180)
    },post.priority === "high")));
  }
}
exports.notifyNearbyUsers = onValueWritten({ref:"/announcements/{annId}",region:databaseRegion,retry:true}, async event => {
  const post = event.data.after.val(), before = event.data.before.val(), id = event.params.annId;
  if (post?.postType === "request") {
    await handleRequestEvent(id,before,post);
    if (!before && !post.sourceAnnouncerUid) await broadcast(id,post);
    return;
  }
  if (!live(post)) { await db.ref(`broadcasts/${id}`).remove(); return; }
  const frequency = [15,30,60].includes(Number(post.frequencyMinutes)) && post.postType === "announcement"
    ? Number(post.frequencyMinutes) : 0;
  const revision = String(post.time || 0);
  const fresh = !before || before.active === false || before.time !== post.time;
  const state = db.ref(`broadcasts/${id}`);
  const claim = await state.transaction(current => {
    if (!fresh) return {...(current || {}), frequency, nextAt: frequency ? (current?.nextAt || Date.now() + frequency * 60000) : 0};
    if (current?.revision === revision) return;
    return {revision, frequency, nextAt: frequency ? Date.now() + frequency * 60000 : 0};
  });
  if (fresh && claim.committed) await broadcast(id,post);
});
exports.repeatAnnouncements = onSchedule("every 1 minutes", async () => {
  const now = Date.now();
  const rows = (await db.ref("broadcasts").orderByChild("nextAt").startAt(1).endAt(now).get()).val() || {};
  for (const [id,state] of Object.entries(rows)) {
    const ref = db.ref(`broadcasts/${id}`);
    const claim = await ref.transaction(current => {
      if (!current?.frequency || current.nextAt > now || current.nextAt < 1) return;
      return {...current,nextAt: now + current.frequency * 60000};
    });
    if (!claim.committed) continue;
    const post = (await db.ref(`announcements/${id}`).get()).val();
    if (!live(post) || ![15,30,60].includes(Number(post.frequencyMinutes))) { await ref.remove(); continue; }
    await broadcast(id,post);
  }
});
exports.requestDelivery = onCall({region:"us-central1"}, async request => {
  const uid=authenticated(request), {annId,action,reason,actionId}=request.data||{};
  if(!key(annId)||!key(actionId)||!["accept","delivered","cancel","received","notReceived"].includes(action))throw new HttpsError("invalid-argument","Invalid request");
  const actor=(await db.ref(`users/${uid}`).get()).val();
  let error="Request changed. Please refresh.";
  const result=await db.ref(`announcements/${annId}`).transaction(post=>{
    if(!post)return null;
    const next=transition(post,action,uid,actor,Date.now(),reason,actionId);
    if(next.error){error=next.error;return;}
    return next.post;
  });
  if(!result.committed||!result.snapshot.exists())throw new HttpsError("failed-precondition",error);
  return {post:result.snapshot.val()};
});
exports.saveRequest = onCall({region:"us-central1"}, async request => {
  const uid=authenticated(request), input=request.data||{};
  if(!key(input.id)||(input.sourceAnnouncementId&&!key(input.sourceAnnouncementId)))throw new HttpsError("invalid-argument","Invalid post ID");
  const [user,source]=await Promise.all([db.ref(`users/${uid}`).get(),input.sourceAnnouncementId?db.ref(`announcements/${input.sourceAnnouncementId}`).get():Promise.resolve(null)]);
  const draft=requestDraft(input,uid,user.val(),source?.val());
  if(draft.error)throw new HttpsError("failed-precondition",draft.error);
  let error="Request changed. Reopen it before editing.";
  const result=await db.ref(`announcements/${input.id}`).transaction(current=>{
    if(!current)return input.edit?null:draft.post;
    if(current.userId!==uid||current.postType!=="request")return;
    if(!input.edit)return current; // Retry of the same create ID cannot create a duplicate.
    if((current.status||"open")!=="open"||current.active===false)return;
    return {...draft.post,workflowRevision:(current.workflowRevision||0)+1};
  });
  if(!result.committed||!result.snapshot.exists())throw new HttpsError("failed-precondition",error);
  return {id:input.id};
});
exports.requestOffer = onCall({region:"us-central1"}, async request => {
  const uid=authenticated(request), {annId,offerPostId,otherUid,actionId}=request.data||{};
  if(![annId,offerPostId,otherUid,actionId].every(key)||uid===otherUid)throw new HttpsError("invalid-argument","Choose a conversation and active post");
  const [ctx,off,actor,other]=await Promise.all([db.ref(`announcements/${annId}`).get(),db.ref(`announcements/${offerPostId}`).get(),db.ref(`users/${uid}`).get(),db.ref(`users/${otherUid}`).get()]);
  const outcome=offerMessage({...ctx.val(),id:annId},{...off.val(),id:offerPostId},uid,otherUid,actor.val(),other.val());
  if(outcome.error)throw new HttpsError("failed-precondition",outcome.error);
  const room=[uid,otherUid].sort().join("|"), id=crypto.createHash("sha256").update(uid+"|"+actionId).digest("hex");
  await db.ref(`chats/${room}/${id}`).transaction(current=>current||outcome.message);
  return {messageId:id};
});
function trackingData(post,user,previous) {
  return {ownerUid:post.userId,sellerUid:assignedUid(post),sellerName:post.acceptedByName||post.deliveredByName||"",
    lat:user.lat,lng:user.lng,updatedAt:user.locationUpdatedAt||0,accuracy:user.locationAccuracy||0,
    previousLat:previous?.lat??user.lat,previousLng:previous?.lng??user.lng,previousTime:previous?.updatedAt||0};
}
async function handleRequestEvent(id,before,post) {
  const current=(await db.ref(`announcements/${id}`).get()).val();
  if(current && Number(current.workflowRevision||0)>Number(post.workflowRevision||0))return; // Drop out-of-order lifecycle events.
  const seller=assignedUid(post), ref=db.ref(`requestTracking/${id}`);
  if(inProgress(post)&&live(post)&&seller) {
    const user=(await db.ref(`users/${seller}`).get()).val();
    if(user&&Number.isFinite(user.lat)&&Number.isFinite(user.lng))await ref.transaction(current=>current?.sellerUid===seller?current:trackingData(post,user,null));
  } else await ref.remove();
  // A late trigger never recreates tracking for a request that already closed.
  const latest=(await db.ref(`announcements/${id}`).get()).val();
  if(!live(latest)||!inProgress(latest))await ref.remove();
  if(before && before.status===post.status && before.workflowRevision===post.workflowRevision)return;
  let recipient="", title="", body="";
  if((post.status||"open")==="open" && post.sourceAnnouncerUid) {recipient=post.sourceAnnouncerUid;title="New request / নতুন রিকোয়েস্ট";body=post.title;}
  if(post.status==="accepted" && before?.status!=="accepted") {recipient=before?.status==="delivered"?seller:post.userId;title=before?.status==="delivered"?"Not received / পাওয়া যায়নি":"Request accepted / রিকোয়েস্ট গ্রহণ করা হয়েছে";body=post.title;}
  if(post.status==="delivered") {recipient=post.userId;title="Delivered / ডেলিভারি দেওয়া হয়েছে";body="Please confirm Received / পেয়েছেন কি না নিশ্চিত করুন";}
  if(post.status==="received") {recipient=seller;title="Received / পণ্য গ্রহণ করেছেন";body=post.title;}
  if(post.status==="cancelled") {
    recipient=post.cancelledByUid===post.userId?(seller||post.sourceAnnouncerUid):post.userId;
    title="Request cancelled / রিকোয়েস্ট বাতিল";body=post.cancellationReason;
    if(recipient) {
      const sender=post.cancelledByUid,room=[sender,recipient].sort().join("|"),messageId=`cancel_${id}_${post.workflowRevision||0}`;
      await db.ref(`chats/${room}/${messageId}`).transaction(current=>current||{senderId:sender,receiverId:recipient,timestamp:post.cancelledAt,announcementId:id,messageType:"request_cancelled",text:title+"\n"+body});
    }
  }
  if(recipient) {
    const eventId=`${id}_${post.workflowRevision||0}_${post.status||"open"}`;
    await db.ref(`requestNotifications/${recipient}/${eventId}`).transaction(current=>current||{annId:id,eventId,title,body:String(body||""),action:post.status||"open",timestamp:post.statusUpdatedAt||post.time||Date.now(),read:false});
  }
}
exports.notifyRequestEvent=onValueCreated({ref:"/requestNotifications/{uid}/{eventId}",region:databaseRegion,retry:true},async event=>{
  const record=event.data.val(),user=(await db.ref(`users/${event.params.uid}`).get()).val();
  await notify(event.params.uid,user,{type:"request",annId:record.annId,eventId:event.params.eventId,action:record.action,title:record.title,body:record.body},true);
});
exports.addCategory = onCall(async request => {
  const uid = authenticated(request);
  const name = String(request.data?.name || "").trim();
  const items = [...new Set((Array.isArray(request.data?.items) ? request.data.items : []).map(x => String(x).trim()).filter(Boolean))];
  if (!name || name.length > 80 || !items.length || items.length > 100 || items.some(x => x.length > 80)) throw new HttpsError("invalid-argument","Enter a category and 1–100 items (up to 80 characters each).");
  const id = crypto.createHash("sha256").update(name.toLowerCase().normalize("NFKC")).digest("hex");
  const result = await db.ref(`categories/${id}`).transaction(current => current ? undefined : {name,items,createdBy:uid});
  if (!result.committed) throw new HttpsError("already-exists","Category already exists.");
  return {name};
});
exports.notifyOnChatMessage = onValueCreated({ref:"/chats/{chatRoomId}/{messageId}",region:databaseRegion}, async event => {
  const msg = event.data.val();
  if (!msg || !key(msg.receiverId) || !key(msg.senderId) || (!msg.text && !msg.audioUrl)) return;
  if (event.params.chatRoomId !== [msg.receiverId,msg.senderId].sort().join("|")) return;
  if (key(msg.announcementId)) {
    const post = (await db.ref(`announcements/${msg.announcementId}`).get()).val();
    if (post && [msg.receiverId,msg.senderId].includes(post.userId)) {
      const marker = {otherUid:msg.receiverId,updatedAt:msg.timestamp || Date.now()};
      await db.ref().update({
        [`postInteractions/${msg.senderId}/${msg.announcementId}`]:marker,
        [`postInteractions/${msg.receiverId}/${msg.announcementId}`]:{...marker,otherUid:msg.senderId}
      });
    }
  }
  if (msg.messageType === "request_cancelled") return;
  const receiver = (await db.ref(`users/${msg.receiverId}`).get()).val();
  const sender = (await db.ref(`users/${msg.senderId}`).get()).val();
  await notify(msg.receiverId,receiver,{type:"chat",chatRoomId:event.params.chatRoomId,
    senderId:msg.senderId,senderName:sender?.name || "",annId:msg.announcementId || "",
    title:"💬 " + (sender?.name || "Message"),body:String(msg.text || "🎙 Voice offer / ভয়েস অফার").slice(0,180)});
});
// Voice runs on Android. No billable speech callable is exported.

// Share location only for the seller's active assignments. Clients cannot write tracking records.
exports.syncSellerPostLocations=onValueWritten({ref:"/users/{uid}",region:databaseRegion},async event=>{
  const user=event.data.after.val(),before=event.data.before.val(),uid=event.params.uid;
  if(!user||user.role!=="ANNOUNCER"||![user.lat,user.lng,user.locationUpdatedAt].every(Number.isFinite)||user.locationUpdatedAt===before?.locationUpdatedAt)return;
  const own=await db.ref("announcements").orderByChild("userId").equalTo(uid).get(),updates={};
  own.forEach(row=>{if(live(row.val())&&row.val().postType!=="request"){updates[`announcements/${row.key}/lat`]=user.lat;updates[`announcements/${row.key}/lng`]=user.lng;}});
  if(Object.keys(updates).length)await db.ref().update(updates);
  const jobs=await db.ref("announcements").orderByChild("acceptedByUid").equalTo(uid).get();
  for(const [id,post] of Object.entries(jobs.val()||{})) {
    if(!live(post)||!inProgress(post))continue;
    await db.ref(`requestTracking/${id}`).transaction(previous=>previous?.updatedAt>=user.locationUpdatedAt?undefined:trackingData(post,user,previous));
    const latest=(await db.ref(`announcements/${id}`).get()).val();
    if(!live(latest)||!inProgress(latest))await db.ref(`requestTracking/${id}`).remove();
  }
});
