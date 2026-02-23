import React, { useMemo } from "react";
import { View, StyleSheet, Text, Alert } from "react-native";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { RootStackParamList } from "../navigation/AppNavigator";
import { CelestialBackground } from "../components/CelestialBackground";
import { TopBar } from "../components/TopBar";
import { Banner } from "../components/Banner";
import { FeatureTile } from "../components/FeatureTile";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

type Props = NativeStackScreenProps<RootStackParamList, "Home">;

export function HomeScreen({ navigation }: Props) {
  const { user, credits } = useAppState();

  const features = useMemo(
    () => [
      { title: "Palmistry", glyph: "✋", subtitle: "Hand scan", route: "PalmistryStart" as const, cost: 1 },
      { title: "Tarot", glyph: "☾", subtitle: "Pick 3 cards", route: "TarotDraw" as const, cost: 1 },
      { title: "Numerology", glyph: "Ⅶ", subtitle: "Life number", route: "PalmistryStart" as const, cost: 0 },
      { title: "Kundli", glyph: "✶", subtitle: "Birth chart", route: "PalmistryStart" as const, cost: 0 },
      { title: "Rashifal", glyph: "♈", subtitle: "Daily", route: "PalmistryStart" as const, cost: 0 },
      { title: "Sun Sign", glyph: "☉", subtitle: "Traits", route: "PalmistryStart" as const, cost: 0 },
    ],
    []
  );

  const handleGate = (route: keyof RootStackParamList) => {
    if (!user) {
      navigation.navigate("Auth", { returnTo: route });
      return;
    }
    navigation.navigate(route as any);
  };

  return (
    <CelestialBackground>
      <TopBar
        title="Astra"
        credits={credits}
        userInitial={(user?.name?.[0] ?? "U").toUpperCase()}
        onPressCredits={() => handleGate("Credits")}
        onPressProfile={() => handleGate("Profile")}
      />

      <Banner
        title="Premium Reading"
        subtitle="Get a 1:1 expert interpretation →"
        onPress={() => Alert.alert("Banner", "You can route this to a paywall/upsell.")}
      />

      <View style={styles.section}>
        <Text style={styles.h1}>What would you like to know?</Text>

        <View style={styles.grid}>
          {features.map((f, idx) => (
            <View key={idx} style={{ width: "48%" }}>
              <FeatureTile
                title={f.title}
                glyph={f.glyph}
                subtitle={f.subtitle}
                onPress={() => handleGate(f.route)}
              />
            </View>
          ))}
        </View>

        <Text style={styles.disclaimer}>
          For entertainment purposes only. For important decisions, consult an expert.
        </Text>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  section: { paddingHorizontal: theme.spacing(2), paddingTop: theme.spacing(2), flex: 1 },
  h1: { color: theme.colors.text, fontSize: 22, fontWeight: "900", marginBottom: theme.spacing(2) },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: theme.spacing(2), justifyContent: "space-between" },
  disclaimer: { color: theme.colors.muted, fontSize: 12, marginTop: theme.spacing(3), textAlign: "center" },
});
