import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

export function ProfileScreen({ navigation }: any) {
  const { user, logout } = useAppState();

  return (
    <CelestialBackground>
      <View style={styles.header}>
        <Text style={styles.title}>Profile</Text>
        <Text style={styles.sub}>Manage your account.</Text>
      </View>

      <View style={styles.body}>
        <GlassCard style={{ gap: theme.spacing(2) }}>
          <Text style={styles.row}><Text style={styles.label}>Name: </Text>{user?.name ?? "-"}</Text>

          <SecondaryButton
            title="Log out"
            onPress={async () => {
              await logout();
              navigation.popToTop();
            }}
          />
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
  row: { color: theme.colors.text, fontSize: 14 },
  label: { color: theme.colors.muted, fontWeight: "700" },
});
