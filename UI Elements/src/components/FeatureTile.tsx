import React from "react";
import { Text, StyleSheet, Pressable, View } from "react-native";
import { GlassCard } from "./GlassCard";
import { theme } from "../theme";

export function FeatureTile({
  title,
  glyph,
  subtitle,
  helpsWith,
  onPress,
}: {
  title: string;
  glyph: string;
  subtitle?: string;
  helpsWith?: string;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={{ flex: 1 }}>
      <GlassCard style={styles.tile}>
        {/* Icon glyph in sacred circle */}
        <View style={styles.glyphWrap}>
          <Text style={styles.glyph}>{glyph}</Text>
        </View>

        {/* Feature name */}
        <View style={{ gap: 3 }}>
          <Text style={styles.title}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
        </View>

        {/* Divider */}
        {helpsWith ? <View style={styles.divider} /> : null}

        {/* "Reveals:" guide */}
        {helpsWith ? (
          <View>
            <Text style={styles.revealsLabel}>Reveals ›</Text>
            <Text style={styles.revealsText}>{helpsWith}</Text>
          </View>
        ) : null}
      </GlassCard>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  tile: { minHeight: 170, justifyContent: "space-between" },
  glyphWrap: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: "rgba(232,137,10,0.12)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.30)",
    alignItems: "center",
    justifyContent: "center",
  },
  glyph: { color: theme.colors.gold, fontSize: 18 },
  title: { color: theme.colors.text, fontSize: 15, fontWeight: "900" },
  subtitle: { color: theme.colors.muted, fontSize: 11 },
  divider: {
    height: 1,
    backgroundColor: "rgba(232,137,10,0.15)",
    marginVertical: 2,
  },
  revealsLabel: {
    color: theme.colors.saffron,
    fontSize: 9,
    fontWeight: "900",
    letterSpacing: 1,
    textTransform: "uppercase",
    marginBottom: 3,
  },
  revealsText: {
    color: theme.colors.muted,
    fontSize: 10,
    lineHeight: 15,
  },
});
