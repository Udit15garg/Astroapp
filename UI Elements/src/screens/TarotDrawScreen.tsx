import React, { useMemo, useState } from "react";
import { View, Text, StyleSheet, Alert } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { theme } from "../theme";
import { TAROT_DECK } from "../services/mockTarot";
import { TarotFlipCard } from "../components/TarotFlipCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { useAppState } from "../state/AppState";

export function TarotDrawScreen({ navigation }: any) {
  const { spendCredits } = useAppState();
  const [picked, setPicked] = useState<string[]>([]);

  const cards = useMemo(() => picked.map((id) => TAROT_DECK.find((c) => c.id === id)!).filter(Boolean), [picked]);

  const pickCard = async () => {
    if (picked.length >= 3) return;

    // charge 1 credit to start a draw, only when first card is picked
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

  return (
    <CelestialBackground>
      <View style={styles.header}>
        <Text style={styles.title}>Pick 3 cards</Text>
        <Text style={styles.subtitle}>Tap to reveal — Past, Present, Future</Text>
      </View>

      <View style={styles.row}>
        {[0, 1, 2].map((i) => {
          const card = cards[i];
          const revealed = Boolean(card);
          return (
            <TarotFlipCard
              key={i}
              index={i}
              revealed={revealed}
              titleTop={revealed ? card.name : ""}
              titleBottom={revealed ? card.meaningShort : ""}
              onPress={pickCard}
            />
          );
        })}
      </View>

      <View style={styles.actions}>
        <PrimaryButton
          title="See reading"
          disabled={cards.length !== 3}
          onPress={() => navigation.navigate("TarotResult", { cards })}
        />
        <SecondaryButton title="Redo cards" onPress={() => setPicked([])} />
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: theme.spacing(4), paddingHorizontal: theme.spacing(2) },
  title: { color: theme.colors.text, fontSize: 26, fontWeight: "900" },
  subtitle: { color: theme.colors.muted, marginTop: 6 },
  row: { flexDirection: "row", justifyContent: "space-between", paddingHorizontal: theme.spacing(2), marginTop: theme.spacing(3) },
  actions: { paddingHorizontal: theme.spacing(2), marginTop: theme.spacing(3), gap: theme.spacing(1) },
});
