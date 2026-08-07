import React, { useEffect } from 'react';
import { View, Text, StyleSheet, Animated } from 'react-native';
import { Check } from 'lucide-react-native';
import { Colors } from '../../theme/colors';

const formatCelebrationDuration = (seconds: number) => {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? `${m} dk ${s.toString().padStart(2, '0')} sn` : `${s} sn`;
};

export const WorkoutFinishCelebrationScreen: React.FC<{
  title?: string;
  durationSeconds?: number;
  onFinished: () => void;
}> = ({ title = 'Antrenman Tamamlandı!', durationSeconds = 0, onFinished }) => {
  const ringScale = React.useRef(new Animated.Value(0.4)).current;
  const contentScale = React.useRef(new Animated.Value(0)).current;
  const contentAlpha = React.useRef(new Animated.Value(0)).current;
  const pulse = React.useRef(new Animated.Value(1)).current;

  useEffect(() => {
    // Entrance animations
    Animated.spring(ringScale, {
      toValue: 1,
      useNativeDriver: true,
      bounciness: 10,
    }).start();

    setTimeout(() => {
      Animated.spring(contentScale, {
        toValue: 1,
        useNativeDriver: true,
        bounciness: 12,
      }).start();

      Animated.timing(contentAlpha, {
        toValue: 1,
        duration: 320,
        useNativeDriver: true,
      }).start();
    }, 120);

    // Pulse animation
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, {
          toValue: 1.12,
          duration: 900,
          useNativeDriver: true,
        }),
        Animated.timing(pulse, {
          toValue: 1,
          duration: 900,
          useNativeDriver: true,
        }),
      ])
    ).start();

    // Auto finish after 2.5 seconds
    const timer = setTimeout(() => {
      onFinished();
    }, 2500);

    return () => clearTimeout(timer);
  }, []);

  return (
    <View style={styles.container} onTouchEnd={onFinished}>
      <Animated.View
        style={[
          styles.ring,
          {
            transform: [
              { scale: ringScale },
              { scale: pulse }
            ],
          },
        ]}
      />
      <Animated.View
        style={[
          styles.contentCircle,
          {
            transform: [{ scale: contentScale }],
            opacity: contentAlpha,
          },
        ]}
      >
        <Check size={56} color={Colors.primary} />
      </Animated.View>

      <Animated.View
        style={[
          styles.textContainer,
          {
            opacity: contentAlpha,
            transform: [
              { scale: Animated.add(0.98, Animated.multiply(contentScale, 0.02)) }
            ],
          },
        ]}
      >
        <Text style={styles.congratulationsText}>Tamamlandı!</Text>
        <Text style={styles.titleText}>{title}</Text>
        <Text style={styles.durationText}>{formatCelebrationDuration(durationSeconds)}</Text>
      </Animated.View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(35, 28, 51, 0.95)',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 9999,
  },
  ring: {
    position: 'absolute',
    width: 180,
    height: 180,
    borderRadius: 90,
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
  },
  contentCircle: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: Colors.allWhite,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textContainer: {
    position: 'absolute',
    bottom: 72,
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  congratulationsText: {
    fontSize: 32,
    fontWeight: 'bold',
    color: Colors.allWhite,
    textAlign: 'center',
    marginBottom: 8,
  },
  titleText: {
    fontSize: 16,
    fontWeight: '500',
    color: 'rgba(255, 255, 255, 0.9)',
    textAlign: 'center',
    marginBottom: 8,
  },
  durationText: {
    fontSize: 14,
    color: 'rgba(255, 255, 255, 0.75)',
    textAlign: 'center',
  },
});
