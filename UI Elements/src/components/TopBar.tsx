import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { theme } from "../theme";

export function TopBar({
  title = "Astra",
  credits,
  userInitial,
  onPressCredits,
  onPressProfile,
}: {
  title?: string;
  credits: number;
  userInitial: string;
  onPressCredits: () => void;
  onPressProfile: () => void;
}) {
  return (
    <View style={styles.row}>
      {/* Sacred brand mark */}
      <View style={styles.brand}>
        <Text style={styles.brandSymbol}>✦</Text>
        <View>
          <Text style={styles.logo}>{title}</Text>
          <Text style={styles.logoSub}>Cosmic Wisdom</Text>
        </View>
      </View>

      <View style={styles.right}>
        {/* Dakshina (credits) pill */}
        <Pressable style={styles.creditsPill} onPress={onPressCredits}>
          <Text style={styles.divaSymbol}>🪔</Text>
          <Text style={styles.creditsText}>{credits}</Text>
        </Pressable>

        {/* Seeker avatar */}
        <Pressable style={styles.avatar} onPress={onPressProfile}>
          <Text style={styles.avatarText}>{userInitial}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    paddingHorizontal: theme.spacing(2),
    paddingTop: theme.spacing(2),
    paddingBottom: theme.spacing(1),
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  brand: { flexDirection: "row", alignItems: "center", gap: 10 },
  brandSymbol: { color: theme.colors.gold, fontSize: 22 },
  logo: {
    color: theme.colors.text,
    fontSize: 20,
    fontWeight: "900",
    letterSpacing: 2,
  },
  logoSub: {
    color: theme.colors.muted,
    fontSize: 9,
    letterSpacing: 1.5,
    textTransform: "uppercase",
    marginTop: 1,
  },
  right: { flexDirection: "row", alignItems: "center", gap: theme.spacing(1) },
  creditsPill: {
    borderRadius: theme.radius.pill,
    paddingVertical: 7,
    paddingHorizontal: 12,
    backgroundColor: "rgba(232,137,10,0.12)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.30)",
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  divaSymbol: { fontSize: 13 },
  creditsText: { color: theme.colors.gold, fontWeight: "800", fontSize: 13 },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "rgba(232,137,10,0.15)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.35)",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { color: theme.colors.gold, fontWeight: "900", fontSize: 14 },
});
