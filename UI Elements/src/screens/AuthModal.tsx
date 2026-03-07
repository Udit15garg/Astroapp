import React, { useState } from "react";
import { View, Text, StyleSheet, TextInput } from "react-native";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { RootStackParamList } from "../navigation/AppNavigator";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

type Props = NativeStackScreenProps<RootStackParamList, "Auth">;

export function AuthModal({ navigation, route }: Props) {
  const { login } = useAppState();
  const [name, setName] = useState("");

  const returnTo = route.params?.returnTo;

  return (
    <CelestialBackground>
      <View style={styles.wrap}>
        <GlassCard style={styles.card}>
          {/* Sacred symbol */}
          <View style={styles.symbolWrap}>
            <Text style={styles.symbol}>ॐ</Text>
          </View>

          <Text style={styles.title}>Enter, Dear Seeker</Text>
          <Text style={styles.sub}>
            "Before the Baba can read your path, you must first introduce yourself. Who seeks this ancient wisdom?"
          </Text>

          <TextInput
            value={name}
            onChangeText={setName}
            placeholder="Your sacred name, child…"
            placeholderTextColor="rgba(255,210,150,0.35)"
            style={styles.input}
            autoCapitalize="words"
          />

          <PrimaryButton
            title="🪔  Enter the Sacred Chamber"
            onPress={async () => {
              await login(name.trim() || "Dear Seeker");
              navigation.goBack();
              if (returnTo) navigation.navigate(returnTo as any);
            }}
          />

          <SecondaryButton title="Not yet — I shall return" onPress={() => navigation.goBack()} />

          <Text style={styles.small}>
            By entering, you acknowledge that the Baba's wisdom is for spiritual guidance and reflection only.
          </Text>
        </GlassCard>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, alignItems: "center", justifyContent: "center", padding: theme.spacing(2) },
  card: { width: "100%", maxWidth: 420, gap: theme.spacing(1.5) },
  symbolWrap: { alignItems: "center", marginBottom: theme.spacing(0.5) },
  symbol: { fontSize: 40, color: theme.colors.gold, opacity: 0.90 },
  title: { color: theme.colors.text, fontSize: 22, fontWeight: "900", textAlign: "center" },
  sub: {
    color: theme.colors.muted,
    fontSize: 13,
    textAlign: "center",
    fontStyle: "italic",
    lineHeight: 20,
    marginBottom: theme.spacing(0.5),
  },
  input: {
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.30)",
    borderRadius: theme.radius.btn,
    paddingVertical: 14,
    paddingHorizontal: 14,
    color: theme.colors.text,
    backgroundColor: "rgba(232,137,10,0.05)",
    fontSize: 15,
    marginBottom: theme.spacing(0.5),
  },
  small: {
    color: "rgba(255,210,150,0.40)",
    fontSize: 11,
    marginTop: theme.spacing(0.5),
    textAlign: "center",
    lineHeight: 16,
  },
});
