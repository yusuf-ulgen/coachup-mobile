import React, { useRef, useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  PanResponder,
  TouchableOpacity,
} from 'react-native';
import { Timer, Activity, Maximize2, X } from 'lucide-react-native';
import { Colors } from '../theme/colors';
import { useNavigation } from '@react-navigation/native';
import { ActiveWorkoutManager } from '../services/activeWorkoutManager';
import { CustomAlert } from './CustomAlertModal';

export const FloatingActiveWorkoutOverlay: React.FC = () => {
  const pan = useRef(new Animated.ValueXY()).current;
  const navigation = useNavigation<any>();
  const [workoutState, setWorkoutState] = useState(() => ActiveWorkoutManager.getState());

  useEffect(() => {
    return ActiveWorkoutManager.subscribe(() => {
      setWorkoutState(ActiveWorkoutManager.getState());
    });
  }, []);

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => {
        return Math.abs(gestureState.dx) > 5 || Math.abs(gestureState.dy) > 5;
      },
      onPanResponderGrant: () => {
        pan.setOffset({
          x: (pan.x as any)._value,
          y: (pan.y as any)._value,
        });
      },
      onPanResponderMove: Animated.event(
        [null, { dx: pan.x, dy: pan.y }],
        { useNativeDriver: false }
      ),
      onPanResponderRelease: () => {
        pan.flattenOffset();
      },
    })
  ).current;

  // Only show when there is an active session and user is NOT on ActiveWorkout screen
  if (!ActiveWorkoutManager.shouldShowOverlay()) {
    return null;
  }

  const formatTime = (totalSeconds: number) => {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  const handleExpand = () => {
    navigation.navigate('ActiveWorkout', {
      sessionId: workoutState.sessionId,
      programId: workoutState.programId,
      title: workoutState.title,
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
        {
          transform: [{ translateX: pan.x }, { translateY: pan.y }],
        },
      ]}
      {...panResponder.panHandlers}
    >
      <View style={styles.card}>
        <TouchableOpacity
          style={styles.mainClickArea}
          activeOpacity={0.8}
          onPress={handleExpand}
        >
          <View style={styles.iconContainer}>
            <Activity size={18} color={Colors.primary} />
          </View>
          <View style={styles.textContainer}>
            <Text style={styles.title} numberOfLines={1}>
              {workoutState.title || 'Antrenman Devam Ediyor'}
            </Text>
            <View style={styles.timeRow}>
              <Timer size={13} color={Colors.textSecondaryDark} />
              <Text style={styles.timeText}>{formatTime(workoutState.seconds)}</Text>
            </View>
          </View>
        </TouchableOpacity>

        {/* Action Controls */}
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
    bottom: 100, // Tab barın üzerinde
    right: 16,
    zIndex: 9999,
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
    elevation: 10,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
  },
  mainClickArea: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingRight: 10,
  },
  iconContainer: {
    backgroundColor: 'rgba(255, 107, 0, 0.15)',
    borderRadius: 16,
    padding: 6,
  },
  textContainer: {
    marginLeft: 10,
    maxWidth: 130,
  },
  title: {
    color: Colors.textDark,
    fontSize: 13,
    fontWeight: '600',
  },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 2,
  },
  timeText: {
    color: Colors.primary,
    fontSize: 12,
    marginLeft: 4,
    fontWeight: '700',
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderLeftWidth: 1,
    borderLeftColor: 'rgba(255, 255, 255, 0.1)',
    paddingLeft: 10,
  },
  actionBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  closeBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
  },
});
