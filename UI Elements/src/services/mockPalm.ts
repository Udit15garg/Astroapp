import { HandProfile, ChatMessage } from "../types";

export const initialHands: HandProfile[] = [
  {
    id: "hand-1",
    name: "Hand 1",
    createdAt: Date.now() - 9 * 86400000,
    lastAnalyzedAt: Date.now() - 5 * 86400000,
    imageUri: "https://picsum.photos/400/600",
  },
  {
    id: "hand-2",
    name: "Hand 2",
    createdAt: Date.now() - 12 * 86400000,
    lastAnalyzedAt: Date.now() - 8 * 86400000,
    imageUri: "https://picsum.photos/401/601",
  },
];

export const initialChats: Record<string, ChatMessage[]> = {
  "hand-1": [
    { id: "m1", role: "assistant", text: "Saved. Ask me anything about this hand.", ts: Date.now() - 3600_000 },
  ],
  "hand-2": [
    { id: "m1", role: "assistant", text: "Saved. Ask me anything about this hand.", ts: Date.now() - 3600_000 },
  ],
};
