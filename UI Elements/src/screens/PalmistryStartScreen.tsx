import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

const REVEALS = [
  { icon: "❤️", label: "Love & Relationships", desc: "Is your destined partner near? What does your heart line say?" },
  { icon: "💼", label: "Career & Wealth", desc: "The fate line reveals your professional journey and fortune." },
  { icon: "🌿", label: "Health & Vitality", desc: "The life line speaks of your body's strength and longevity." },
  { icon: "🧠", label: "Mind & Intellect", desc: "Your head line shows how your mind processes the world." },
];

export function PalmistryStartScreen({ navigation }: any) {
  const { hands } = useAppState();
  const hasHands = hands.length > 0;

  return (
    <CelestialBackground>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Sacred header */}
        <View style={styles.header}>
          <Text style={styles.sacredSymbol}>🖐</Text>
          <Text style={styles.title}>Hast Rekha</Text>
          <Text style={styles.titleSub}>The Ancient Art of Palm Reading</Text>
          <Text style={styles.babaWords}>
            "Child, your hands carry the map of your destiny — written by the stars at the very moment of your birth. Let the Baba read what the universe has inscribed."
          </Text>
        </View>

        {/* What it reveals */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>✦ What Your Palm Reveals</Text>
          <View style={styles.revealsGrid}>
            {REVEALS.map((r, i) => (
              <GlassCard key={i} style={styles.revealCard}>
                <Text style={styles.revealIcon}>{r.icon}</Text>
                <Text style={styles.revealLabel}>{r.label}</Text>
                <Text style={styles.revealDesc}>{r.desc}</Text>
              </GlassCard>
            ))}
          </View>
        </View>

        {/* How it works */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>✦ How It Works</Text>
          <GlassCard>
            <View style={styles.stepsList}>
              {[
                { n: "1", t: "Show the Baba your palm", d: "Take a clear photo of your dominant hand in good light." },
                { n: "2", t: "The Baba studies your lines", d: "Ancient palmistry patterns are read and interpreted." },
                { n: "3", t: "Ask any question", d: "Type what troubles your heart — love, career, health, or life." },
              ].map((s) => (
                <View key={s.n} style={styles.step}>
                  <View style={styles.stepNum}>
                    <Text style={styles.stepNumText}>{s.n}</Text>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.stepTitle}>{s.t}</Text>
                    <Text style={styles.stepDesc}>{s.d}</Text>
                  </View>
                </View>
              ))}
            </View>
          </GlassCard>
        </View>

        {/* Action card */}
        <View style={styles.section}>
          {!hasHands ? (
            <GlassCard style={{ gap: theme.spacing(1.5) }}>
              <Text style={styles.actionTitle}>Begin Your Reading</Text>
              <Text style={styles.actionSub}>
                Show the Baba your open palm. Use good light and keep the hand flat.
              </Text>

              {/* Palm guide tip */}
              <View style={styles.tipRow}>
                {["☀️ Good light", "✋ Palm flat", "🚫 No shadows"].map((t) => (
                  <View key={t} style={styles.tipPill}>
                    <Text style={styles.tipText}>{t}</Text>
                  </View>
                ))}
              </View>

              <PrimaryButton title="📷  Take Photo of Palm" onPress={() => navigation.navigate("PalmCapture")} />
              <SecondaryButton title="Upload from Gallery" onPress={() => navigation.navigate("PalmCapture")} />
            </GlassCard>
          ) : (
            <GlassCard style={{ gap: theme.spacing(1.5) }}>
              <Text style={styles.actionTitle}>Welcome Back, Seeker</Text>
              <Text style={styles.actionSub}>
                The Baba remembers your palm. Shall we continue where we left off?
              </Text>
              <PrimaryButton title="Continue Reading" onPress={() => navigation.navigate("HandList")} />
              <SecondaryButton title="Add Another Hand" onPress={() => navigation.navigate("PalmCapture")} />
            </GlassCard>
          )}
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
    alignItems: "center",
    paddingBottom: theme.spacing(2),
  },
  sacredSymbol: { fontSize: 48, marginBottom: theme.spacing(1) },
  title: { color: theme.colors.text, fontSize: 28, fontWeight: "900", letterSpacing: 0.5 },
  titleSub: { color: theme.colors.saffron, fontSize: 13, marginTop: 4, letterSpacing: 0.5 },
  babaWords: {
    color: theme.colors.muted,
    fontSize: 13,
    fontStyle: "italic",
    textAlign: "center",
    marginTop: theme.spacing(2),
    lineHeight: 22,
    paddingHorizontal: theme.spacing(1),
    borderLeftWidth: 2,
    borderLeftColor: "rgba(232,137,10,0.35)",
    paddingLeft: theme.spacing(1.5),
  },
  section: {
    paddingHorizontal: theme.spacing(2),
    marginBottom: theme.spacing(2),
  },
  sectionTitle: {
    color: theme.colors.gold,
    fontSize: 12,
    fontWeight: "900",
    letterSpacing: 1.2,
    textTransform: "uppercase",
    marginBottom: theme.spacing(1.5),
  },
  revealsGrid: { gap: theme.spacing(1) },
  revealCard: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: theme.spacing(1.5),
    paddingVertical: theme.spacing(1.5),
  },
  revealIcon: { fontSize: 22, marginTop: 1 },
  revealLabel: { color: theme.colors.text, fontWeight: "800", fontSize: 14, marginBottom: 3 },
  revealDesc: { color: theme.colors.muted, fontSize: 12, lineHeight: 18 },
  stepsList: { gap: theme.spacing(2) },
  step: { flexDirection: "row", gap: theme.spacing(1.5), alignItems: "flex-start" },
  stepNum: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: "rgba(232,137,10,0.15)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.35)",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  stepNumText: { color: theme.colors.gold, fontWeight: "900", fontSize: 13 },
  stepTitle: { color: theme.colors.text, fontWeight: "800", fontSize: 14 },
  stepDesc: { color: theme.colors.muted, fontSize: 12, lineHeight: 18, marginTop: 2 },
  actionTitle: { color: theme.colors.text, fontSize: 18, fontWeight: "900" },
  actionSub: { color: theme.colors.muted, fontSize: 13, lineHeight: 20 },
  tipRow: { flexDirection: "row", gap: 8, flexWrap: "wrap", marginBottom: 4 },
  tipPill: {
    paddingVertical: 5,
    paddingHorizontal: 10,
    borderRadius: theme.radius.pill,
    backgroundColor: "rgba(232,137,10,0.10)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.22)",
  },
  tipText: { color: theme.colors.muted, fontSize: 11 },
});
