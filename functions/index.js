/* eslint-disable */
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// ─────────────────────────────────────────────
// 1) নতুন Announcement হলে → nearby users notify
// ─────────────────────────────────────────────
exports.notifyNearbyUsers = functions.database
  .ref("/announcements/{annId}")
  .onCreate(async (snap, context) => {
    const ann = snap.val();
    if (!ann || !ann.lat || !ann.lng || !ann.title) return null;

    const usersSnap = await admin.database().ref("users").once("value");
    const tokens = [];

    usersSnap.forEach(child => {
      const user = child.val();
      if (!user || !user.fcmToken) return;
      if (user.uid === ann.userId) return; // নিজেকে notify করবে না

      const dist = getDistanceKm(ann.lat, ann.lng, user.lat || 0, user.lng || 0);
      if (dist <= 5.0) tokens.push(user.fcmToken); // 5km radius
    });

    if (tokens.length === 0) return null;

    const payload = {
      notification: {
        title: "📢 " + (ann.title || "New Announcement"),
        body: (ann.description || "").substring(0, 100),
      },
      data: {
        type: "announcement",
        annId: context.params.annId,
      },
      tokens: tokens,
    };

    const response = await admin.messaging().sendEachForMulticast(payload);
    console.log(`Sent: ${response.successCount}, Failed: ${response.failureCount}`);
    return null;
  });

// ─────────────────────────────────────────────
// 2) নতুন Chat Message হলে → receiver notify
// ─────────────────────────────────────────────
exports.notifyOnChatMessage = functions.database
  .ref("/chats/{chatRoomId}/{messageId}")
  .onCreate(async (snap, context) => {
    const msg = snap.val();
    if (!msg || !msg.receiverId || !msg.text) return null;

    const receiverSnap = await admin
      .database()
      .ref(`users/${msg.receiverId}`)
      .once("value");

    const receiver = receiverSnap.val();
    if (!receiver || !receiver.fcmToken) return null;

    const payload = {
      notification: {
        title: "💬 " + (msg.senderName || "Someone") + " sent you a message",
        body: (msg.text || "").substring(0, 100),
      },
      data: {
        type: "chat",
        chatRoomId: context.params.chatRoomId,
        senderName: msg.senderName || "",
        senderId: msg.senderId || "",
      },
      token: receiver.fcmToken,
    };

    try {
      await admin.messaging().send(payload);
      console.log("Chat notification sent to:", msg.receiverId);
    } catch (e) {
      console.error("Chat notification failed:", e.message);
    }
    return null;
  });

// ─────────────────────────────────────────────
// Helper: Haversine distance (km)
// ─────────────────────────────────────────────
function getDistanceKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
function toRad(deg) {
  return (deg * Math.PI) / 180;
}