import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

export function CreditsScreen({ navigation }: any) {
  const { credits, addCredits } = useAppState();

  return (
    <CelestialBackground>
      <View style={styles.header}>
        <Text style={styles.title}>Credits</Text>
        <Text style={styles.sub}>Use credits to unlock readings.</Text>
      </View>

      <View style={styles.body}>
        <GlassCard style={{ gap: theme.spacing(2) }}>
          <Text style={styles.big}>{credits} credits</Text>

          <PrimaryButton title="Buy 50 credits" onPress={() => addCredits(50)} />
          <PrimaryButton title="Buy 200 credits" onPress={() => addCredits(200)} style={{ backgroundColor: "rgba(46,91,255,0.80)" }} />

          <SecondaryButton title="Back" onPress={() => navigation.goBack()} />
        </GlassCard>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: theme.spacing(4), paddingHorizontal: theme.spacing(2) },
  title: { color: theme.colors.text, fontSize: 26, fontWeight: "900" },
  sub: { color: theme.colors.muted, marginTop: 6 },
  body: { padding: theme.spacing(2) },
  big: { color: theme.colors.text, fontSize: 28, fontWeight: "900" },
});
