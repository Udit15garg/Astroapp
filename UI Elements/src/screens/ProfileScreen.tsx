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
        <View style={styles.avatarCircle}>
          <Text style={styles.avatarGlyph}>{(user?.name?.[0] ?? "?").toUpperCase()}</Text>
        </View>
        <Text style={styles.title}>{user?.name ?? "Seeker"}</Text>
        <Text style={styles.sub}>Walking the path of cosmic wisdom</Text>
      </View>

      <View style={styles.body}>
        {/* Seeker info card */}
        <GlassCard style={styles.infoCard}>
          <Text style={styles.sectionLabel}>✦ Your Sacred Profile</Text>

          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Name</Text>
            <Text style={styles.infoValue}>{user?.name ?? "—"}</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Status</Text>
            <Text style={styles.infoValueGold}>Seeker of Truth</Text>
          </View>
        </GlassCard>

        {/* Baba's farewell message */}
        <GlassCard style={styles.babaCard}>
          <Text style={styles.babaText}>
            "The Baba sees your dedication, dear child. Each question you ask brings you closer to your true self. Continue walking the path."
          </Text>
        </GlassCard>

        {/* Actions */}
        <View style={styles.actions}>
          <SecondaryButton
            title="Leave the Sacred Chamber"
            onPress={async () => {
              await logout();
              navigation.popToTop();
            }}
          />
          <SecondaryButton title="← Return" onPress={() => navigation.goBack()} />
        </View>
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingTop: theme.spacing(5),
    paddingHorizontal: theme.spacing(2),
    alignItems: "center",
    paddingBottom: theme.spacing(2),
  },
  avatarCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: "rgba(232,137,10,0.15)",
    borderWidth: 2,
    borderColor: "rgba(232,137,10,0.40)",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: theme.spacing(1.5),
  },
  avatarGlyph: { color: theme.colors.gold, fontSize: 30, fontWeight: "900" },
  title: { color: theme.colors.text, fontSize: 24, fontWeight: "900" },
  sub: { color: theme.colors.muted, fontSize: 13, marginTop: 4, fontStyle: "italic" },
  body: { padding: theme.spacing(2), gap: theme.spacing(2) },
  infoCard: { gap: theme.spacing(1.5) },
  sectionLabel: {
    color: theme.colors.gold,
    fontSize: 11,
    fontWeight: "900",
    letterSpacing: 1,
    textTransform: "uppercase",
    marginBottom: theme.spacing(0.5),
  },
  infoRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  infoLabel: { color: theme.colors.muted, fontSize: 14 },
  infoValue: { color: theme.colors.text, fontWeight: "700", fontSize: 14 },
  infoValueGold: { color: theme.colors.saffron, fontWeight: "700", fontSize: 14 },
  divider: { height: 1, backgroundColor: theme.colors.divider },
  babaCard: {
    backgroundColor: "rgba(80,20,120,0.20)",
    borderColor: "rgba(245,192,48,0.18)",
  },
  babaText: {
    color: theme.colors.muted,
    fontSize: 13,
    fontStyle: "italic",
    lineHeight: 21,
    textAlign: "center",
  },
  actions: { gap: theme.spacing(1) },
});
