import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import {
  ArrowLeft,
  Play,
  Pause,
  CheckCircle2,
  Clock,
  Flame,
  Award,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { supabase } from '../../services/supabaseClient';

interface ActiveWorkoutScreenProps {
  route?: any;
  navigation?: any;
}

export const ActiveWorkoutScreen: React.FC<ActiveWorkoutScreenProps> = ({
  route,
  navigation,
}) => {
  const workoutTitle = route?.params?.title || 'Aktif Antrenman';
  const category = route?.params?.category || 'Genel';

  const [seconds, setSeconds] = useState(0);
  const [isActive, setIsActive] = useState(true);
  const [completedSets, setCompletedSets] = useState(0);
  const [totalSets, setTotalSets] = useState(4);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let interval: any = null;
    if (isActive) {
      interval = setInterval(() => {
        setSeconds((sec) => sec + 1);
      }, 1000);
    } else {
      clearInterval(interval);
    }
    return () => clearInterval(interval);
  }, [isActive]);

  const formatTime = (totalSeconds: number) => {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  const handleFinishWorkout = async () => {
    setIsActive(false);
    setSaving(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (user) {
        // Record session in user_activities and training_sessions
        await supabase.from('user_activities').insert({
          user_id: user.id,
          activity_type: category,
          duration: Math.ceil(seconds / 60),
          calories_burned: Math.ceil((seconds / 60) * 8),
          activity_date: new Date().toISOString().split('T')[0],
        });
      }
      Alert.alert(
        'Tebrikler! 🎉',
        `Antrenman başarıyla tamamlandı.\nSüre: ${formatTime(seconds)}`,
        [
          {
            text: 'Tamam',
            onPress: () => navigation?.goBack(),
          },
        ]
      );
    } catch (e) {
      console.error('Error saving workout session:', e);
      navigation?.goBack();
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backButton}>
          <ArrowLeft size={24} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{workoutTitle}</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Timer Card */}
        <View style={styles.timerCard}>
          <Clock size={32} color={Colors.primary} />
          <Text style={styles.timerText}>{formatTime(seconds)}</Text>
          <Text style={styles.timerSublabel}>Geçen Süre</Text>

          <View style={styles.timerControls}>
            <TouchableOpacity
              style={styles.controlBtn}
              onPress={() => setIsActive(!isActive)}
              activeOpacity={0.8}
            >
              {isActive ? (
                <Pause size={24} color={Colors.allWhite} />
              ) : (
                <Play size={24} color={Colors.allWhite} />
              )}
            </TouchableOpacity>
          </View>
        </View>

        {/* Workout Stats Grid */}
        <View style={styles.statsGrid}>
          <View style={styles.statBox}>
            <Flame size={22} color="#FF9800" />
            <Text style={styles.statVal}>{Math.ceil((seconds / 60) * 8)} kcal</Text>
            <Text style={styles.statLbl}>Tahmini Kalori</Text>
          </View>
          <View style={styles.statBox}>
            <Award size={22} color={Colors.primary} />
            <Text style={styles.statVal}>
              {completedSets} / {totalSets} Set
            </Text>
            <Text style={styles.statLbl}>Tamamlanan</Text>
          </View>
        </View>

        {/* Set Tracker List */}
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Set Takibi</Text>
        </View>

        {[1, 2, 3, 4].map((setNum) => {
          const isDone = completedSets >= setNum;
          return (
            <TouchableOpacity
              key={setNum}
              style={[styles.setRow, isDone && styles.setRowDone]}
              onPress={() => {
                if (completedSets < setNum) {
                  setCompletedSets(setNum);
                } else {
                  setCompletedSets(setNum - 1);
                }
              }}
              activeOpacity={0.8}
            >
              <View style={styles.setLeft}>
                <Text style={[styles.setNumText, isDone && { color: Colors.primary }]}>
                  Set {setNum}
                </Text>
                <Text style={styles.setSubText}>12 Tekrar · 60 kg</Text>
              </View>
              <CheckCircle2
                size={24}
                color={isDone ? Colors.primary : Colors.textSecondaryDark}
              />
            </TouchableOpacity>
          );
        })}

        {/* Complete Workout Button */}
        <TouchableOpacity
          style={styles.finishBtn}
          onPress={handleFinishWorkout}
          disabled={saving}
          activeOpacity={0.85}
        >
          {saving ? (
            <ActivityIndicator color={Colors.allWhite} />
          ) : (
            <Text style={styles.finishBtnText}>Antrenmanı Bitir</Text>
          )}
        </TouchableOpacity>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
  },
  backButton: {
    marginRight: 14,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 100,
  },
  timerCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 32,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
    marginBottom: 20,
  },
  timerText: {
    fontSize: 48,
    fontWeight: '900',
    color: Colors.textDark,
    marginVertical: 8,
  },
  timerSublabel: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
  },
  timerControls: {
    marginTop: 20,
  },
  controlBtn: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  statsGrid: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 24,
  },
  statBox: {
    flex: 1,
    backgroundColor: Colors.cardDark,
    borderRadius: 18,
    padding: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  statVal: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
    marginTop: 6,
  },
  statLbl: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  sectionHeader: {
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  setRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  setRowDone: {
    borderColor: Colors.primary,
    backgroundColor: 'rgba(255, 96, 71, 0.06)',
  },
  setLeft: {},
  setNumText: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  setSubText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  finishBtn: {
    backgroundColor: Colors.primary,
    borderRadius: 100,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 24,
  },
  finishBtnText: {
    color: Colors.allWhite,
    fontWeight: '700',
    fontSize: 16,
  },
});
