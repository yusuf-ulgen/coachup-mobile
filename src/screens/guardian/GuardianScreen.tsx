import React from 'react';
import { View, StyleSheet, TouchableOpacity, Text, Platform } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import {
  Home,
  Users,
  CreditCard,
  User,
} from 'lucide-react-native';

import { Colors } from '../../theme/colors';
import { GuardianHomeScreen } from './GuardianHomeScreen';
import { GuardianChildrenScreen } from './GuardianChildrenScreen';
import { GuardianPaymentsScreen } from './GuardianPaymentsScreen';
import { GuardianProfileScreen } from './GuardianProfileScreen';

const Tab = createBottomTabNavigator();

import { useSafeAreaInsets } from 'react-native-safe-area-context';

const GuardianTabBar = ({ state, descriptors, navigation }: any) => {
  const insets = useSafeAreaInsets();
  const bottomPadding = Math.max(insets.bottom, Platform.OS === 'ios' ? 20 : 12) + 4;

  return (
    <View style={[styles.customTabBarContainer, { paddingBottom: bottomPadding }]}>
      <View style={styles.customTabBarRow}>
        {state.routes.map((route: any, index: number) => {
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
          if (route.name === 'GuardianHomeTab') IconComponent = Home;
          else if (route.name === 'GuardianChildrenTab') IconComponent = Users;
          else if (route.name === 'GuardianPaymentsTab') IconComponent = CreditCard;
          else if (route.name === 'GuardianProfileTab') IconComponent = User;

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

export const GuardianScreen = () => {
  return (
    <Tab.Navigator
      tabBar={(props) => <GuardianTabBar {...props} />}
      screenOptions={{ headerShown: false }}
    >
      <Tab.Screen
        name="GuardianHomeTab"
        component={GuardianHomeScreen}
        options={{ tabBarLabel: 'Ana Sayfa' }}
      />
      <Tab.Screen
        name="GuardianChildrenTab"
        component={GuardianChildrenScreen}
        options={{ tabBarLabel: 'Çocuklarım' }}
      />
      <Tab.Screen
        name="GuardianPaymentsTab"
        component={GuardianPaymentsScreen}
        options={{ tabBarLabel: 'Ödemeler' }}
      />
      <Tab.Screen
        name="GuardianProfileTab"
        component={GuardianProfileScreen}
        options={{ tabBarLabel: 'Profil' }}
      />
    </Tab.Navigator>
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
