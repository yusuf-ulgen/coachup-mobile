import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  Animated,
  ActivityIndicator,
  Dimensions,
} from 'react-native';
import { GYM_CONFIG } from '../config/gym';

interface SplashViewProps {
  onAnimationFinish?: () => void;
}

export const SplashView: React.FC<SplashViewProps> = ({ onAnimationFinish }) => {
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(fadeAnim, {
      toValue: 1,
      duration: 400,
      useNativeDriver: true,
    }).start();

    const timer = setTimeout(() => {
      if (onAnimationFinish) {
        onAnimationFinish();
      }
    }, 1800);

    return () => clearTimeout(timer);
  }, []);

  return (
    <View style={styles.container}>
      <Animated.View style={[styles.centerContent, { opacity: fadeAnim }]}>
        <Image
          source={GYM_CONFIG.SPLASH_LOGO}
          style={styles.logo}
          resizeMode="contain"
        />
        <ActivityIndicator
          size="small"
          color="rgba(255, 255, 255, 0.7)"
          style={styles.spinner}
        />
      </Animated.View>

      <Animated.View style={[styles.bottomContent, { opacity: fadeAnim }]}>
        <Text style={styles.byText}>by</Text>
        <Image
          source={GYM_CONFIG.WATERMARK_LOGO}
          style={styles.watermarkLogo}
          resizeMode="contain"
        />
      </Animated.View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFill,
    backgroundColor: GYM_CONFIG.SPLASH_BG_COLOR,
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 9999,
  },
  centerContent: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  logo: {
    width: 160,
    height: 160,
  },
  spinner: {
    marginTop: 24,
  },
  bottomContent: {
    position: 'absolute',
    bottom: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  byText: {
    color: '#8E8E93',
    fontSize: 14,
    marginRight: 4,
  },
  watermarkLogo: {
    height: 26,
    width: 100,
  },
});
