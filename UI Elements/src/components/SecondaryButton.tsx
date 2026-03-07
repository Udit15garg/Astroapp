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
    borderColor: "rgba(245,192,48,0.22)",
    backgroundColor: "rgba(232,137,10,0.05)",
    paddingVertical: 15,
    alignItems: "center",
    justifyContent: "center",
  },
  txt: { color: theme.colors.muted, fontWeight: "800", fontSize: 14 },
});
