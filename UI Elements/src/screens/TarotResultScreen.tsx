import React, { useState } from "react";
import { View, Text, StyleSheet, Pressable, ScrollView } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

const POSITION_NAMES = ["Bhoot · Your Past", "Vartaman · Your Present", "Bhavishya · Your Future"];
const POSITION_BABA = [
  "The card from your past speaks of what has shaped you, dear child.",
  "This card sits in the heart of your present moment — pay close heed.",
  "The Baba sees this card in your path ahead. Prepare yourself, seeker.",
];

export function TarotResultScreen({ route, navigation }: any) {
  const { cards } = route.params;
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const { spendCredits } = useAppState();

  return (
    <CelestialBackground>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.sacredSymbol}>🔮</Text>
          <Text style={styles.title}>The Baba Speaks</Text>
          <Text style={styles.subtitle}>
            "Listen carefully, dear child. The cards do not lie — they reveal the truth that already lives within you."
          </Text>
        </View>

        {/* Divider */}
        <View style={styles.dividerRow}>
          <View style={styles.dividerLine} />
          <Text style={styles.dividerText}>Your Sacred Reading</Text>
          <View style={styles.dividerLine} />
        </View>

        {/* Card readings */}
        <View style={styles.cardsWrap}>
          {cards.map((c: any, idx: number) => (
            <GlassCard key={c.id} style={styles.readingCard}>
              {/* Position */}
              <View style={styles.positionRow}>
                <View style={styles.positionBadge}>
                  <Text style={styles.positionBadgeText}>{idx + 1}</Text>
                </View>
                <View>
                  <Text style={styles.positionName}>{POSITION_NAMES[idx]}</Text>
                  <Text style={styles.cardName}>{c.name}</Text>
                </View>
              </View>

              {/* Baba's contextual intro */}
              <Text style={styles.babaIntro}>{POSITION_BABA[idx]}</Text>

              {/* Meaning */}
              <View style={styles.meaningBox}>
                <Text style={styles.meaningLabel}>The Card's Message</Text>
                <Text style={styles.meaningText}>{c.meaningShort}</Text>
              </View>

              {/* Expanded meaning */}
              {expanded[c.id] ? (
                <View style={styles.deeperBox}>
                  <Text style={styles.deeperLabel}>Deeper Wisdom from Baba Ji</Text>
                  <Text style={styles.deeperText}>{c.meaningLong}</Text>
                </View>
              ) : null}

              <Pressable
                style={styles.expandBtn}
                onPress={() => setExpanded((m) => ({ ...m, [c.id]: !m[c.id] }))}
              >
                <Text style={styles.expandTxt}>
                  {expanded[c.id] ? "▲  Show less" : "▼  Hear Baba's deeper wisdom"}
                </Text>
              </Pressable>
            </GlassCard>
          ))}
        </View>

        {/* Summary card */}
        <View style={styles.summaryWrap}>
          <GlassCard style={styles.summaryCard}>
            <Text style={styles.summaryTitle}>✦ The Baba's Closing Words</Text>
            <Text style={styles.summaryText}>
              "These three cards together tell the story of your soul's journey. Sit with this wisdom, dear child. Do not rush — let it settle like incense smoke."
            </Text>
          </GlassCard>
        </View>

        {/* Actions */}
        <View style={styles.actions}>
          <PrimaryButton
            title="🪔  Ask the Baba a Question"
            onPress={async () => {
              const ok = await spendCredits(1);
              if (!ok) {
                navigation.navigate("Credits");
                return;
              }
              navigation.navigate("Home");
            }}
          />
          <SecondaryButton title="← Return to the Baba's Chamber" onPress={() => navigation.goBack()} />
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
    paddingBottom: theme.spacing(1),
  },
  sacredSymbol: { fontSize: 40, marginBottom: 8 },
  title: { color: theme.colors.text, fontSize: 26, fontWeight: "900" },
  subtitle: {
    color: theme.colors.muted,
    fontSize: 13,
    fontStyle: "italic",
    marginTop: 10,
    textAlign: "center",
    lineHeight: 20,
    paddingHorizontal: theme.spacing(1),
  },
  dividerRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: theme.spacing(2),
    marginVertical: theme.spacing(2),
    gap: theme.spacing(1),
  },
  dividerLine: { flex: 1, height: 1, backgroundColor: "rgba(232,137,10,0.20)" },
  dividerText: { color: theme.colors.muted, fontSize: 10, fontWeight: "800", letterSpacing: 1.2, textTransform: "uppercase" },
  cardsWrap: { paddingHorizontal: theme.spacing(2), gap: theme.spacing(2) },
  readingCard: { gap: theme.spacing(1.5) },
  positionRow: { flexDirection: "row", alignItems: "flex-start", gap: theme.spacing(1.5) },
  positionBadge: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: "rgba(232,137,10,0.15)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.35)",
    alignItems: "center",
    justifyContent: "center",
  },
  positionBadgeText: { color: theme.colors.gold, fontWeight: "900", fontSize: 14 },
  positionName: { color: theme.colors.muted, fontSize: 11, letterSpacing: 0.5 },
  cardName: { color: theme.colors.gold, fontWeight: "900", fontSize: 16, marginTop: 1 },
  babaIntro: { color: theme.colors.muted, fontSize: 12, fontStyle: "italic", lineHeight: 18 },
  meaningBox: {
    backgroundColor: "rgba(232,137,10,0.06)",
    borderRadius: 12,
    padding: theme.spacing(1.5),
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.15)",
  },
  meaningLabel: { color: theme.colors.saffron, fontSize: 10, fontWeight: "900", letterSpacing: 0.8, textTransform: "uppercase", marginBottom: 4 },
  meaningText: { color: theme.colors.text, fontSize: 14, lineHeight: 21 },
  deeperBox: {
    backgroundColor: "rgba(80,20,120,0.15)",
    borderRadius: 12,
    padding: theme.spacing(1.5),
    borderWidth: 1,
    borderColor: "rgba(180,100,255,0.15)",
  },
  deeperLabel: { color: "rgba(200,150,255,0.80)", fontSize: 10, fontWeight: "900", letterSpacing: 0.8, textTransform: "uppercase", marginBottom: 4 },
  deeperText: { color: theme.colors.muted, fontSize: 13, lineHeight: 20 },
  expandBtn: { paddingVertical: 4 },
  expandTxt: { color: theme.colors.gold, fontWeight: "800", fontSize: 12 },
  summaryWrap: { paddingHorizontal: theme.spacing(2), marginTop: theme.spacing(2) },
  summaryCard: { gap: theme.spacing(1), backgroundColor: "rgba(80,20,120,0.20)", borderColor: "rgba(245,192,48,0.20)" },
  summaryTitle: { color: theme.colors.gold, fontSize: 12, fontWeight: "900", letterSpacing: 0.8, textTransform: "uppercase" },
  summaryText: { color: theme.colors.muted, fontSize: 13, fontStyle: "italic", lineHeight: 20 },
  actions: { paddingHorizontal: theme.spacing(2), marginTop: theme.spacing(2), gap: theme.spacing(1) },
});
