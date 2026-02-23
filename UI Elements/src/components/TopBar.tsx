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
      <Text style={styles.logo}>{title}</Text>

      <View style={styles.right}>
        <Pressable style={styles.creditsPill} onPress={onPressCredits}>
          <Text style={styles.creditsText}>{credits} credits</Text>
        </Pressable>

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
  logo: { color: theme.colors.text, fontSize: 18, fontWeight: "800", letterSpacing: 0.2 },
  right: { flexDirection: "row", alignItems: "center", gap: theme.spacing(1) },
  creditsPill: {
    borderRadius: theme.radius.pill,
    paddingVertical: 8,
    paddingHorizontal: 12,
    backgroundColor: "rgba(255,255,255,0.04)",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.10)",
  },
  creditsText: { color: theme.colors.text, fontWeight: "700", fontSize: 13 },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "rgba(255,255,255,0.04)",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.10)",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { color: theme.colors.text, fontWeight: "800" },
});
