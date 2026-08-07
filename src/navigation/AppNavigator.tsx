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
import PusherService from '../services/pusherService';

import { LoginScreen } from '../screens/auth/LoginScreen';
import { RegisterScreen } from '../screens/auth/RegisterScreen';
import { HomeScreen } from '../screens/home/HomeScreen';
import { TrainingScreen } from '../screens/training/TrainingScreen';
import { CoachListScreen } from '../screens/coaches/CoachListScreen';
import { CoachDetailScreen } from '../screens/coaches/CoachDetailScreen';
import { CoachChatScreen } from '../screens/coaches/CoachChatScreen';
import { CommunityScreen } from '../screens/community/CommunityScreen';
import { CalendarScreen } from '../screens/calendar/CalendarScreen';
import { QREntryScreen } from '../screens/qr/QREntryScreen';
import { ActiveWorkoutScreen } from '../screens/training/ActiveWorkoutScreen';
import { RecordAttemptSetupScreen } from '../screens/training/RecordAttemptSetupScreen';
import { RecordAttemptSessionScreen } from '../screens/training/RecordAttemptSessionScreen';
import { RecordAttemptSummaryScreen } from '../screens/training/RecordAttemptSummaryScreen';
import { RecordAttemptTimedModesScreen } from '../screens/training/RecordAttemptTimedModesScreen';
import { WorkoutSummaryScreen } from '../screens/training/WorkoutSummaryScreen';
import { WorkoutFinishCelebrationScreen } from '../screens/training/WorkoutFinishCelebrationScreen';
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
import { SettingsScreen } from '../screens/settings/SettingsScreen';
import { AddressSettingsScreen } from '../screens/settings/AddressScreen';
import { PasswordSettingsScreen } from '../screens/settings/PasswordSettingsScreen';
import { AppearanceSettingsScreen } from '../screens/settings/AppearanceSettingsScreen';
import { StreakScreen } from '../screens/streak/StreakScreen';
import { ResultsScreen } from '../screens/results/ResultsScreen';
import { ResultDetailScreen } from '../screens/results/ResultDetailScreen';
import { NutritionScreen } from '../screens/nutrition/NutritionScreen';
import { ProgressTrackingScreen } from '../screens/progress/ProgressTrackingScreen';
import { AppointmentsScreen } from '../screens/appointments/AppointmentsScreen';
import { PaymentsScreen } from '../screens/payments/PaymentsScreen';
import { ReservationsScreen } from '../screens/reservations/ReservationsScreen';
import GroupClassesScreen from '../screens/groups/GroupClassesScreen';
import SurveysScreen from '../screens/surveys/SurveysScreen';
import { AdminDashboardScreen } from '../screens/admin/AdminDashboardScreen';
import { GuardianScreen } from '../screens/guardian/GuardianScreen';
import { GuardianChildDetailScreen } from '../screens/guardian/GuardianChildDetailScreen';
import { FloatingActiveWorkoutOverlay } from '../components/FloatingActiveWorkoutOverlay';
import { CustomAlertContainer } from '../components/CustomAlertModal';

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
  const [isGuardian, setIsGuardian] = useState(false);
  const [guardianChecked, setGuardianChecked] = useState(false);

  useEffect(() => {
    if (!session) {
      setGuardianChecked(true);
      return;
    }
    // Guardian kontrolu yap
    const checkGuardian = async () => {
      try {
        const { supabase } = await import('../services/supabaseClient');
        const { data } = await supabase
          .from('guardians')
          .select('id')
          .eq('user_id', session.user.id)
          .single();
        setIsGuardian(Boolean(data));
      } catch (e) {
        setIsGuardian(false);
      } finally {
        setGuardianChecked(true);
      }
    };
    checkGuardian();
  }, [session]);

  useEffect(() => {
    if (!session) return;
    // Streak sync
    const runStreakSync = async () => {
      try {
        const { supabase } = await import('../services/supabaseClient');
        // Streak guncelle: son antrenman bugune kadar mi?
        const today = new Date().toISOString().split('T')[0];
        const { data: lastSession } = await supabase
          .from('training_sessions')
          .select('completed_at')
          .eq('user_id', session.user.id)
          .not('completed_at', 'is', null)
          .order('completed_at', { ascending: false })
          .limit(1)
          .single();
        
        if (lastSession?.completed_at) {
          const lastDate = new Date(lastSession.completed_at).toISOString().split('T')[0];
          const yesterday = new Date();
          yesterday.setDate(yesterday.getDate() - 1);
          const yesterdayStr = yesterday.toISOString().split('T')[0];
          
          if (lastDate !== today && lastDate !== yesterdayStr) {
            // Streak kirdi, sifirla
            await supabase
              .from('users')
              .update({ current_streak: 0 })
              .eq('id', session.user.id);
          }
        }
      } catch (e) {
        // Sessizce gec
      }
    };
    runStreakSync();
    
    // Pusher / Supabase Realtime abonelikleri
    const setupSubscriptions = async () => {
      try {
        const { supabase } = await import('../services/supabaseClient');
        const { data: profile } = await supabase
          .from('users')
          .select('gym_id')
          .eq('id', session.user.id)
          .single();
          
        PusherService.subscribeToUser(session.user.id, (payload) => {
          console.log('Kullanıcı bildirimi:', payload);
        });
        
        if (profile?.gym_id) {
          PusherService.subscribeToGym(profile.gym_id, (payload) => {
            console.log('Salon bildirimi:', payload);
          });
        }
      } catch (e) {
        console.error('PusherService abonelik hatası:', e);
      }
    };
    
    setupSubscriptions();
    
    return () => {
      PusherService.unsubscribeAll();
    };
  }, [session]);

  if (loading || showSplash || (session && !guardianChecked)) {
    return <SplashView onAnimationFinish={() => setShowSplash(false)} />;
  }

  return (
    <NavigationContainer>
      {session ? (
        isGuardian ? (
          // Veli modu navigasyonu
          <Stack.Navigator screenOptions={{ headerShown: false }}>
            <Stack.Screen name="GuardianTabs" component={GuardianScreen} />
            <Stack.Screen name="GuardianChildDetail" component={GuardianChildDetailScreen} />
          </Stack.Navigator>
        ) : (
          <Stack.Navigator screenOptions={{ headerShown: false }}>
            <Stack.Screen name="MainTabs" component={MainTabNavigator} />
          <Stack.Screen name="Profile" component={ProfileScreen} />
          <Stack.Screen name="Coaches" component={CoachListScreen} />
          <Stack.Screen name="CoachDetail" component={CoachDetailScreen} />
          <Stack.Screen name="CoachChat" component={CoachChatScreen} />
          <Stack.Screen name="ActiveWorkout" component={ActiveWorkoutScreen} />
          <Stack.Screen name="PersonalRecords" component={RecordAttemptSetupScreen} />
          <Stack.Screen name="RecordAttemptSession" component={RecordAttemptSessionScreen} />
          <Stack.Screen name="RecordAttemptTimedModes" component={RecordAttemptTimedModesScreen} />
          <Stack.Screen name="RecordAttemptSummary" component={RecordAttemptSummaryScreen} />
          <Stack.Screen name="WorkoutSummary" component={WorkoutSummaryScreen} />
          <Stack.Screen name="WorkoutFinishCelebration">
            {(props) => (
              <WorkoutFinishCelebrationScreen 
                {...props.route.params} 
                onFinished={() => props.navigation.navigate('WorkoutSummary', props.route.params)} 
              />
            )}
          </Stack.Screen>
          <Stack.Screen name="Membership" component={MembershipScreen} />
          <Stack.Screen name="TrainingHistory">
            {(props) => <TrainingHistoryScreen {...props} />}
          </Stack.Screen>
          <Stack.Screen name="Appointments" component={AppointmentsScreen} />
          <Stack.Screen name="Calendar" component={CalendarScreen} />
          <Stack.Screen name="GroupClasses" component={GroupClassesScreen} />
          <Stack.Screen name="Nutrition" component={NutritionScreen} />
          <Stack.Screen name="Progress" component={ProgressTrackingScreen} />
          <Stack.Screen name="Goals" component={GoalsScreen} />
          <Stack.Screen name="Payments" component={PaymentsScreen} />
          <Stack.Screen name="Surveys" component={SurveysScreen} />
          <Stack.Screen name="Reservations" component={ReservationsScreen} />
          <Stack.Screen name="Settings" component={SettingsScreen} />
          <Stack.Screen name="AddressSettings" component={AddressSettingsScreen} />
          <Stack.Screen name="PasswordSettings" component={PasswordSettingsScreen} />
          <Stack.Screen name="AppearanceSettings" component={AppearanceSettingsScreen} />
          <Stack.Screen name="AdminDashboard" component={AdminDashboardScreen} />
          <Stack.Screen name="Notifications" component={NotificationsScreen} />
          <Stack.Screen name="AllEntryHistory" component={AllEntryHistoryScreen} />
          <Stack.Screen name="Streak" component={StreakScreen} />
          <Stack.Screen name="Results" component={ResultsScreen} />
          <Stack.Screen name="ResultDetail" component={ResultDetailScreen} />
        </Stack.Navigator>
        )
      ) : (
        <AuthNavigator />
      )}
      
      {session && !isGuardian && <FloatingActiveWorkoutOverlay />}
      <CustomAlertContainer />
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
