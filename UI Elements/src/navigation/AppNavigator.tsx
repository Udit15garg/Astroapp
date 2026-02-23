import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { HomeScreen } from "../screens/HomeScreen";
import { PalmistryStartScreen } from "../screens/PalmistryStartScreen";
import { PalmCaptureScreen } from "../screens/PalmCaptureScreen";
import { HandListScreen } from "../screens/HandListScreen";
import { HandChatScreen } from "../screens/HandChatScreen";
import { TarotDrawScreen } from "../screens/TarotDrawScreen";
import { TarotResultScreen } from "../screens/TarotResultScreen";
import { CreditsScreen } from "../screens/CreditsScreen";
import { ProfileScreen } from "../screens/ProfileScreen";
import { AuthModal } from "../screens/AuthModal";

export type RootStackParamList = {
  Home: undefined;
  PalmistryStart: undefined;
  PalmCapture: undefined;
  HandList: undefined;
  HandChat: { handId: string };
  TarotDraw: undefined;
  TarotResult: { cards: { id: string; name: string; meaningShort: string; meaningLong: string }[] };
  Credits: undefined;
  Profile: undefined;
  Auth: { returnTo?: keyof RootStackParamList } | undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Home" component={HomeScreen} />

      <Stack.Screen name="PalmistryStart" component={PalmistryStartScreen} />
      <Stack.Screen name="PalmCapture" component={PalmCaptureScreen} />
      <Stack.Screen name="HandList" component={HandListScreen} />
      <Stack.Screen name="HandChat" component={HandChatScreen} />

      <Stack.Screen name="TarotDraw" component={TarotDrawScreen} />
      <Stack.Screen name="TarotResult" component={TarotResultScreen} />

      <Stack.Screen name="Credits" component={CreditsScreen} />
      <Stack.Screen name="Profile" component={ProfileScreen} />

      {/* Modal */}
      <Stack.Screen
        name="Auth"
        component={AuthModal}
        options={{ presentation: "modal", animation: "slide_from_bottom" }}
      />
    </Stack.Navigator>
  );
}
