import React, { useMemo } from "react";
import { View, Text, StyleSheet } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { SvgXml } from "react-native-svg";
import { useAppState } from "../state/AppState";
import { HandProfile } from "../types";

export function PalmCaptureScreen({ navigation }: any) {
  const { upsertHand } = useAppState();

  const xml = useMemo(() => (`
<svg xmlns="http://www.w3.org/2000/svg" width="360" height="520" viewBox="0 0 360 520">
  <defs>
    <style>
      .s{fill:none;stroke:rgba(232,137,10,0.55);stroke-width:3;stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:6 10}
      .t{fill:none;stroke:rgba(245,192,48,0.55);stroke-width:2;stroke-linecap:round;stroke-dasharray:2 10}
    </style>
  </defs>
  <path class="s" d="M120 420c-24-34-33-70-30-110 4-45 16-75 18-120 2-40-6-74 16-92 20-16 38 6 40 32 2 24-2 44-2 68" />
  <path class="s" d="M160 198c0-40 0-76 6-110 6-34 30-44 46-22 10 14 6 34 4 52-3 34-8 56-8 80" />
  <path class="s" d="M208 206c2-34 8-62 16-92 10-34 42-34 50-8 4 14-2 30-6 44-10 36-16 56-18 78" />
  <path class="s" d="M248 240c10-28 20-50 34-78 16-30 50-22 48 10-2 18-12 30-22 44-16 24-28 38-38 60" />
  <path class="s" d="M122 420c24 40 56 56 92 56 46 0 84-20 108-62 20-36 22-78 10-114-10-32-34-56-62-64-22-6-44 0-64 10-18 8-34 10-50 6-20-6-36-14-48-12-24 4-40 20-46 46-10 44 0 96 60 134z" />
  <path class="t" d="M70 120c40-34 90-54 140-54s100 20 140 54" />
  <path class="t" d="M58 420c44 26 86 40 122 40 44 0 84-18 124-56" />
</svg>
  `), []);

  return (
    <CelestialBackground>
      {/* Sacred header */}
      <View style={styles.header}>
        <Text style={styles.symbol}>🖐</Text>
        <Text style={styles.title}>Show Your Palm</Text>
        <Text style={styles.subtitle}>
          "Place your open hand inside the sacred guide, dear child. The Baba must see your lines clearly."
        </Text>
      </View>

      <View style={styles.body}>
        {/* Tips */}
        <View style={styles.tipsRow}>
          {["☀️ Good light", "✋ Palm flat", "🚫 No shadows"].map((t) => (
            <View key={t} style={styles.tipPill}>
              <Text style={styles.tipText}>{t}</Text>
            </View>
          ))}
        </View>

        {/* Camera mock with mystical SVG overlay */}
        <GlassCard style={{ padding: 0, overflow: "hidden" }}>
          <View style={styles.cameraMock}>
            <View style={styles.overlay}>
              <SvgXml xml={xml} width="100%" height="100%" />
            </View>
            <View style={styles.cameraNote}>
              <Text style={styles.cameraNoteText}>Camera preview</Text>
            </View>
          </View>
        </GlassCard>

        <View style={{ gap: theme.spacing(1), marginTop: theme.spacing(2) }}>
          <PrimaryButton
            title="✦  The Baba Has Seen Enough"
            onPress={async () => {
              const id = "hand-" + Math.random().toString(16).slice(2);
              const hand: HandProfile = {
                id,
                name: "My Palm",
                createdAt: Date.now(),
                lastAnalyzedAt: Date.now(),
                imageUri: "https://picsum.photos/402/602",
              };
              await upsertHand(hand);
              navigation.navigate("HandChat", { handId: id });
            }}
          />
          <SecondaryButton title="← Return" onPress={() => navigation.goBack()} />
        </View>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingTop: theme.spacing(3),
    paddingHorizontal: theme.spacing(2),
    alignItems: "center",
    paddingBottom: theme.spacing(1),
  },
  symbol: { fontSize: 32, marginBottom: 6 },
  title: { color: theme.colors.text, fontSize: 22, fontWeight: "900" },
  subtitle: {
    color: theme.colors.muted,
    marginTop: 6,
    fontSize: 13,
    fontStyle: "italic",
    textAlign: "center",
    lineHeight: 20,
  },
  body: { padding: theme.spacing(2) },
  tipsRow: { flexDirection: "row", gap: 8, marginBottom: theme.spacing(1.5), flexWrap: "wrap" },
  tipPill: {
    paddingVertical: 5,
    paddingHorizontal: 10,
    borderRadius: theme.radius.pill,
    backgroundColor: "rgba(232,137,10,0.10)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.22)",
  },
  tipText: { color: theme.colors.muted, fontSize: 11 },
  cameraMock: {
    height: 440,
    backgroundColor: "rgba(232,137,10,0.03)",
    alignItems: "center",
    justifyContent: "center",
  },
  overlay: { ...StyleSheet.absoluteFillObject, padding: 18 },
  cameraNote: {
    position: "absolute",
    bottom: 12,
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: theme.radius.pill,
    backgroundColor: "rgba(7,4,15,0.60)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.18)",
  },
  cameraNoteText: { color: theme.colors.muted, fontSize: 11 },
});
