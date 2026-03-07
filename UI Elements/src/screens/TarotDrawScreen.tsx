import React, { useMemo, useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { theme } from "../theme";
import { TAROT_DECK } from "../services/mockTarot";
import { TarotFlipCard } from "../components/TarotFlipCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { GlassCard } from "../components/GlassCard";
import { useAppState } from "../state/AppState";

const WHAT_TAROT_REVEALS = [
  "💕  Love — Is someone thinking of you? What does the heart seek?",
  "🌟  Purpose — What is your soul truly meant to do in this life?",
  "⚠️  Warnings — What hidden force is working against you right now?",
  "🌅  Guidance — The cards show which path will bring you peace.",
];

export function TarotDrawScreen({ navigation }: any) {
  const { spendCredits } = useAppState();
  const [picked, setPicked] = useState<string[]>([]);

  const cards = useMemo(() => picked.map((id) => TAROT_DECK.find((c) => c.id === id)!).filter(Boolean), [picked]);

  const pickCard = async () => {
    if (picked.length >= 3) return;

    if (picked.length === 0) {
      const ok = await spendCredits(1);
      if (!ok) {
        navigation.navigate("Credits");
        return;
      }
    }

    const remaining = TAROT_DECK.filter((c) => !picked.includes(c.id));
    const random = remaining[Math.floor(Math.random() * remaining.length)];
    setPicked((p) => [...p, random.id]);
  };

  const cardCount = cards.length;

  return (
    <CelestialBackground>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.sacredSymbol}>☾</Text>
          <Text style={styles.title}>Tarot Darshan</Text>
          <Text style={styles.titleSub}>Sacred Card Reading</Text>
        </View>

        {/* Baba's invocation */}
        <View style={styles.invocationWrap}>
          <GlassCard style={styles.invocation}>
            <Text style={styles.invocationText}>
              "Close your eyes, dear child. Take a slow breath. Let the noise of the world fall away. When your heart is still... let it guide your hand to the cards."
            </Text>
            <Text style={styles.invocationAttrib}>— Baba Ji</Text>
          </GlassCard>
        </View>

        {/* Progress indicator */}
        <View style={styles.progressWrap}>
          <Text style={styles.progressText}>
            {cardCount === 0
              ? "Touch any card to begin your reading"
              : cardCount < 3
              ? `${cardCount} of 3 cards revealed — touch another`
              : "All three cards are revealed, dear seeker"}
          </Text>
        </View>

        {/* Card row */}
        <View style={styles.row}>
          {[0, 1, 2].map((i) => {
            const card = cards[i];
            const revealed = Boolean(card);
            return (
              <TarotFlipCard
                key={`${i}-${cards[i]?.id ?? "back"}`}
                index={i}
                revealed={revealed}
                titleTop={revealed ? card.name : ""}
                titleBottom={revealed ? card.meaningShort : ""}
                onPress={pickCard}
              />
            );
          })}
        </View>

        {/* What Tarot reveals (shown before cards are drawn) */}
        {cardCount === 0 && (
          <View style={styles.revealsWrap}>
            <Text style={styles.revealsTitle}>✦ What These Cards Can Reveal</Text>
            {WHAT_TAROT_REVEALS.map((r, i) => (
              <Text key={i} style={styles.revealsItem}>{r}</Text>
            ))}
          </View>
        )}

        {/* Action buttons */}
        <View style={styles.actions}>
          <PrimaryButton
            title={cardCount === 3 ? "🔮  Receive the Baba's Reading" : `Reveal Card ${cardCount + 1} of 3`}
            disabled={false}
            onPress={cardCount === 3 ? () => navigation.navigate("TarotResult", { cards }) : pickCard}
          />
          {cardCount > 0 && (
            <SecondaryButton title="Reshuffle the Deck" onPress={() => setPicked([])} />
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
    paddingBottom: theme.spacing(1),
  },
  sacredSymbol: { fontSize: 36, color: theme.colors.gold, marginBottom: 6 },
  title: { color: theme.colors.text, fontSize: 26, fontWeight: "900", letterSpacing: 0.5 },
  titleSub: { color: theme.colors.saffron, fontSize: 12, marginTop: 4, letterSpacing: 0.5 },
  invocationWrap: { paddingHorizontal: theme.spacing(2), marginTop: theme.spacing(2) },
  invocation: { backgroundColor: "rgba(80,20,120,0.25)", borderColor: "rgba(245,192,48,0.20)" },
  invocationText: {
    color: theme.colors.muted,
    fontSize: 13,
    fontStyle: "italic",
    lineHeight: 22,
    textAlign: "center",
  },
  invocationAttrib: {
    color: theme.colors.gold,
    fontSize: 11,
    fontWeight: "800",
    textAlign: "right",
    marginTop: 8,
  },
  progressWrap: { paddingHorizontal: theme.spacing(2), marginTop: theme.spacing(2), alignItems: "center" },
  progressText: {
    color: theme.colors.muted,
    fontSize: 12,
    fontStyle: "italic",
    textAlign: "center",
  },
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingHorizontal: theme.spacing(2),
    marginTop: theme.spacing(2),
  },
  revealsWrap: {
    margin: theme.spacing(2),
    padding: theme.spacing(2),
    backgroundColor: "rgba(232,137,10,0.06)",
    borderRadius: theme.radius.card,
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.15)",
    gap: 8,
  },
  revealsTitle: {
    color: theme.colors.gold,
    fontSize: 11,
    fontWeight: "900",
    letterSpacing: 1,
    textTransform: "uppercase",
    marginBottom: 4,
  },
  revealsItem: { color: theme.colors.muted, fontSize: 13, lineHeight: 20 },
  actions: { paddingHorizontal: theme.spacing(2), marginTop: theme.spacing(3), gap: theme.spacing(1) },
});
