import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

export function PalmistryStartScreen({ navigation }: any) {
  const { hands } = useAppState();
  const hasHands = hands.length > 0;

  return (
    <CelestialBackground>
      <View style={styles.header}>
        <Text style={styles.title}>Palmistry</Text>
        <Text style={styles.subtitle}>Scan your palm and ask questions.</Text>
      </View>

      <View style={styles.body}>
        {!hasHands ? (
          <GlassCard style={{ gap: theme.spacing(2) }}>
            <Text style={styles.h2}>Upload your palm</Text>
            <View style={styles.guideBox}>
              <Text style={styles.guideText}>Align your palm in the frame</Text>
            </View>

            <PrimaryButton title="Take Photo" onPress={() => navigation.navigate("PalmCapture")} />
            <SecondaryButton title="Upload from Gallery" onPress={() => navigation.navigate("PalmCapture")} />

            <Text style={styles.tip}>Tip: Use good light • Palm flat • No shadows</Text>
          </GlassCard>
        ) : (
          <GlassCard style={{ gap: theme.spacing(1) }}>
            <Text style={styles.h2}>Your hands</Text>
            <Text style={styles.subtitle}>Open and continue your Q&A history.</Text>
            <PrimaryButton title="Open hands" onPress={() => navigation.navigate("HandList")} />
            <SecondaryButton title="Add new hand" onPress={() => navigation.navigate("PalmCapture")} />
          </GlassCard>
        )}
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: theme.spacing(4), paddingHorizontal: theme.spacing(2) },
  title: { color: theme.colors.text, fontSize: 26, fontWeight: "900" },
  subtitle: { color: theme.colors.muted, marginTop: 6 },
  body: { padding: theme.spacing(2) },
  h2: { color: theme.colors.text, fontSize: 18, fontWeight: "900" },
  guideBox: {
    height: 260,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.10)",
    backgroundColor: "rgba(255,255,255,0.03)",
    alignItems: "center",
    justifyContent: "center",
  },
  guideText: { color: theme.colors.muted },
  tip: { color: theme.colors.muted, fontSize: 12 },
});
