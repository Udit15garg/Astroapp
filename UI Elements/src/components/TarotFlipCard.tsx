import React, { useRef } from "react";
import { Animated, Pressable, StyleSheet, Text, View } from "react-native";
import { theme } from "../theme";

// Position labels the Baba uses
const POSITION_LABELS = ["Bhoot\nPast", "Vartaman\nPresent", "Bhavishya\nFuture"];

export function TarotFlipCard({
  index,
  revealed,
  titleTop,
  titleBottom,
  onPress,
}: {
  index: number;
  revealed: boolean;
  titleTop: string;
  titleBottom: string;
  onPress: () => void;
}) {
  const anim = useRef(new Animated.Value(revealed ? 1 : 0)).current;

  React.useEffect(() => {
    Animated.timing(anim, {
      toValue: revealed ? 1 : 0,
      duration: 520,
      useNativeDriver: true,
    }).start();
  }, [revealed]);

  const rotateY = anim.interpolate({
    inputRange: [0, 1],
    outputRange: ["0deg", "180deg"],
  });

  const frontOpacity = anim.interpolate({
    inputRange: [0, 0.49, 0.5, 1],
    outputRange: [1, 1, 0, 0],
  });

  const backOpacity = anim.interpolate({
    inputRange: [0, 0.49, 0.5, 1],
    outputRange: [0, 0, 1, 1],
  });

  const posLabel = POSITION_LABELS[index] ?? "";

  return (
    <Pressable onPress={onPress} style={{ width: "30%" }}>
      {/* Position label above card */}
      <Text style={styles.posLabel}>{posLabel}</Text>

      <View style={styles.container}>
        <Animated.View style={[styles.card, { transform: [{ perspective: 1000 }, { rotateY }] }]}>
          {/* FRONT: face-down sacred card back */}
          <Animated.View style={[styles.face, { opacity: frontOpacity }]}>
            <View style={styles.sacredSymbolWrap}>
              <Text style={styles.sacredSymbol}>ॐ</Text>
            </View>
            <Text style={styles.tapHint}>Touch to{"\n"}reveal</Text>
          </Animated.View>

          {/* BACK: revealed card content */}
          <Animated.View
            style={[
              styles.face,
              styles.backFace,
              { opacity: backOpacity, transform: [{ rotateY: "180deg" }] },
            ]}
          >
            <Text style={styles.cardName}>{titleTop}</Text>
            <View style={styles.goldDot} />
            <Text style={styles.cardMeaning}>{titleBottom}</Text>
          </Animated.View>
        </Animated.View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  posLabel: {
    color: theme.colors.muted,
    fontSize: 9,
    fontWeight: "700",
    letterSpacing: 0.5,
    textAlign: "center",
    marginBottom: 6,
    lineHeight: 13,
  },
  container: { height: 220 },
  card: {
    flex: 1,
    borderRadius: theme.radius.card,
    backgroundColor: "rgba(50,20,80,0.60)",
    borderWidth: 1,
    borderColor: "rgba(245,192,48,0.25)",
    overflow: "hidden",
  },
  face: {
    ...StyleSheet.absoluteFillObject,
    padding: theme.spacing(1.5),
    justifyContent: "space-between",
    alignItems: "center",
  },
  backFace: {
    backgroundColor: "rgba(232,137,10,0.06)",
  },
  // Front face elements
  sacredSymbolWrap: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  sacredSymbol: {
    color: "rgba(245,192,48,0.55)",
    fontSize: 40,
  },
  tapHint: {
    color: theme.colors.muted,
    fontSize: 10,
    textAlign: "center",
    lineHeight: 14,
  },
  // Back face elements
  cardName: {
    color: theme.colors.gold,
    fontWeight: "900",
    fontSize: 11,
    textAlign: "center",
  },
  goldDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: theme.colors.gold,
    opacity: 0.85,
  },
  cardMeaning: {
    color: theme.colors.muted,
    fontSize: 10,
    textAlign: "center",
    lineHeight: 14,
  },
});
