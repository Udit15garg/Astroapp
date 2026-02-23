export type TarotCard = {
  id: string;
  name: string;
  meaningShort: string;
  meaningLong: string;
};

export type HandProfile = {
  id: string;
  name: string;
  createdAt: number;
  lastAnalyzedAt?: number;
  imageUri: string;
};

export type ChatMessage = {
  id: string;
  role: "user" | "assistant";
  text: string;
  ts: number;
};
