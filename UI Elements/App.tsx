import 'react-native-gesture-handler';
import React from "react";
import { NavigationContainer } from "@react-navigation/native";
import { AppNavigator } from "./src/navigation/AppNavigator";
import { AppStateProvider } from "./src/state/AppState";
import { StatusBar } from "expo-status-bar";

export default function App() {
  return (
    <AppStateProvider>
      <NavigationContainer>
        <StatusBar style="light" />
        <AppNavigator />
      </NavigationContainer>
    </AppStateProvider>
  );
}
