import React from 'react';
import { View, StyleSheet, ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { StatusBar, StatusBarStyle } from 'expo-status-bar';
import { Colors } from '../theme/colors';

interface ScreenContainerProps {
  children: React.ReactNode;
  style?: ViewStyle;
  contentStyle?: ViewStyle;
  includeTopInset?: boolean;
  includeBottomInset?: boolean;
  backgroundColor?: string;
  statusBarStyle?: StatusBarStyle;
}

export const ScreenContainer: React.FC<ScreenContainerProps> = ({
  children,
  style,
  contentStyle,
  includeTopInset = true,
  includeBottomInset = true,
  backgroundColor = Colors.backgroundDark,
  statusBarStyle = 'light',
}) => {
  const insets = useSafeAreaInsets();

  return (
    <View
      style={[
        styles.container,
        {
          backgroundColor,
          paddingTop: includeTopInset ? insets.top : 0,
          paddingBottom: includeBottomInset ? insets.bottom : 0,
        },
        style,
      ]}
    >
      <StatusBar style={statusBarStyle} />
      <View style={[styles.content, contentStyle]}>{children}</View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
  },
});
