import React, { useEffect, useRef, useState } from 'react';
import { Animated, StyleSheet, View, StyleProp, ViewStyle } from 'react-native';
import { MotionTokens, useReducedMotion } from '../../theme/motion';

interface CollapsibleProps {
  expanded: boolean;
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  duration?: number;
}

export const Collapsible: React.FC<CollapsibleProps> = ({
  expanded,
  children,
  style,
  duration = MotionTokens.duration.normal,
}) => {
  const isReducedMotion = useReducedMotion();
  const anim = useRef(new Animated.Value(expanded ? 1 : 0)).current;
  const [shouldRender, setShouldRender] = useState(expanded);

  useEffect(() => {
    if (expanded) {
      setShouldRender(true);
      if (isReducedMotion) {
        anim.setValue(1);
      } else {
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
        setShouldRender(false);
      } else {
        Animated.timing(anim, {
          toValue: 0,
          duration: Math.max(100, duration * 0.75),
          easing: MotionTokens.easing.exit,
          useNativeDriver: true,
        }).start(({ finished }) => {
          if (finished) {
            setShouldRender(false);
          }
        });
      }
    }
  }, [expanded, isReducedMotion]);

  if (!shouldRender) return null;

  const opacity = anim.interpolate({
    inputRange: [0, 1],
    outputRange: [0, 1],
  });

  const translateY = anim.interpolate({
    inputRange: [0, 1],
    outputRange: [-6, 0],
  });

  return (
    <Animated.View
      style={[
        styles.container,
        style,
        {
          opacity,
          transform: [{ translateY }],
        },
      ]}
    >
      {children}
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    overflow: 'hidden',
  },
});
