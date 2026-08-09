import { Easing, AccessibilityInfo, Animated } from 'react-native';
import { useEffect, useState } from 'react';

/**
 * Motion System Design Tokens
 * Responsive, restrained motion guidelines for mobile interaction
 */
export const MotionTokens = {
  duration: {
    fast: 150,     // Tooltips, popovers, small toggles, tab fades
    normal: 220,   // Modals, bottom sheets, drawers, overlays
    slow: 280,     // Complex sheet transitions or multi-step reveals
  },

  easing: {
    // Standard Material / Natural ease-out curve
    standard: Easing.out(Easing.poly(4)),
    // Entrance ease curve (starts fast, decelerates smoothly)
    enter: Easing.out(Easing.cubic),
    // Exit ease curve (accelerates out smoothly)
    exit: Easing.in(Easing.cubic),
    // Linear (for simple opacity fades)
    linear: Easing.linear,
  },

  spring: {
    // Subtle physical spring (no bounce, smooth damping)
    subtle: {
      tension: 120,
      friction: 14,
      useNativeDriver: true,
    },
    // Gentle spring for modals
    gentle: {
      tension: 140,
      friction: 16,
      useNativeDriver: true,
    },
  },
};

/**
 * Hook to detect whether Reduced Motion is enabled on the device.
 * Respects user accessibility settings by simplifying or disabling heavy motion.
 */
export function useReducedMotion(): boolean {
  const [reducedMotion, setReducedMotion] = useState(false);

  useEffect(() => {
    let isMounted = true;
    AccessibilityInfo.isReduceMotionEnabled()
      .then((enabled) => {
        if (isMounted) setReducedMotion(enabled);
      })
      .catch(() => {});

    const subscription = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      (enabled: boolean) => {
        if (isMounted) setReducedMotion(enabled);
      }
    );

    return () => {
      isMounted = false;
      if (subscription && typeof subscription.remove === 'function') {
        subscription.remove();
      }
    };
  }, []);

  return reducedMotion;
}

/**
 * Helper to create an animated timing config respecting reduced motion
 */
export function createTimingConfig(
  toValue: number,
  options?: {
    duration?: number;
    easing?: (value: number) => number;
    useNativeDriver?: boolean;
    reducedMotion?: boolean;
  }
): Animated.TimingAnimationConfig {
  const isReduced = options?.reducedMotion ?? false;
  return {
    toValue,
    duration: isReduced ? 0 : (options?.duration ?? MotionTokens.duration.normal),
    easing: options?.easing ?? MotionTokens.easing.standard,
    useNativeDriver: options?.useNativeDriver ?? true,
  };
}
