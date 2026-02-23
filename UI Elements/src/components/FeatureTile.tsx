import React from "react";
import { Text, StyleSheet, Pressable, View } from "react-native";
import { GlassCard } from "./GlassCard";
import { theme } from "../theme";

export function FeatureTile({
  title,
  glyph,
  subtitle,
  onPress,
}: {
  title: string;
  glyph: string;
  subtitle?: string;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={{ flex: 1 }}>
      <GlassCard style={styles.tile}>
        <View style={styles.glyphWrap}>
          <Text style={styles.glyph}>{glyph}</Text>
        </View>

        <View style={{ gap: 4 }}>
          <Text style={styles.title}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
        </View>
      </GlassCard>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  tile: { height: 118, justifyContent: "space-between" },
  glyphWrap: {
    width: 36,
    height: 36,
    borderRadius: 12,
    backgroundColor: "rgba(255,255,255,0.04)",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.10)",
    alignItems: "center",
    justifyContent: "center",
  },
  glyph: { color: theme.colors.gold, fontSize: 16, fontWeight: "700" },
  title: { color: theme.colors.text, fontSize: 15, fontWeight: "800" },
  subtitle: { color: theme.colors.muted, fontSize: 12 },
});
