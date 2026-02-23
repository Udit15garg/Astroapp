import React from "react";
import { View, StyleSheet, ViewStyle } from "react-native";
import { theme } from "../theme";

export function GlassCard({ children, style }: { children: React.ReactNode; style?: ViewStyle }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: theme.colors.card,
    borderColor: theme.colors.cardBorder,
    borderWidth: 1,
    borderRadius: theme.radius.card,
    padding: theme.spacing(2),
  },
});
