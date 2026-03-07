import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { CelestialBackground } from "../components/CelestialBackground";
import { GlassCard } from "../components/GlassCard";
import { PrimaryButton } from "../components/PrimaryButton";
import { SecondaryButton } from "../components/SecondaryButton";
import { theme } from "../theme";
import { useAppState } from "../state/AppState";

const PACKAGES = [
  {
    label: "🪔  50 Sacred Tokens",
    subLabel: "Good for ~50 questions or readings",
    price: "₹49",
    credits: 50,
    highlighted: false,
  },
  {
    label: "🔥  200 Sacred Tokens",
    subLabel: "Best value — explore all paths deeply",
    price: "₹149",
    credits: 200,
    highlighted: true,
  },
];

export function CreditsScreen({ navigation }: any) {
  const { credits, addCredits } = useAppState();

  return (
    <CelestialBackground>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.sacredSymbol}>🪔</Text>
        <Text style={styles.title}>Dakshina</Text>
        <Text style={styles.titleSub}>Sacred Tokens</Text>
      </View>

      <View style={styles.body}>
        {/* Current balance */}
        <GlassCard style={styles.balanceCard}>
          <Text style={styles.balanceLabel}>Your Balance</Text>
          <Text style={styles.balanceAmount}>{credits}</Text>
          <Text style={styles.balanceUnit}>Sacred Tokens</Text>
          <Text style={styles.balanceBaba}>
            "Each token is a small offering that keeps the sacred flame burning, dear child."
          </Text>
        </GlassCard>

        {/* What credits are used for */}
        <GlassCard style={styles.usageCard}>
          <Text style={styles.usageTitle}>✦ How Tokens Are Used</Text>
          <View style={styles.usageList}>
            {[
              { icon: "☾", t: "Tarot reading", cost: "1 token" },
              { icon: "🖐", t: "Palm reading session", cost: "1 token" },
              { icon: "💬", t: "Each question to Baba", cost: "1 token" },
            ].map((u) => (
              <View key={u.t} style={styles.usageRow}>
                <Text style={styles.usageIcon}>{u.icon}</Text>
                <Text style={styles.usageText}>{u.t}</Text>
                <View style={styles.usageCostPill}>
                  <Text style={styles.usageCost}>{u.cost}</Text>
                </View>
              </View>
            ))}
          </View>
        </GlassCard>

        {/* Packages */}
        <Text style={styles.packagesTitle}>✦ Offer Your Dakshina</Text>
        <View style={styles.packages}>
          {PACKAGES.map((pkg) => (
            <GlassCard
              key={pkg.credits}
              style={[styles.packageCard, pkg.highlighted && styles.packageHighlighted]}
            >
              {pkg.highlighted && (
                <View style={styles.bestValueBadge}>
                  <Text style={styles.bestValueText}>BABA'S CHOICE</Text>
                </View>
              )}
              <View style={styles.packageInfo}>
                <Text style={styles.packageLabel}>{pkg.label}</Text>
                <Text style={styles.packageSub}>{pkg.subLabel}</Text>
              </View>
              <PrimaryButton
                title={`Offer ${pkg.price}`}
                onPress={() => addCredits(pkg.credits)}
                style={pkg.highlighted ? {} : { backgroundColor: "rgba(232,137,10,0.65)" }}
              />
            </GlassCard>
          ))}
        </View>

        <SecondaryButton title="← Return to the Chamber" onPress={() => navigation.goBack()} />
      </View>
    </CelestialBackground>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingTop: theme.spacing(4),
    paddingHorizontal: theme.spacing(2),
    alignItems: "center",
    paddingBottom: theme.spacing(1),
  },
  sacredSymbol: { fontSize: 36, marginBottom: 6 },
  title: { color: theme.colors.text, fontSize: 26, fontWeight: "900" },
  titleSub: { color: theme.colors.saffron, fontSize: 12, marginTop: 4, letterSpacing: 0.5 },
  body: { padding: theme.spacing(2), gap: theme.spacing(2) },
  balanceCard: {
    alignItems: "center",
    gap: 4,
    backgroundColor: "rgba(232,137,10,0.08)",
    borderColor: "rgba(232,137,10,0.30)",
  },
  balanceLabel: { color: theme.colors.muted, fontSize: 12, letterSpacing: 0.5, textTransform: "uppercase" },
  balanceAmount: { color: theme.colors.gold, fontSize: 52, fontWeight: "900", lineHeight: 60 },
  balanceUnit: { color: theme.colors.saffron, fontSize: 14, fontWeight: "700" },
  balanceBaba: { color: theme.colors.muted, fontSize: 12, fontStyle: "italic", textAlign: "center", marginTop: 6, lineHeight: 18 },
  usageCard: { gap: theme.spacing(1.5) },
  usageTitle: { color: theme.colors.gold, fontSize: 11, fontWeight: "900", letterSpacing: 1, textTransform: "uppercase" },
  usageList: { gap: 10 },
  usageRow: { flexDirection: "row", alignItems: "center", gap: theme.spacing(1) },
  usageIcon: { fontSize: 16, width: 24 },
  usageText: { color: theme.colors.text, fontSize: 13, flex: 1 },
  usageCostPill: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: theme.radius.pill,
    backgroundColor: "rgba(232,137,10,0.12)",
    borderWidth: 1,
    borderColor: "rgba(232,137,10,0.25)",
  },
  usageCost: { color: theme.colors.gold, fontSize: 11, fontWeight: "800" },
  packagesTitle: { color: theme.colors.gold, fontSize: 11, fontWeight: "900", letterSpacing: 1, textTransform: "uppercase" },
  packages: { gap: theme.spacing(1.5) },
  packageCard: { gap: theme.spacing(1.5) },
  packageHighlighted: {
    backgroundColor: "rgba(232,137,10,0.10)",
    borderColor: "rgba(232,137,10,0.45)",
  },
  bestValueBadge: {
    alignSelf: "flex-start",
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: theme.radius.pill,
    backgroundColor: theme.colors.saffron,
  },
  bestValueText: { color: "#FFF8E7", fontSize: 9, fontWeight: "900", letterSpacing: 1 },
  packageInfo: { gap: 4 },
  packageLabel: { color: theme.colors.text, fontSize: 15, fontWeight: "900" },
  packageSub: { color: theme.colors.muted, fontSize: 12 },
});
