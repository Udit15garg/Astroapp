import React, { useMemo, useState } from "react";
import { View, Text, StyleSheet, TextInput, Pressable, FlatList } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";
import { ChatMessage } from "../types";

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

    // cost per question = 1 credit (change later)
    const ok = await spendCredits(1);
    if (!ok) {
      navigation.navigate("Credits");
      return;
    }

    const userMsg: ChatMessage = { id: "u-" + Date.now(), role: "user", text: t, ts: Date.now() };
    await appendChat(handId, userMsg);
    setText("");

    // Mock assistant reply
    const assistantMsg: ChatMessage = {
      id: "a-" + Date.now(),
      role: "assistant",
      text: "Noted. (Mock) I’ll analyze your palm and answer based on common palmistry patterns.",
      ts: Date.now() + 1,
    };
    setTimeout(() => appendChat(handId, assistantMsg), 350);
  };

  return (
    <CelestialBackground>
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()}>
          <Text style={styles.back}>‹ Back</Text>
        </Pressable>

        <View style={{ flex: 1 }}>
          {editingName ? (
            <View style={styles.nameRow}>
              <TextInput
                value={nameDraft}
                onChangeText={setNameDraft}
                style={styles.nameInput}
                placeholder="Hand name"
                placeholderTextColor="rgba(255,255,255,0.35)"
              />
              <Pressable
                onPress={async () => {
                  await renameHand(handId, nameDraft.trim() || "Hand");
                  setEditingName(false);
                }}
              >
                <Text style={styles.action}>Save</Text>
              </Pressable>
            </View>
          ) : (
            <Pressable onPress={() => setEditingName(true)}>
              <Text style={styles.title}>{hand?.name ?? "Hand"}</Text>
              <Text style={styles.subtitle}>Tap to rename • 1 credit per question</Text>
            </Pressable>
          )}
        </View>
      </View>

      <FlatList
        data={history}
        keyExtractor={(m) => m.id}
        contentContainerStyle={{ padding: theme.spacing(2), gap: theme.spacing(1) }}
        renderItem={({ item }) => (
          <View style={[styles.bubble, item.role === "user" ? styles.user : styles.assistant]}>
            <Text style={styles.msg}>{item.text}</Text>
          </View>
        )}
      />

      <View style={styles.inputBar}>
        <TextInput
          value={text}
          onChangeText={setText}
          placeholder="Ask about love, career, health…"
          placeholderTextColor="rgba(255,255,255,0.35)"
          style={styles.input}
        />
        <Pressable onPress={send} style={styles.send}>
          <Text style={styles.sendTxt}>Send</Text>
        </Pressable>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: theme.spacing(4), paddingHorizontal: theme.spacing(2), flexDirection: "row", alignItems: "center", gap: theme.spacing(2) },
  back: { color: theme.colors.muted, fontWeight: "800" },
  title: { color: theme.colors.text, fontSize: 18, fontWeight: "900" },
  subtitle: { color: theme.colors.muted, marginTop: 4, fontSize: 12 },
  nameRow: { flexDirection: "row", alignItems: "center", gap: theme.spacing(1) },
  nameInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: theme.colors.cardBorder,
    borderRadius: theme.radius.btn,
    paddingVertical: 10,
    paddingHorizontal: 12,
    color: theme.colors.text,
    backgroundColor: "rgba(255,255,255,0.03)",
  },
  action: { color: theme.colors.gold, fontWeight: "900" },
  bubble: { maxWidth: "88%", borderRadius: 16, padding: 12, borderWidth: 1 },
  user: { alignSelf: "flex-end", backgroundColor: "rgba(46,91,255,0.12)", borderColor: "rgba(46,91,255,0.25)" },
  assistant: { alignSelf: "flex-start", backgroundColor: "rgba(255,255,255,0.04)", borderColor: "rgba(255,255,255,0.10)" },
  msg: { color: theme.colors.text, lineHeight: 20 },
  inputBar: {
    flexDirection: "row",
    alignItems: "center",
    gap: theme.spacing(1),
    padding: theme.spacing(2),
    borderTopWidth: 1,
    borderTopColor: theme.colors.divider,
    backgroundColor: "rgba(5,6,10,0.9)",
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: theme.colors.cardBorder,
    borderRadius: theme.radius.btn,
    paddingVertical: 10,
    paddingHorizontal: 12,
    color: theme.colors.text,
    backgroundColor: "rgba(255,255,255,0.03)",
  },
  send: {
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: theme.radius.btn,
    backgroundColor: "rgba(243,196,107,0.16)",
    borderWidth: 1,
    borderColor: "rgba(243,196,107,0.28)",
  },
  sendTxt: { color: theme.colors.text, fontWeight: "900" },
});
