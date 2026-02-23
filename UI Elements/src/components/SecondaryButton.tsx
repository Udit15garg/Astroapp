import React from "react";
import { Pressable, StyleSheet, Text, ViewStyle } from "react-native";
import { theme } from "../theme";

export function SecondaryButton({
  title,
  onPress,
  style,
}: {
  title: string;
  onPress: () => void;
  style?: ViewStyle;
}) {
  return (
    <Pressable onPress={onPress} style={[styles.btn, style]}>
      <Text style={styles.txt}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  btn: {
    borderRadius: theme.radius.btn,
    borderWidth: 1,
    borderColor: theme.colors.cardBorder,
    backgroundColor: "rgba(255,255,255,0.03)",
    paddingVertical: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  txt: { color: theme.colors.text, fontWeight: "800", fontSize: 14 },
});
