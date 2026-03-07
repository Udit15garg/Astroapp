export const theme = {
  colors: {
    bg0: "#07040F",                         // deep cosmic purple-black
    bg1: "#0D0820",                         // slightly lighter dark
    card: "rgba(255,165,50,0.07)",          // warm amber glass
    cardBorder: "rgba(255,165,50,0.22)",    // golden border
    text: "rgba(255,242,215,0.95)",         // warm cream white
    muted: "rgba(255,210,150,0.60)",        // warm amber muted
    saffron: "#E8890A",                     // primary saffron (replaces royal blue)
    saffronLight: "#FF9E1B",                // lighter saffron for hover/highlights
    gold: "#F5C030",                        // bright gold accent
    goldDeep: "#C9920A",                    // deep gold for borders
    divider: "rgba(255,165,50,0.12)",
    danger: "#FF4D4D",
    sacred: "rgba(232,137,10,0.15)",        // saffron glow tint for backgrounds
    glow: "rgba(245,192,48,0.08)",          // gold glow for cards
  },
  radius: { card: 20, btn: 14, pill: 999 },
  spacing: (n: number) => n * 8,
};
