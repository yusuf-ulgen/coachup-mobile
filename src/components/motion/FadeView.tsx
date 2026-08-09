import React, { useEffect, useRef } from 'react';
import { Animated, StyleProp, ViewStyle } from 'react-native';
import { MotionTokens, useReducedMotion } from '../../theme/motion';

interface FadeViewProps {
  visible?: boolean;
  activeKey?: string | number;
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  duration?: number;
  translateYDistance?: number;
}

export const FadeView: React.FC<FadeViewProps> = ({
  visible = true,
  activeKey,
  children,
  style,
  duration = MotionTokens.duration.fast,
  translateYDistance = 4,
}) => {
  const isReducedMotion = useReducedMotion();
  const anim = useRef(new Animated.Value(visible ? 1 : 0)).current;

  // Trigger smooth fade-in whenever activeKey or visible changes
  useEffect(() => {
    if (visible) {
      if (isReducedMotion) {
        anim.setValue(1);
      } else {
        anim.setValue(0);
        Animated.timing(anim, {
          toValue: 1,
          duration,
          easing: MotionTokens.easing.enter,
          useNativeDriver: true,
        }).start();
      }
    } else {
      if (isReducedMotion) {
        anim.setValue(0);
      } else {
        Animated.timing(anim, {
          toValue: 0,
          duration: Math.max(80, duration * 0.7),
          easing: MotionTokens.easing.exit,
          useNativeDriver: true,
        }).start();
      }
    }
  }, [visible, activeKey, isReducedMotion]);

  if (!visible) return null;

  const translateY = anim.interpolate({
    inputRange: [0, 1],
    outputRange: [translateYDistance, 0],
  });

  return (
    <Animated.View
      style={[
        style,
        {
          opacity: anim,
          transform: translateYDistance > 0 ? [{ translateY }] : [],
        },
      ]}
    >
      {children}
    </Animated.View>
  );
};
