import React from "react";
import { Pressable, StyleSheet, Text, ViewStyle } from "react-native";
import { theme } from "../theme";

export function PrimaryButton({
  title,
  onPress,
  disabled,
  style,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  style?: ViewStyle;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={[
        styles.btn,
        disabled ? { opacity: 0.4 } : null,
        style,
      ]}
    >
      <Text style={styles.txt}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  btn: {
    borderRadius: theme.radius.btn,
    backgroundColor: theme.colors.royal,
    paddingVertical: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  txt: { color: "white", fontWeight: "900", fontSize: 14 },
});
