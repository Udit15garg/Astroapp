import React, { useState } from "react";
import { View, Text, StyleSheet, TextInput, Pressable } from "react-native";
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
          <Text style={styles.title}>Sign in</Text>
          <Text style={styles.sub}>Create an account to continue.</Text>

          <TextInput
            value={name}
            onChangeText={setName}
            placeholder="Your name"
            placeholderTextColor="rgba(255,255,255,0.35)"
            style={styles.input}
          />

          <PrimaryButton
            title="Continue"
            onPress={async () => {
              await login(name || "Udit");
              navigation.goBack();
              if (returnTo) navigation.navigate(returnTo as any);
            }}
          />

          <SecondaryButton title="Not now" onPress={() => navigation.goBack()} />

          <Text style={styles.small}>
            By continuing, you agree this app is for entertainment purposes only.
          </Text>
        </GlassCard>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, alignItems: "center", justifyContent: "center", padding: theme.spacing(2) },
  card: { width: "100%", maxWidth: 420, gap: theme.spacing(1) },
  title: { color: theme.colors.text, fontSize: 20, fontWeight: "900" },
  sub: { color: theme.colors.muted, marginBottom: theme.spacing(1) },
  input: {
    borderWidth: 1,
    borderColor: theme.colors.cardBorder,
    borderRadius: theme.radius.btn,
    paddingVertical: 12,
    paddingHorizontal: 12,
    color: theme.colors.text,
    backgroundColor: "rgba(255,255,255,0.03)",
    marginBottom: theme.spacing(1),
  },
  small: { color: theme.colors.muted, fontSize: 12, marginTop: theme.spacing(1) },
});
