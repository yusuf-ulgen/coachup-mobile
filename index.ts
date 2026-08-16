import * as SplashScreen from 'expo-splash-screen';

// Dismiss native splash screen immediately so custom SplashView is the only visible splash
SplashScreen.hideAsync().catch(() => {});

import { registerRootComponent } from 'expo';
import './src/services/locationService';

import App from './App';

// registerRootComponent calls AppRegistry.registerComponent('main', () => App);
// It also ensures that whether you load the app in Expo Go or in a native build,
// the environment is set up appropriately
registerRootComponent(App);

