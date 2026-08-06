import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Text, TouchableOpacity, Platform } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import {
  Home,
  Calendar,
  Dumbbell,
  Users,
  QrCode,
} from 'lucide-react-native';

import { useAuth } from '../context/AuthContext';
import { Colors } from '../theme/colors';
import { SplashView } from '../components/SplashView';
import { AuthService } from '../services/authService';

import { LoginScreen } from '../screens/auth/LoginScreen';
import { RegisterScreen } from '../screens/auth/RegisterScreen';
import { HomeScreen } from '../screens/home/HomeScreen';
import { TrainingScreen } from '../screens/training/TrainingScreen';
import { CoachListScreen } from '../screens/coaches/CoachListScreen';
import { CommunityScreen } from '../screens/community/CommunityScreen';
import { CalendarScreen } from '../screens/calendar/CalendarScreen';
import { QREntryScreen } from '../screens/qr/QREntryScreen';
import { ActiveWorkoutScreen } from '../screens/training/ActiveWorkoutScreen';
import { RecordAttemptSetupScreen } from '../screens/training/RecordAttemptSetupScreen';
import {
  MembershipScreen,
  TrainingHistoryScreen,
  GoalsScreen,
  PersonalRecordsScreen,
  GenericMenuScreen,
} from '../screens/menu/MenuSubScreens';
import { ProfileScreen } from '../screens/profile/ProfileScreen';
import { NotificationsScreen } from '../screens/notifications/NotificationsScreen';
import { AllEntryHistoryScreen } from '../screens/qr/AllEntryHistoryScreen';

const Tab = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

