import React, { useRef, useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  PanResponder,
  TouchableOpacity,
  Dimensions,
} from 'react-native';
import { Timer, Activity, Maximize2, X } from 'lucide-react-native';
import { Colors } from '../theme/colors';
import { useNavigation } from '@react-navigation/native';
import { ActiveWorkoutManager } from '../services/activeWorkoutManager';
import { CustomAlert } from './CustomAlertModal';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

export const FloatingActiveWorkoutOverlay: React.FC = () => {
  const insets = useSafeAreaInsets();
  const pan = useRef(new Animated.ValueXY()).current;
  const currentPos = useRef({ x: 0, y: 0 });
  const navigation = useNavigation<any>();
  const [workoutState, setWorkoutState] = useState(() => ActiveWorkoutManager.getState());
  const [shouldShow, setShouldShow] = useState(() => ActiveWorkoutManager.shouldShowOverlay());

  // Subscribe to manager state & update seconds dynamically every second
  useEffect(() => {
    const update = () => {
      const state = ActiveWorkoutManager.getState();
      const visible = ActiveWorkoutManager.shouldShowOverlay();
      setWorkoutState(state);
      setShouldShow(visible);
    };

    update();
    const unsubscribe = ActiveWorkoutManager.subscribe(update);
    const interval = setInterval(update, 1000);

    return () => {
      unsubscribe();
      clearInterval(interval);
    };
  }, []);

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => false,
      onMoveShouldSetPanResponder: (_, gestureState) => {
        return Math.abs(gestureState.dx) > 3 || Math.abs(gestureState.dy) > 3;
      },
      onPanResponderGrant: () => {
        // Position anchored at bottom: 90, right: 16
      },
      onPanResponderMove: (_, gestureState) => {
        const startX = currentPos.current.x;
        const startY = currentPos.current.y;

        const rawX = startX + gestureState.dx;
        const rawY = startY + gestureState.dy;

        // Screen boundaries for overlay (width ~240px, height ~54px)
        const minX = -(SCREEN_WIDTH - 250 - 28);
        const maxX = 0;
        const minY = -(SCREEN_HEIGHT - 90 - 54 - 50);
        const maxY = 60;

        const clampedX = Math.min(Math.max(rawX, minX), maxX);
        const clampedY = Math.min(Math.max(rawY, minY), maxY);

        pan.setValue({ x: clampedX, y: clampedY });
      },
      onPanResponderRelease: (_, gestureState) => {
        const startX = currentPos.current.x;
        const startY = currentPos.current.y;

        const minX = -(SCREEN_WIDTH - 250 - 28);
        const maxX = 0;
        const minY = -(SCREEN_HEIGHT - 90 - 54 - 50);
        const maxY = 60;

        const finalX = Math.min(Math.max(startX + gestureState.dx, minX), maxX);
        const finalY = Math.min(Math.max(startY + gestureState.dy, minY), maxY);

        currentPos.current = { x: finalX, y: finalY };
        pan.setValue({ x: finalX, y: finalY });
      },
    })
  ).current;

  // Show when there is an active session and user is NOT on ActiveWorkout screen
  if (!shouldShow || !workoutState.sessionId) {
    return null;
  }

  const formatTime = (totalSeconds: number) => {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  const handleExpand = () => {
    ActiveWorkoutManager.setScreenFocus(true);
    navigation.navigate('ActiveWorkout', {
      sessionId: workoutState.sessionId,
      programId: workoutState.programId,
      title: workoutState.title,
      workoutTitle: workoutState.workoutTitle || workoutState.title,
      category: workoutState.category || '',
      emoji: workoutState.emoji || '🏃',
    });
  };

  const handleClose = () => {
    CustomAlert.show({
      title: 'Antrenmanı Bitir',
      message: 'Devam eden antrenmanınızı sonlandırmak istiyor musunuz?',
      type: 'warning',
      buttons: [
        { text: 'Vazgeç', style: 'cancel' },
        {
          text: 'Bitir',
          style: 'destructive',
          onPress: () => {
            ActiveWorkoutManager.finishWorkout();
          },
        },
      ],
    });
  };

  return (
    <Animated.View
      style={[
        styles.container,
        { bottom: Math.max(85, insets.bottom + 65) },
        {
          transform: [
            { translateX: pan.x },
            { translateY: pan.y },
          ],
        },
      ]}
      {...panResponder.panHandlers}
    >
      <View style={styles.card}>
        <TouchableOpacity
          style={styles.mainClickArea}
          activeOpacity={0.85}
          onPress={handleExpand}
        >
          <View style={styles.iconContainer}>
            <Text style={{ fontSize: 16 }}>{workoutState.emoji || '🏃'}</Text>
          </View>
          <View style={styles.textContainer}>
            <Text style={styles.title} numberOfLines={1}>
              {workoutState.workoutTitle || workoutState.title || 'Antrenman'}
            </Text>
            <View style={styles.timeRow}>
              <Timer size={12} color={Colors.primary} />
              <Text style={styles.timeText}>{formatTime(workoutState.seconds)}</Text>
            </View>
          </View>
        </TouchableOpacity>

        {/* Action Controls: Expand & Close */}
        <View style={styles.controlsRow}>
          <TouchableOpacity
            style={styles.actionBtn}
            onPress={handleExpand}
            activeOpacity={0.7}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Maximize2 size={16} color={Colors.primary} />
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.actionBtn, styles.closeBtn]}
            onPress={handleClose}
            activeOpacity={0.7}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <X size={16} color={Colors.textSecondaryDark} />
          </TouchableOpacity>
        </View>
      </View>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: 90,
    right: 16,
    zIndex: 99999,
    elevation: 20,
  },
  card: {
    backgroundColor: '#1E1F25',
    borderRadius: 24,
    paddingVertical: 8,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.4,
    shadowRadius: 10,
    elevation: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.12)',
  },
  mainClickArea: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingRight: 10,
  },
  iconContainer: {
    backgroundColor: 'rgba(255, 96, 71, 0.15)',
    borderRadius: 16,
    padding: 6,
    width: 32,
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textContainer: {
    marginLeft: 10,
    maxWidth: 130,
  },
  title: {
    color: Colors.allWhite,
    fontSize: 13,
    fontWeight: '700',
  },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 2,
  },
  timeText: {
    color: Colors.primary,
    fontSize: 13,
    marginLeft: 4,
    fontWeight: '800',
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderLeftWidth: 1,
    borderLeftColor: 'rgba(255, 255, 255, 0.12)',
    paddingLeft: 10,
  },
  actionBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  closeBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
  },
});

