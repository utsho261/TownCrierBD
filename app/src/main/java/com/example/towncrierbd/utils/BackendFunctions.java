package com.example.towncrierbd.utils;
import com.google.firebase.functions.FirebaseFunctions;
/** Only request/category/offer features use this backend. Voice is offline. */
public final class BackendFunctions {
    private BackendFunctions() {}
    public static FirebaseFunctions get() { return FirebaseFunctions.getInstance("us-central1"); }
}
