import React, { useState } from "react";
import { View, Text, StyleSheet, TextInput, Pressable, FlatList } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";
import { ChatMessage } from "../types";

const MOCK_BABA_REPLIES = [
  "Hmm... the Baba studies your palm carefully. The lines here speak of a restless spirit — one who seeks but has not yet found stillness. Patience, dear child. What you search for is closer than it appears.",
  "Ah, this question touches the Baba's heart. Look at your fate line — it does not run straight, but curves. This means your path changes by choice, not by chance. You have more power than you know.",
  "The Baba sees. Your heart line runs deep — you love with great intensity. This is both your greatest strength and your tender vulnerability. Guard it wisely, dear seeker.",
  "Interesting. The mount of Jupiter on your palm is prominent — this speaks of leadership, of ambition. But the Baba also sees hesitation. What holds you back from stepping forward?",
];

let replyIdx = 0;

export function HandChatScreen({ navigation, route }: any) {
  const { handId } = route.params;
  const { hands, chats, appendChat, renameHand, spendCredits } = useAppState();
  const hand = hands.find((h) => h.id === handId);
  const history = chats[handId] ?? [];

  const [text, setText] = useState("");
  const [editingName, setEditingName] = useState(false);
  const [nameDraft, setNameDraft] = useState(hand?.name ?? "Hand");

  const send = async () => {
    const t = text.trim();
    if (!t) return;

    const ok = await spendCredits(1);
    if (!ok) {
      navigation.navigate("Credits");
      return;
    }

    const userMsg: ChatMessage = { id: "u-" + Date.now(), role: "user", text: t, ts: Date.now() };
    await appendChat(handId, userMsg);
    setText("");

    // Rotating mystical mock replies
    const babaReply = MOCK_BABA_REPLIES[replyIdx % MOCK_BABA_REPLIES.length];
    replyIdx += 1;

    const assistantMsg: ChatMessage = {
      id: "a-" + Date.now(),
      role: "assistant",
      text: babaReply,
      ts: Date.now() + 1,
    };
    setTimeout(() => appendChat(handId, assistantMsg), 800);
  };

  return (
    <CelestialBackground>
      {/* Header */}
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.back}>‹</Text>
        </Pressable>

        <View style={{ flex: 1 }}>
          {editingName ? (
            <View style={styles.nameRow}>
              <TextInput
                value={nameDraft}
                onChangeText={setNameDraft}
                style={styles.nameInput}
                placeholder="Name this palm…"
                placeholderTextColor="rgba(255,210,150,0.35)"
              />
              <Pressable
                onPress={async () => {
                  await renameHand(handId, nameDraft.trim() || "Hand");
                  setEditingName(false);
                }}
              >
                <Text style={styles.saveAction}>Save</Text>
              </Pressable>
            </View>
          ) : (
            <Pressable onPress={() => setEditingName(true)}>
              <Text style={styles.title}>{hand?.name ?? "Your Palm"}</Text>
              <Text style={styles.subtitle}>🪔 1 token per question · Tap name to rename</Text>
            </Pressable>
          )}
        </View>
      </View>

      {/* Chat history */}
      <FlatList
        data={history}
        keyExtractor={(m) => m.id}
        contentContainerStyle={styles.chatContainer}
        ListEmptyComponent={
          <View style={styles.emptyChat}>
            <Text style={styles.emptyChatSymbol}>ॐ</Text>
            <Text style={styles.emptyChatTitle}>The Baba awaits your question</Text>
            <Text style={styles.emptyChatSub}>
              Ask about love, career, health, relationships — or anything that troubles your heart.
            </Text>
          </View>
        }
        renderItem={({ item }) => (
          <View style={[styles.bubble, item.role === "user" ? styles.userBubble : styles.babaBubble]}>
            {item.role === "assistant" && (
              <Text style={styles.babaLabel}>🪔 Baba Ji</Text>
            )}
            <Text style={[styles.msg, item.role === "assistant" && styles.babaMsg]}>{item.text}</Text>
          </View>
        )}
      />

      {/* Input bar */}
      <View style={styles.inputBar}>
        <TextInput
          value={text}
          onChangeText={setText}
          placeholder="Ask the Baba about love, career, health…"
          placeholderTextColor="rgba(255,210,150,0.35)"
          style={styles.input}
          multiline
          onSubmitEditing={send}
        />
        <Pressable onPress={send} style={styles.sendBtn}>
          <Text style={styles.sendSymbol}>✦</Text>
        </Pressable>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingTop: theme.spacing(4),
    paddingHorizontal: theme.spacing(2),
    flexDirection: "row",
    alignItems: "center",
    gap: theme.spacing(1.5),
    paddingBottom: theme.spacing(1.5),
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.divider,
  },
  backBtn: { padding: 4 },
  back: { color: theme.colors.gold, fontWeight: "900", fontSize: 24 },
  title: { color: theme.colors.text, fontSize: 16, fontWeight: "900" },
  subtitle: { color: theme.colors.muted, marginTop: 3, fontSize: 11 },
  nameRow: { flexDirection: "row", alignItems: "center", gap: theme.spacing(1) },
  nameInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.30)",
    borderRadius: theme.radius.btn,
    paddingVertical: 10,
    paddingHorizontal: 12,
    color: theme.colors.text,
    backgroundColor: "rgba(232,137,10,0.05)",
  },
  saveAction: { color: theme.colors.gold, fontWeight: "900", fontSize: 14 },
  chatContainer: {
    padding: theme.spacing(2),
    gap: theme.spacing(1.5),
    flexGrow: 1,
  },
  emptyChat: {
    alignItems: "center",
    paddingTop: theme.spacing(6),
    gap: theme.spacing(1),
  },
  emptyChatSymbol: { fontSize: 44, color: theme.colors.gold, opacity: 0.6 },
  emptyChatTitle: { color: theme.colors.text, fontSize: 16, fontWeight: "800" },
  emptyChatSub: { color: theme.colors.muted, fontSize: 13, textAlign: "center", lineHeight: 20 },
  bubble: { maxWidth: "88%", borderRadius: 18, padding: 14, borderWidth: 1 },
  userBubble: {
    alignSelf: "flex-end",
    backgroundColor: "rgba(232,137,10,0.12)",
    borderColor: "rgba(232,137,10,0.28)",
  },
  babaBubble: {
    alignSelf: "flex-start",
    backgroundColor: "rgba(80,20,120,0.25)",
    borderColor: "rgba(180,100,255,0.18)",
  },
  babaLabel: { color: theme.colors.gold, fontSize: 10, fontWeight: "900", marginBottom: 6, letterSpacing: 0.5 },
  msg: { color: theme.colors.text, lineHeight: 21, fontSize: 14 },
  babaMsg: { color: "rgba(255,242,215,0.90)", fontStyle: "italic" },
  inputBar: {
    flexDirection: "row",
    alignItems: "flex-end",
    gap: theme.spacing(1),
    padding: theme.spacing(2),
    borderTopWidth: 1,
    borderTopColor: theme.colors.divider,
    backgroundColor: "rgba(7,4,15,0.95)",
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.25)",
    borderRadius: 16,
    paddingVertical: 11,
    paddingHorizontal: 14,
    color: theme.colors.text,
    backgroundColor: "rgba(232,137,10,0.05)",
    maxHeight: 100,
    lineHeight: 20,
    fontSize: 14,
  },
  sendBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: theme.colors.saffron,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: "rgba(245,192,48,0.40)",
  },
  sendSymbol: { color: "#FFF8E7", fontSize: 18, fontWeight: "900" },
});
