import React, { useState } from "react";
import { View, Text, StyleSheet, Pressable, ScrollView } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

export function TarotResultScreen({ route, navigation }: any) {
  const { cards } = route.params;
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const { spendCredits } = useAppState();

  return (
    <CelestialBackground>
      <View style={styles.header}>
        <Text style={styles.title}>Your reading</Text>
        <Text style={styles.subtitle}>Tap “Show more” for deeper meaning.</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: theme.spacing(2), gap: theme.spacing(2) }}>
        {cards.map((c: any, idx: number) => (
          <GlassCard key={c.id} style={{ gap: 8 }}>
            <Text style={styles.cardLabel}>
              {idx === 0 ? "Past" : idx === 1 ? "Present" : "Future"} • {c.name}
            </Text>
            <Text style={styles.body}>{c.meaningShort}</Text>
            {expanded[c.id] ? <Text style={styles.bodyMuted}>{c.meaningLong}</Text> : null}

            <Pressable onPress={() => setExpanded((m) => ({ ...m, [c.id]: !m[c.id] }))}>
              <Text style={styles.showMore}>{expanded[c.id] ? "Show less" : "Show more"}</Text>
            </Pressable>
          </GlassCard>
        ))}

        <PrimaryButton
          title="Ask a question (1 credit)"
          onPress={async () => {
            const ok = await spendCredits(1);
            if (!ok) {
              navigation.navigate("Credits");
              return;
            }
            navigation.navigate("Home"); // placeholder for a Tarot Q&A screen
          }}
        />

        <SecondaryButton title="Back" onPress={() => navigation.goBack()} />
      </ScrollView>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: theme.spacing(4), paddingHorizontal: theme.spacing(2) },
  title: { color: theme.colors.text, fontSize: 26, fontWeight: "900" },
  subtitle: { color: theme.colors.muted, marginTop: 6 },
  cardLabel: { color: theme.colors.text, fontWeight: "900" },
  body: { color: theme.colors.text, fontSize: 14, lineHeight: 20 },
  bodyMuted: { color: theme.colors.muted, fontSize: 13, lineHeight: 19 },
  showMore: { color: theme.colors.gold, fontWeight: "900", marginTop: 6 },
});
