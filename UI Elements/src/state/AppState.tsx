import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import { getJSON, setJSON, remove } from "../services/storage";
import { HandProfile, ChatMessage } from "../types";
import { initialHands, initialChats } from "../services/mockPalm";

type User = { id: string; name: string };

type AppState = {
  user: User | null;
  credits: number;
  hands: HandProfile[];
  chats: Record<string, ChatMessage[]>;
  login: (name: string) => Promise<void>;
  logout: () => Promise<void>;
  addCredits: (n: number) => Promise<void>;
  spendCredits: (n: number) => Promise<boolean>;
  upsertHand: (hand: HandProfile) => Promise<void>;
  renameHand: (handId: string, name: string) => Promise<void>;
  appendChat: (handId: string, msg: ChatMessage) => Promise<void>;
};

const Ctx = createContext<AppState | null>(null);

const KEYS = {
  user: "astra.user",
  credits: "astra.credits",
  hands: "astra.hands",
  chats: "astra.chats",
};

export function AppStateProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [credits, setCredits] = useState<number>(48);
  const [hands, setHands] = useState<HandProfile[]>([]);
  const [chats, setChats] = useState<Record<string, ChatMessage[]>>({});

  useEffect(() => {
    (async () => {
      const u = await getJSON<User | null>(KEYS.user, null);
      const c = await getJSON<number>(KEYS.credits, 48);
      const h = await getJSON<HandProfile[]>(KEYS.hands, initialHands);
      const ch = await getJSON<Record<string, ChatMessage[]>>(KEYS.chats, initialChats);
      setUser(u);
      setCredits(c);
      setHands(h);
      setChats(ch);
    })();
  }, []);

  const persist = async (next: Partial<{ user: User | null; credits: number; hands: HandProfile[]; chats: Record<string, ChatMessage[]> }>) => {
    if (next.user !== undefined) {
      if (next.user === null) await remove(KEYS.user);
      else await setJSON(KEYS.user, next.user);
    }
    if (next.credits !== undefined) await setJSON(KEYS.credits, next.credits);
    if (next.hands !== undefined) await setJSON(KEYS.hands, next.hands);
    if (next.chats !== undefined) await setJSON(KEYS.chats, next.chats);
  };

  const api: AppState = useMemo(() => ({
    user,
    credits,
    hands,
    chats,

    login: async (name: string) => {
      const next = { id: "u1", name: name.trim() || "User" };
      setUser(next);
      await persist({ user: next });
    },

    logout: async () => {
      setUser(null);
      await persist({ user: null });
    },

    addCredits: async (n: number) => {
      const next = Math.max(0, credits + n);
      setCredits(next);
      await persist({ credits: next });
    },

    spendCredits: async (n: number) => {
      if (credits < n) return false;
      const next = credits - n;
      setCredits(next);
      await persist({ credits: next });
      return true;
    },

    upsertHand: async (hand: HandProfile) => {
      const idx = hands.findIndex(h => h.id === hand.id);
      const next = idx >= 0 ? hands.map(h => (h.id === hand.id ? hand : h)) : [hand, ...hands];
      setHands(next);
      await persist({ hands: next });
    },

    renameHand: async (handId: string, name: string) => {
      const next = hands.map(h => (h.id === handId ? { ...h, name } : h));
      setHands(next);
      await persist({ hands: next });
    },

    appendChat: async (handId: string, msg: ChatMessage) => {
      const next = { ...chats, [handId]: [...(chats[handId] ?? []), msg] };
      setChats(next);
      await persist({ chats: next });
    },
  }), [user, credits, hands, chats]);

  return <Ctx.Provider value={api}>{children}</Ctx.Provider>;
}

export function useAppState() {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAppState must be used within AppStateProvider");
  return v;
}
