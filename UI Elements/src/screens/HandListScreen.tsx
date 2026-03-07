import React from "react";
import { View, Text, StyleSheet, Pressable, Image, ScrollView } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

function fmtDays(ts?: number) {
  if (!ts) return "Not yet read";
  const d = Math.max(0, Math.round((Date.now() - ts) / 86400000));
  return d === 0 ? "Read today" : `${d} days ago`;
}

export function HandListScreen({ navigation }: any) {
  const { hands } = useAppState();

  return (
    <CelestialBackground>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>Your Sacred Palms</Text>
          <Text style={styles.subtitle}>
            "The Baba remembers every hand that has been shown. Choose one to continue your reading."
          </Text>
        </View>

        {/* Hand cards */}
        <View style={styles.body}>
          {hands.length === 0 ? (
            <GlassCard style={styles.emptyCard}>
              <Text style={styles.emptySymbol}>🖐</Text>
              <Text style={styles.emptyTitle}>No palms yet, dear child</Text>
              <Text style={styles.emptyText}>Show the Baba your palm and the reading shall begin.</Text>
            </GlassCard>
          ) : (
            <View style={{ gap: theme.spacing(1.5) }}>
              {hands.map((h, idx) => (
                <Pressable key={h.id} onPress={() => navigation.navigate("HandChat", { handId: h.id })}>
                  <GlassCard style={styles.handCard}>
                    <Image source={{ uri: h.imageUri }} style={styles.thumb} />
                    <View style={{ flex: 1, gap: 4 }}>
                      <Text style={styles.hName}>{h.name}</Text>
                      <Text style={styles.hMeta}>🕐 {fmtDays(h.lastAnalyzedAt)}</Text>
                      <View style={styles.readingBadge}>
                        <Text style={styles.readingBadgeText}>Tap to continue reading →</Text>
                      </View>
                    </View>
                  </GlassCard>
                </Pressable>
              ))}
            </View>
          )}

          {/* Actions */}
          <View style={styles.actions}>
            <PrimaryButton title="🖐  Show Baba a New Palm" onPress={() => navigation.navigate("PalmCapture")} />
            <SecondaryButton title="← Return" onPress={() => navigation.goBack()} />
          </View>
        </View>

        <View style={{ height: theme.spacing(4) }} />
      </ScrollView>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingTop: theme.spacing(4),
    paddingHorizontal: theme.spacing(2),
    paddingBottom: theme.spacing(1),
  },
  title: { color: theme.colors.text, fontSize: 24, fontWeight: "900" },
  subtitle: {
    color: theme.colors.muted,
    fontSize: 13,
    fontStyle: "italic",
    lineHeight: 20,
    marginTop: 8,
  },
  body: { padding: theme.spacing(2), gap: theme.spacing(2) },
  emptyCard: { alignItems: "center", gap: theme.spacing(1), paddingVertical: theme.spacing(4) },
  emptySymbol: { fontSize: 48 },
  emptyTitle: { color: theme.colors.text, fontSize: 16, fontWeight: "900" },
  emptyText: { color: theme.colors.muted, fontSize: 13, textAlign: "center", lineHeight: 20 },
  handCard: { flexDirection: "row", alignItems: "center", gap: theme.spacing(2) },
  thumb: {
    width: 58,
    height: 58,
    borderRadius: 14,
    backgroundColor: "rgba(232,137,10,0.10)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.22)",
  },
  hName: { color: theme.colors.text, fontWeight: "900", fontSize: 15 },
  hMeta: { color: theme.colors.muted, fontSize: 12 },
  readingBadge: {
    alignSelf: "flex-start",
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: theme.radius.pill,
    backgroundColor: "rgba(232,137,10,0.10)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.22)",
    marginTop: 2,
  },
  readingBadgeText: { color: theme.colors.saffron, fontSize: 10, fontWeight: "700" },
  actions: { gap: theme.spacing(1), marginTop: theme.spacing(1) },
});
