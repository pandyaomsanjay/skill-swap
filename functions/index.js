const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.resetFirebasePassword = functions.https.onCall(async (data, context) => {
  const { email, newPassword } = data;

  if (!email || !newPassword) {
    throw new functions.https.HttpsError("invalid-argument", "Email and newPassword required");
  }

  try {
    const userRecord = await admin.auth().getUserByEmail(email);
    await admin.auth().updateUser(userRecord.uid, { password: newPassword });
    return { success: true };
  } catch (error) {
    throw new functions.https.HttpsError("internal", error.message);
  }
});