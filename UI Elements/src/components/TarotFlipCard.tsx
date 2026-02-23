import React, { useMemo, useRef } from "react";
import { Animated, Pressable, StyleSheet, Text, View } from "react-native";
import { theme } from "../theme";

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

  // drive animation when prop changes
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

  return (
    <Pressable onPress={onPress} style={{ width: "30%" }}>
      <View style={styles.container}>
        <Animated.View style={[styles.card, { transform: [{ perspective: 1000 }, { rotateY }] }]}>
          {/* FRONT (face down) */}
          <Animated.View style={[styles.face, styles.front, { opacity: frontOpacity }]}>
            <Text style={styles.small}>Tap</Text>
            <View style={styles.backMark} />
            <Text style={styles.muted}>Reveal</Text>
          </Animated.View>

          {/* BACK (revealed) */}
          <Animated.View style={[styles.face, styles.back, { opacity: backOpacity, transform: [{ rotateY: "180deg" }] }]}>
            <Text style={styles.small}>{titleTop}</Text>
            <View style={styles.revealDot} />
            <Text style={styles.muted}>{titleBottom}</Text>
          </Animated.View>
        </Animated.View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { height: 220 },
  card: {
    flex: 1,
    borderRadius: theme.radius.card,
    backgroundColor: theme.colors.card,
    borderWidth: 1,
    borderColor: theme.colors.cardBorder,
    overflow: "hidden",
  },
  face: {
    ...StyleSheet.absoluteFillObject,
    padding: theme.spacing(2),
    justifyContent: "space-between",
  },
  front: {},
  back: {},
  small: { color: theme.colors.text, fontWeight: "900", fontSize: 12 },
  muted: { color: theme.colors.muted, fontSize: 12 },
  backMark: {
    alignSelf: "center",
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.12)",
    backgroundColor: "rgba(255,255,255,0.04)",
  },
  revealDot: {
    alignSelf: "center",
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: "rgba(243,196,107,0.85)",
  },
});
