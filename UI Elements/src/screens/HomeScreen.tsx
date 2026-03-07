import React, { useMemo } from "react";
import { View, StyleSheet, Text, Alert, ScrollView } from "react-native";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { RootStackParamList } from "../navigation/AppNavigator";
import { CelestialBackground } from "../components/CelestialBackground";
import { TopBar } from "../components/TopBar";
import { Banner } from "../components/Banner";
import { FeatureTile } from "../components/FeatureTile";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

type Props = NativeStackScreenProps<RootStackParamList, "Home">;

export function HomeScreen({ navigation }: Props) {
  const { user, credits } = useAppState();

  const greeting = user?.name
    ? `Pranaam, ${user.name}. The cosmos awaits.`
    : "Speak, dear seeker. The stars are listening.";

  const features = useMemo(
    () => [
      {
        title: "Hast Rekha",
        glyph: "🖐",
        subtitle: "Palmistry",
        helpsWith: "Love · Career · Health · Life Path · Destiny",
        route: "PalmistryStart" as const,
      },
      {
        title: "Tarot Darshan",
        glyph: "☾",
        subtitle: "Sacred Cards",
        helpsWith: "Past & Future · Hidden Truths · Soul's Path",
        route: "TarotDraw" as const,
      },
      {
        title: "Ank Shastra",
        glyph: "७",
        subtitle: "Numerology",
        helpsWith: "Life Number · Lucky Dates · Name Vibrations",
        route: "PalmistryStart" as const,
      },
      {
        title: "Kundli",
        glyph: "✶",
        subtitle: "Birth Chart",
        helpsWith: "Planetary Doshas · Marriage Timing · Career Yoga",
        route: "PalmistryStart" as const,
      },
      {
        title: "Rashifal",
        glyph: "♈",
        subtitle: "Daily Horoscope",
        helpsWith: "Today's Energy · Weekly Blessings · Monthly Outlook",
        route: "PalmistryStart" as const,
      },
      {
        title: "Surya Rashi",
        glyph: "☀",
        subtitle: "Sun Sign",
        helpsWith: "Personality · Strengths · Cosmic Traits · Compatibility",
        route: "PalmistryStart" as const,
      },
    ],
    []
  );

  const handleGate = (route: keyof RootStackParamList) => {
    if (!user) {
      navigation.navigate("Auth", { returnTo: route });
      return;
    }
    navigation.navigate(route as any);
  };

  return (
    <CelestialBackground>
      <TopBar
        title="ASTRA"
        credits={credits}
        userInitial={(user?.name?.[0] ?? "?").toUpperCase()}
        onPressCredits={() => handleGate("Credits")}
        onPressProfile={() => handleGate("Profile")}
      />

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Baba's greeting */}
        <View style={styles.greetingWrap}>
          <Text style={styles.babaSymbol}>ॐ</Text>
          <Text style={styles.greeting}>{greeting}</Text>
          <Text style={styles.greetingSub}>
            {"What troubles your mind, dear child?\nChoose a path below and I shall guide you."}
          </Text>
        </View>

        {/* Sacred offering banner */}
        <Banner
          title="Seek Baba's Personal Guidance"
          subtitle="A private 1:1 reading, just for you →"
          onPress={() => Alert.alert("🪔 Baba Ji Says", "The Baba's personal sessions open soon. Return when the moon is full.")}
        />

        {/* Divider with sacred text */}
        <View style={styles.dividerRow}>
          <View style={styles.dividerLine} />
          <Text style={styles.dividerText}>Choose Your Path</Text>
          <View style={styles.dividerLine} />
        </View>

        {/* Feature grid */}
        <View style={styles.grid}>
          {features.map((f, idx) => (
            <View key={idx} style={{ width: "48%" }}>
              <FeatureTile
                title={f.title}
                glyph={f.glyph}
                subtitle={f.subtitle}
                helpsWith={f.helpsWith}
                onPress={() => handleGate(f.route)}
              />
            </View>
          ))}
        </View>

        <Text style={styles.disclaimer}>
          {"✦ The Baba's wisdom is for guidance and reflection.\nFor matters of the body or law, seek qualified counsel."}
        </Text>
      </ScrollView>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  greetingWrap: {
    paddingHorizontal: theme.spacing(2),
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(2),
    alignItems: "center",
  },
  babaSymbol: {
    fontSize: 36,
    color: theme.colors.gold,
    marginBottom: theme.spacing(1),
    opacity: 0.85,
  },
  greeting: {
    color: theme.colors.text,
    fontSize: 18,
    fontWeight: "900",
    textAlign: "center",
    letterSpacing: 0.3,
  },
  greetingSub: {
    color: theme.colors.muted,
    fontSize: 13,
    textAlign: "center",
    marginTop: 8,
    lineHeight: 20,
  },
  dividerRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: theme.spacing(2),
    marginVertical: theme.spacing(2),
    gap: theme.spacing(1),
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: "rgba(232,137,10,0.20)",
  },
  dividerText: {
    color: theme.colors.muted,
    fontSize: 10,
    fontWeight: "800",
    letterSpacing: 1.5,
    textTransform: "uppercase",
  },
  grid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: theme.spacing(1.5),
    paddingHorizontal: theme.spacing(2),
    justifyContent: "space-between",
  },
  disclaimer: {
    color: "rgba(255,210,150,0.38)",
    fontSize: 11,
    marginTop: theme.spacing(3),
    marginBottom: theme.spacing(3),
    textAlign: "center",
    paddingHorizontal: theme.spacing(3),
    lineHeight: 17,
  },
});
