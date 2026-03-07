import React from "react";
import { Text, StyleSheet, Pressable, View } from "react-native";
import { GlassCard } from "./GlassCard";
import { theme } from "../theme";

export function Banner({
  title,
  subtitle,
  onPress,
}: {
  title: string;
  subtitle: string;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={{ paddingHorizontal: theme.spacing(2) }}>
      <View style={styles.sacredBorder}>
        <GlassCard style={styles.card}>
          <View style={styles.row}>
            <Text style={styles.flame}>🔥</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.title}>{title}</Text>
              <Text style={styles.subtitle}>{subtitle}</Text>
            </View>
            <Text style={styles.arrow}>›</Text>
          </View>
        </GlassCard>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  sacredBorder: {
    borderRadius: theme.radius.card + 2,
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.35)",
  },
  card: {
    paddingVertical: theme.spacing(2),
    backgroundColor: "rgba(232,137,10,0.08)",
    borderColor: "transparent",
    borderWidth: 0,
  },
  row: { flexDirection: "row", alignItems: "center", gap: 10 },
  flame: { fontSize: 20 },
  title: { color: theme.colors.text, fontSize: 14, fontWeight: "900" },
  subtitle: { color: theme.colors.muted, fontSize: 12, marginTop: 2 },
  arrow: { color: theme.colors.gold, fontSize: 22, fontWeight: "900" },
});
