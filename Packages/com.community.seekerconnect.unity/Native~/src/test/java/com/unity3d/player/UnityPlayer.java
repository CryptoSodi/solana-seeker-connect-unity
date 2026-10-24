package com.unity3d.player;

import java.util.ArrayList;
import java.util.List;

/** Test-only callback sink. Never included in the Android library or Unity player. */
public final class UnityPlayer {
    public static final List<String> messages = new ArrayList<>();
    public static void UnitySendMessage(String receiver, String method, String json) { messages.add(json); }
}
