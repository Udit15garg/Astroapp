import React from "react";
import { View, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { theme } from "../theme";

export function CelestialBackground({ children }: { children: React.ReactNode }) {
  return (
    <View style={styles.root}>
      <LinearGradient colors={[theme.colors.bg0, theme.colors.bg1]} style={StyleSheet.absoluteFillObject} />
      <View style={styles.cornerGlowTopLeft} pointerEvents="none" />
      <View style={styles.cornerGlowBottomRight} pointerEvents="none" />
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: theme.colors.bg0 },
  cornerGlowTopLeft: {
    position: "absolute",
    top: -140,
    left: -120,
    width: 260,
    height: 260,
    borderRadius: 260,
    backgroundColor: "rgba(46, 91, 255, 0.10)",
  },
  cornerGlowBottomRight: {
    position: "absolute",
    bottom: -160,
    right: -140,
    width: 320,
    height: 320,
    borderRadius: 320,
    backgroundColor: "rgba(243, 196, 107, 0.06)",
  },
});
