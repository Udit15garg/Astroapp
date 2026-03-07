import React from "react";
import { View, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { theme } from "../theme";

export function CelestialBackground({ children }: { children: React.ReactNode }) {
  return (
    <View style={styles.root}>
      {/* Deep cosmic gradient — purple-black to midnight blue */}
      <LinearGradient
        colors={[theme.colors.bg0, "#0B0618", theme.colors.bg1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0.6, y: 1 }}
        style={StyleSheet.absoluteFillObject}
      />

      {/* Top-right: divine saffron light — like a sacred diya flame */}
      <View style={styles.glowTopRight} pointerEvents="none" />

      {/* Top-left: subtle amber warmth */}
      <View style={styles.glowTopLeft} pointerEvents="none" />

      {/* Bottom-left: deep purple mystical glow */}
      <View style={styles.glowBottomLeft} pointerEvents="none" />

      {/* Bottom-right: gold shimmer */}
      <View style={styles.glowBottomRight} pointerEvents="none" />

      {/* Center faint sacred aura */}
      <View style={styles.centerAura} pointerEvents="none" />

      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: theme.colors.bg0 },

  glowTopRight: {
    position: "absolute",
    top: -80,
    right: -60,
    width: 280,
    height: 280,
    borderRadius: 280,
    backgroundColor: "rgba(232,137,10,0.13)",
  },
  glowTopLeft: {
    position: "absolute",
    top: -60,
    left: -80,
    width: 220,
    height: 220,
    borderRadius: 220,
    backgroundColor: "rgba(200,100,10,0.07)",
  },
  glowBottomLeft: {
    position: "absolute",
    bottom: -120,
    left: -100,
    width: 300,
    height: 300,
    borderRadius: 300,
    backgroundColor: "rgba(80,20,120,0.18)",
  },
  glowBottomRight: {
    position: "absolute",
    bottom: -80,
    right: -80,
    width: 240,
    height: 240,
    borderRadius: 240,
    backgroundColor: "rgba(245,192,48,0.08)",
  },
  centerAura: {
    position: "absolute",
    top: "30%",
    left: "20%",
    width: 260,
    height: 260,
    borderRadius: 260,
    backgroundColor: "rgba(232,137,10,0.04)",
  },
});
