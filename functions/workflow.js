"use strict";
const ANNOUNCER = "ANNOUNCER";
function distanceKm(a,b) {
  const r=n=>n*Math.PI/180, x=Math.sin(r(b.lat-a.lat)/2)**2+Math.cos(r(a.lat))*Math.cos(r(b.lat))*Math.sin(r(b.lng-a.lng)/2)**2;
  return 6371*2*Math.atan2(Math.sqrt(x),Math.sqrt(Math.max(0,1-x)));
}
const terminal=p=>["received","cancelled"].includes(p?.status);
const assignedUid=p=>p?.acceptedByUid || p?.deliveredByUid || "";
const inProgress=p=>p?.postType==="request" && ["accepted","delivered"].includes(p.status);
const live=(p,now=Date.now())=>!!p && p.active!==false && !terminal(p) && (inProgress(p) || !p.expireAt || p.expireAt>now);
const categories=p=>p.selectedCategories?.length?p.selectedCategories:[p.category].filter(Boolean);
function nearbyRecipient(p,u,uid,now=Date.now()) {
  if(!live(p,now)||!u?.fcmToken||u.notificationsEnabled===false||uid===p.userId)return false;
  if(p.postType==="request" && ((p.status||"open")!=="open" || (p.sourceAnnouncerUid && p.sourceAnnouncerUid!==uid)))return false;
  if(![p.lat,p.lng,u.lat,u.lng].every(Number.isFinite)||(p.userRole===ANNOUNCER)===(u.role===ANNOUNCER))return false;
  const d=distanceKm(p,u), radius=Number(u.notificationRadiusKm??1);
  if(radius<=0||d>radius||(p.postType==="request"&&d>Number(p.requestRadiusKm||1)))return false;
  return !u.notificationCategories?.length || categories(p).some(c=>u.notificationCategories.includes(c));
}
function transition(p,action,uid,actor,now=Date.now(),reason="",actionId="") {
  if(!p || p.postType!=="request")return {error:"Request not found"};
  if(actionId && p.lastActionId===actionId && p.lastActionByUid===uid && p.lastAction===action)return {post:p};
  const seller=assignedUid(p), owner=uid===p.userId, isSeller=actor?.role===ANNOUNCER && uid!==p.userId;
  const change=fields=>({post:{...p,...fields,workflowRevision:(p.workflowRevision||0)+1,statusUpdatedAt:now,lastAction:action,lastActionId:actionId,lastActionByUid:uid}});
  if(action==="accept") {
    if(!isSeller || (p.sourceAnnouncerUid && p.sourceAnnouncerUid!==uid))return {error:"This request belongs to another seller"};
    if(p.status==="accepted" && seller===uid && live(p,now))return {post:p};
    if(!live(p,now)||(p.status||"open")!=="open")return {error:"Request is no longer open"};
    return change({status:"accepted",acceptedByUid:uid,acceptedByName:actor.name||"",acceptedAt:now});
  }
  if(action==="delivered") {
    if(!isSeller || seller!==uid)return {error:"Only the assigned seller can mark delivery"};
    if(p.status==="delivered" && live(p,now))return {post:p};
    if(!live(p,now)||p.status!=="accepted")return {error:"Accept the request before marking delivery"};
    return change({status:"delivered",deliveredByUid:uid,deliveredByName:actor.name||"",deliveredAt:now});
  }
  if(action==="cancel") {
    if(!owner && !(isSeller && seller===uid))return {error:"Only the owner or assigned seller can cancel"};
    if(owner && p.status==="delivered")return {error:"General user cannot cancel after delivery is marked as done"};
    if(terminal(p)||p.active===false)return {error:"Request is already closed"};
    reason=String(reason).trim();
    if(reason.length<3||reason.length>500)return {error:"Enter a cancellation reason (3–500 characters)"};
    return change({status:"cancelled",active:false,cancelledByUid:uid,cancelledByName:actor?.name||"",cancelledAt:now,cancellationReason:reason});
  }
  if(!owner)return {error:"Only the request owner can confirm receipt"};
  if(action==="received" && p.status==="received")return {post:p};
  if(!live(p,now)||p.status!=="delivered")return {error:"The seller has not marked this request delivered"};
  if(action==="received")return change({status:"received",receivedAt:now,active:false});
  if(action==="notReceived")return change({status:"accepted",deliveredByUid:null,deliveredByName:null,deliveredAt:null});
  return {error:"Invalid request transition"};
}
function requestDraft(input,uid,user,source,now=Date.now()) {
  if(!user || user.role!=="General User")return {error:"Only general users can create requests"};
  const strings=v=>Array.isArray(v)&&v.length>0&&v.length<=100&&v.every(x=>typeof x==="string"&&x.trim().length>0&&x.length<=80);
  if(!strings(input.selectedProducts)||!strings(input.selectedCategories))return {error:"Select at least one item and category"};
  if(![user.lat,user.lng].every(Number.isFinite)||Math.abs(user.lat)>90||Math.abs(user.lng)>180||(user.lat===0&&user.lng===0))return {error:"Set your delivery location first"};
  if(!Number.isFinite(input.expireAt)||input.expireAt<=now||input.expireAt>now+7*86400000)return {error:"Expiry must be within seven days"};
  const products=[...new Set(input.selectedProducts)], cats=[...new Set(input.selectedCategories)], qty=input.requestedItems;
  if(!Array.isArray(qty)||qty.length!==products.length || products.some(p=>qty.filter(q=>q?.product===p&&typeof q.quantity==="string"&&q.quantity.trim().length>0&&q.quantity.length<=100).length!==1))return {error:"Enter a quantity for every item"};
  const mode=input.requestMode==="independent"?"independent":"active_posts";
  if(mode==="active_posts" && !input.sourceAnnouncementId)return {error:"Choose the seller's active post"};
  if(mode==="active_posts" && (!source || !live(source,now) || source.postType==="request" || source.userRole!==ANNOUNCER || source.userId===uid))return {error:"The selected seller post is no longer available"};
  if(mode==="active_posts" && (products.some(p=>!(source.selectedProducts||[]).includes(p)&&p!==source.customSubcategory)||cats.some(c=>!categories(source).includes(c))))return {error:"Select items offered by this seller"};
  const urls=Array.isArray(input.imageUrls)?input.imageUrls:[], audio=input.audioUrl||"";
  const media=x=>typeof x==="string" && x.length<=2048 && /^https:\/\//.test(x);
  if(urls.length>10||urls.some(x=>!media(x))||(audio&&!media(audio)))return {error:"Invalid media URL"};
  const radius=Number(input.requestRadiusKm||1);
  if(radius<0.1||radius>3)return {error:"Choose a request radius between 100 m and 3 km"};
  if(input.showPhone && !user.phone)return {error:"Add a phone number to your profile"};
  return {post:{id:input.id,userId:uid,userRole:user.role,userName:user.name||"",postType:"request",requestMode:mode,
    sourceAnnouncementId:mode==="active_posts"?input.sourceAnnouncementId:"",sourceAnnouncerUid:mode==="active_posts"?source.userId:"",sourcePostTitle:mode==="active_posts"?source.title||"":"",
    title:String(input.title||products.join(", ")).slice(0,500),description:String(input.description||"").slice(0,10000),
    category:cats[0],selectedCategories:cats,selectedProducts:products,selectedSubcategories:[],subcategory:"",customSubcategory:String(input.customSubcategory||"").slice(0,500),requestedItems:qty,
    phone:input.showPhone?user.phone:"",showPhone:!!input.showPhone,contactMethod:["chat","phone","voice"].includes(input.contactMethod)?input.contactMethod:"chat",
    imageUrls:urls,imageUrl:urls[0]||"",audioUrl:audio,lat:user.lat,lng:user.lng,time:now,expireAt:input.expireAt,requestRadiusKm:radius,active:true,status:"open",workflowRevision:0}};
}
function offerMessage(context,offer,uid,otherUid,actor,other,now=Date.now()) {
  if(!actor||!other||!context||!offer||!live(offer,now)||offer.userRole!==ANNOUNCER||offer.postType==="request"||!offer.audioUrl)return {error:"No active post with voice is available"};
  const seller=actor.role===ANNOUNCER?uid:otherUid, buyer=seller===uid?otherUid:uid;
  if(offer.userId!==seller||(actor.role===ANNOUNCER)===(other.role===ANNOUNCER))return {error:"Choose the announcer's active post"};
  if(context.postType==="request") {
    if(context.userId!==buyer || (assignedUid(context)&&assignedUid(context)!==seller) || (context.sourceAnnouncerUid&&context.sourceAnnouncerUid!==seller))return {error:"This post belongs to another conversation"};
    if(!live(context,now))return {error:"This request is closed"};
  } else if(context.userId!==seller)return {error:"The conversation must refer to this seller's post"};
  return {message:{senderId:seller,senderName:(seller===uid?actor:other).name||"",receiverId:buyer,timestamp:now,text:offer.title||"Voice offer",audioUrl:offer.audioUrl,announcementId:context.id,offerPostId:offer.id,messageType:"post_offer",requestedByUid:uid}};
}
module.exports={distanceKm,terminal,assignedUid,inProgress,live,categories,nearbyRecipient,transition,requestDraft,offerMessage};