const CustomTabBar = ({ state, descriptors, navigation }: any) => {
  const [hasActiveGym, setHasActiveGym] = useState(true);

  useEffect(() => {
    AuthService.getCurrentProfile()
      .then((p) => {
        setHasActiveGym(Boolean(p?.gym_id));
      })
      .catch(() => setHasActiveGym(true));
  }, []);

  return (
    <View style={styles.customTabBarContainer}>
      <View style={styles.customTabBarRow}>
        {state.routes.map((route: any, index: number) => {
          if (route.name === 'QRTab' && !hasActiveGym) {
            return null;
          }

          const { options } = descriptors[route.key];
          const isFocused = state.index === index;

          const onPress = () => {
            const event = navigation.emit({
              type: 'tabPress',
              target: route.key,
              canPreventDefault: true,
            });

            if (!isFocused && !event.defaultPrevented) {
              navigation.navigate(route.name);
            }
          };

          let IconComponent = Home;
          if (route.name === 'HomeTab') IconComponent = Home;
          else if (route.name === 'CalendarTab') IconComponent = Calendar;
          else if (route.name === 'TrainingTab') IconComponent = Dumbbell;
          else if (route.name === 'CommunityTab') IconComponent = Users;
          else if (route.name === 'QRTab') IconComponent = QrCode;

          const label = options.tabBarLabel || route.name;

          return (
            <TouchableOpacity
              key={route.key}
              onPress={onPress}
              style={styles.tabItem}
              activeOpacity={0.7}
            >
              <View
                style={[
                  styles.iconBox,
                  isFocused && styles.iconBoxFocused,
                ]}
              >
                <IconComponent
                  size={22}
                  color={isFocused ? Colors.allWhite : Colors.textSecondaryDark}
                />
              </View>
              <Text
                style={[
                  styles.tabLabel,
                  isFocused && styles.tabLabelFocused,
                ]}
              >
                {label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );
};

const MainTabNavigator: React.FC = () => {
  return (
    <Tab.Navigator
      tabBar={(props) => <CustomTabBar {...props} />}
      screenOptions={{ headerShown: false }}
    >
      <Tab.Screen
        name="HomeTab"
        component={HomeScreen}
        options={{ tabBarLabel: 'Anasayfa' }}
      />
      <Tab.Screen
        name="CalendarTab"
        component={CalendarScreen}
        options={{ tabBarLabel: 'Takvim' }}
      />
      <Tab.Screen
        name="TrainingTab"
        component={TrainingScreen}
        options={{ tabBarLabel: 'Antrenman' }}
      />
      <Tab.Screen
        name="CommunityTab"
        component={CommunityScreen}
        options={{ tabBarLabel: 'Topluluk' }}
      />
      <Tab.Screen
        name="QRTab"
        component={QREntryScreen}
        options={{ tabBarLabel: 'QR Tarama' }}
      />
    </Tab.Navigator>
  );
};

const AuthNavigator: React.FC = () => {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Login">
        {(props) => (
          <LoginScreen
            {...props}
            onNavigateToRegister={() => props.navigation.navigate('Register')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="Register">
        {(props) => (
          <RegisterScreen
            {...props}
            onNavigateToLogin={() => props.navigation.navigate('Login')}
          />
        )}
      </Stack.Screen>
    </Stack.Navigator>
  );
};

export const AppNavigator: React.FC = () => {
  const { session, loading } = useAuth();
  const [showSplash, setShowSplash] = useState(true);

  if (loading || showSplash) {
    return <SplashView onAnimationFinish={() => setShowSplash(false)} />;
  }

  return (
    <NavigationContainer>
      {session ? (
        <Stack.Navigator screenOptions={{ headerShown: false }}>
          <Stack.Screen name="MainTabs" component={MainTabNavigator} />
          <Stack.Screen name="Profile" component={ProfileScreen} />
          <Stack.Screen name="Coaches" component={CoachListScreen} />
          <Stack.Screen name="ActiveWorkout" component={ActiveWorkoutScreen} />
          <Stack.Screen name="PersonalRecords" component={RecordAttemptSetupScreen} />
          <Stack.Screen name="Membership" component={MembershipScreen} />
          <Stack.Screen name="TrainingHistory">
            {(props) => <TrainingHistoryScreen {...props} />}
          </Stack.Screen>
          <Stack.Screen name="Appointments">
            {(props) => <GenericMenuScreen {...props} title="Randevularım" />}
          </Stack.Screen>
          <Stack.Screen name="Calendar" component={CalendarScreen} />
          <Stack.Screen name="GroupClasses">
            {(props) => <GenericMenuScreen {...props} title="Grup Dersleri" />}
          </Stack.Screen>
          <Stack.Screen name="Nutrition">
            {(props) => <GenericMenuScreen {...props} title="Beslenme & Diyet" />}
          </Stack.Screen>
          <Stack.Screen name="Progress">
            {(props) => <GenericMenuScreen {...props} title="Gelişim & Ölçümler" />}
          </Stack.Screen>
          <Stack.Screen name="Goals" component={GoalsScreen} />
          <Stack.Screen name="Payments">
            {(props) => <GenericMenuScreen {...props} title="Ödemelerim" />}
          </Stack.Screen>
          <Stack.Screen name="Surveys">
            {(props) => <GenericMenuScreen {...props} title="Anketler & Geri Bildirim" />}
          </Stack.Screen>
          <Stack.Screen name="Reservations">
            {(props) => <GenericMenuScreen {...props} title="Rezervasyonlarım" />}
          </Stack.Screen>
          <Stack.Screen name="Settings">
            {(props) => <GenericMenuScreen {...props} title="Ayarlar" />}
          </Stack.Screen>
          <Stack.Screen name="AdminDashboard">
            {(props) => <GenericMenuScreen {...props} title="Admin Paneli" />}
          </Stack.Screen>
          <Stack.Screen name="Notifications" component={NotificationsScreen} />
          <Stack.Screen name="AllEntryHistory" component={AllEntryHistoryScreen} />
          <Stack.Screen name="Streak">
            {(props) => <GenericMenuScreen {...props} title="Seri & İstatistikler" />}
          </Stack.Screen>
        </Stack.Navigator>
      ) : (
        <AuthNavigator />
      )}
    </NavigationContainer>
  );
};

const styles = StyleSheet.create({
  customTabBarContainer: {
    backgroundColor: Colors.cardDark,
    borderTopWidth: 1,
    borderTopColor: Colors.borderDark,
    paddingBottom: Platform.OS === 'ios' ? 24 : 12,
    paddingTop: 10,
    elevation: 8,
  },
  customTabBarRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    paddingHorizontal: 8,
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconBox: {
    width: 50,
    height: 46,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconBoxFocused: {
    backgroundColor: Colors.primary,
  },
  tabLabel: {
    fontSize: 11,
    fontWeight: '500',
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  tabLabelFocused: {
    color: Colors.primary,
    fontWeight: '600',
  },
});
