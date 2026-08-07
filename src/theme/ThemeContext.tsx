import React, { createContext, useContext, useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

export interface ThemeColors {
  isDark: boolean;
  bg: string;
  cardBg: string;
  textPrimary: string;
  textSecondary: string;
  border: string;
  iconBg: string;
  iconBorder: string;
  pausePlayBg: string;
  pausePlayIcon: string;
}

const darkColors: ThemeColors = {
  isDark: true,
  bg: '#0A0A0F',
  cardBg: '#1C1C1E',
  textPrimary: '#FFFFFF',
  textSecondary: 'rgba(255, 255, 255, 0.7)',
  border: 'rgba(255, 255, 255, 0.1)',
  iconBg: 'rgba(255, 255, 255, 0.08)',
  iconBorder: 'rgba(255, 255, 255, 0.12)',
  pausePlayBg: '#FFFFFF',
  pausePlayIcon: '#000000',
};

const lightColors: ThemeColors = {
  isDark: false,
  bg: '#F2F2F7',
  cardBg: '#FFFFFF',
  textPrimary: '#0D0D0D',
  textSecondary: 'rgba(0, 0, 0, 0.6)',
  border: 'rgba(0, 0, 0, 0.08)',
  iconBg: 'rgba(0, 0, 0, 0.05)',
  iconBorder: 'rgba(0, 0, 0, 0.1)',
  pausePlayBg: '#1A1A1A',
  pausePlayIcon: '#FFFFFF',
};

interface ThemeContextType {
  isDark: boolean;
  themeMode: 'dark' | 'light' | 'system';
  colors: ThemeColors;
  toggleTheme: () => void;
  setThemeMode: (mode: 'dark' | 'light' | 'system') => void;
}

const ThemeContext = createContext<ThemeContextType>({
  isDark: true,
  themeMode: 'dark',
  colors: darkColors,
  toggleTheme: () => {},
  setThemeMode: () => {},
});

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [themeMode, setThemeModeState] = useState<'dark' | 'light' | 'system'>('dark');

  useEffect(() => {
    AsyncStorage.getItem('coachup_theme_mode').then((saved) => {
      if (saved === 'light' || saved === 'dark' || saved === 'system') {
        setThemeModeState(saved as any);
      }
    });
  }, []);

  const setThemeMode = async (mode: 'dark' | 'light' | 'system') => {
    setThemeModeState(mode);
    await AsyncStorage.setItem('coachup_theme_mode', mode);
  };

  const toggleTheme = async () => {
    const nextMode = themeMode === 'dark' ? 'light' : 'dark';
    await setThemeMode(nextMode);
  };

  const isDark = themeMode === 'dark';
  const colors = isDark ? darkColors : lightColors;

  return (
    <ThemeContext.Provider
      value={{
        isDark,
        themeMode,
        colors,
        toggleTheme,
        setThemeMode,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => useContext(ThemeContext);
