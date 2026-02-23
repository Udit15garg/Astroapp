import React from "react";
import { View, Text, StyleSheet, Pressable, Image } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

function fmtDays(ts?: number) {
  if (!ts) return "—";
  const d = Math.max(0, Math.round((Date.now() - ts) / 86400000));
  return d === 0 ? "Today" : `${d} days ago`;
}

export function HandListScreen({ navigation }: any) {
  const { hands } = useAppState();

  return (
    <CelestialBackground>
      <View style={styles.header}>
        <Text style={styles.title}>Your hands</Text>
        <Text style={styles.subtitle}>Tap a hand to continue the Q&A.</Text>
      </View>

      <View style={styles.body}>
        <View style={{ gap: theme.spacing(2) }}>
          {hands.map((h) => (
            <Pressable key={h.id} onPress={() => navigation.navigate("HandChat", { handId: h.id })}>
              <GlassCard style={styles.handCard}>
                <Image source={{ uri: h.imageUri }} style={styles.thumb} />
                <View style={{ flex: 1, gap: 6 }}>
                  <Text style={styles.hName}>{h.name}</Text>
                  <Text style={styles.hMeta}>Last analyzed: {fmtDays(h.lastAnalyzedAt)}</Text>
                </View>
                <Text style={styles.chev}>›</Text>
              </GlassCard>
            </Pressable>
          ))}
        </View>

        <View style={{ gap: theme.spacing(1), marginTop: theme.spacing(2) }}>
          <PrimaryButton title="Add new hand" onPress={() => navigation.navigate("PalmCapture")} />
          <SecondaryButton title="Back" onPress={() => navigation.goBack()} />
        </View>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: theme.spacing(4), paddingHorizontal: theme.spacing(2) },
  title: { color: theme.colors.text, fontSize: 26, fontWeight: "900" },
  subtitle: { color: theme.colors.muted, marginTop: 6 },
  body: { padding: theme.spacing(2) },
  handCard: { flexDirection: "row", alignItems: "center", gap: theme.spacing(2) },
  thumb: { width: 54, height: 54, borderRadius: 14, backgroundColor: "rgba(255,255,255,0.06)" },
  hName: { color: theme.colors.text, fontWeight: "900", fontSize: 15 },
  hMeta: { color: theme.colors.muted, fontSize: 12 },
  chev: { color: theme.colors.muted, fontSize: 22, fontWeight: "900" },
});
