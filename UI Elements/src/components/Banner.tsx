import React from "react";
import { Text, StyleSheet, Pressable } from "react-native";
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
      <GlassCard style={styles.card}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>
      </GlassCard>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { paddingVertical: theme.spacing(2) },
  title: { color: theme.colors.text, fontSize: 14, fontWeight: "800" },
  subtitle: { color: theme.colors.muted, fontSize: 12, marginTop: 4 },
});
