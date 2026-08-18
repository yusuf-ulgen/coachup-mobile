import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '../../theme/colors';
import { Trophy, Home, RotateCcw, CheckCircle2 } from 'lucide-react-native';
import { RecordAttemptService } from '../../services/recordAttemptService';

export const RecordAttemptSummaryScreen = ({ route, navigation }: any) => {
  const {
    attempt,
    exercise,
    recordType,
    targetValue,
    targetReps,
    success,
    isNewPR,
    rpe,
    elapsedSeconds,
  } = route.params || {};

  const typeStr = (recordType || 'weight').toLowerCase();
  const isWeight = typeStr === 'weight';
  const epley1RM = success && isWeight
    ? RecordAttemptService.epley1RM(targetValue || 0, targetReps || 1)
    : targetValue;

  const formatSubtitle = () => {
    const name = exercise?.name || 'Rekor Denemesi';
    if (isWeight) {
      return `${name} · ${targetValue || 0} kg × ${targetReps || 1}`;
    }
    if (typeStr === 'reps' || typeStr === 'bodyweight') {
      return `${name} · ${targetValue || 0} tekrar`;
    }
    if (typeStr === 'amrap') {
      return `${name} · ${targetReps || targetValue || 0} tur`;
    }
    if (
      typeStr === 'time' ||
      typeStr === 'distance' ||
      typeStr === 'running' ||
      typeStr === 'fixed_distance_time' ||
      typeStr === 'benchmark_time' ||
      typeStr === 'benchmark'
    ) {
      const totalSec = elapsedSeconds || targetValue || 0;
      const m = Math.floor(totalSec / 60);
      const s = totalSec % 60;
      return `${name} · ${m}:${s.toString().padStart(2, '0')}`;
    }
    if (typeStr === 'calories' || typeStr === 'cardio' || typeStr === 'fixed_calorie_time') {
      const totalSec = elapsedSeconds || 0;
      const m = Math.floor(totalSec / 60);
      const s = totalSec % 60;
      return `${name} · ${m}:${s.toString().padStart(2, '0')}`;
    }
    return `${name} · ${targetValue || ''}`;
  };

  const getTitle = () => {
    if (isNewPR) return 'YENİ REKOR!';
    if (success) return 'DENEME TAMAMLANDI!';
    return 'GÜZEL DENEME!';
  };

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        {isNewPR ? (
          <Trophy size={100} color="#FFD700" style={styles.icon} />
        ) : success ? (
          <CheckCircle2 size={100} color="#4CAF50" style={styles.icon} />
        ) : (
          <View
            style={[
              styles.icon,
              {
                width: 100,
                height: 100,
                borderRadius: 50,
                backgroundColor: Colors.cardDark,
                justifyContent: 'center',
                alignItems: 'center',
              },
            ]}
          >
            <Text style={{ fontSize: 40 }}>💪</Text>
          </View>
        )}

        <Text style={styles.title}>{getTitle()}</Text>

        <Text style={styles.subtitle}>{formatSubtitle()}</Text>

        <View style={styles.statsCard}>
          {success && isWeight && (
            <View style={styles.statRow}>
              <Text style={styles.statLabel}>Tahmini 1RM (Epley):</Text>
              <Text style={styles.statValue}>{epley1RM} kg</Text>
            </View>
          )}
          {!isWeight && (
            <View style={styles.statRow}>
              <Text style={styles.statLabel}>Sonuç:</Text>
              <Text style={styles.statValue}>
                {formatSubtitle().split('·')[1]?.trim() || 'Tamamlandı'}
              </Text>
            </View>
          )}
          {rpe !== undefined && rpe !== null && (
            <View style={styles.statRow}>
              <Text style={styles.statLabel}>RPE Zorluk:</Text>
              <Text style={styles.statValue}>{rpe}/10</Text>
            </View>
          )}
        </View>

        <View style={styles.actionRow}>
          <TouchableOpacity 
            style={[styles.btn, styles.secondaryBtn]} 
            onPress={() => navigation.navigate('MainTabs')}
          >
            <Home size={20} color={Colors.textDark} />
            <Text style={styles.secondaryBtnText}>Ana Sayfa</Text>
          </TouchableOpacity>
          
          <TouchableOpacity 
            style={[styles.btn, styles.primaryBtn]} 
            onPress={() => navigation.navigate('RecordAttemptSetup')}
          >
            <RotateCcw size={20} color={Colors.allWhite} />
            <Text style={styles.primaryBtnText}>Yeni Deneme</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.backgroundDark, justifyContent: 'center' },
  content: { padding: 30, alignItems: 'center' },
  icon: { marginBottom: 30 },
  title: { fontSize: 32, fontWeight: '900', color: Colors.textDark, marginBottom: 10, textAlign: 'center' },
  subtitle: { fontSize: 18, color: Colors.textSecondaryDark, marginBottom: 40, textAlign: 'center' },
  statsCard: { backgroundColor: Colors.cardDark, borderRadius: 20, padding: 25, width: '100%', marginBottom: 40, borderWidth: 1, borderColor: Colors.borderDark },
  statRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: Colors.borderDark },
  statLabel: { fontSize: 16, color: Colors.textSecondaryDark },
  statValue: { fontSize: 18, fontWeight: '700', color: Colors.primary },
  actionRow: { flexDirection: 'row', gap: 15, width: '100%' },
  btn: { flex: 1, paddingVertical: 18, borderRadius: 15, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 10 },
  primaryBtn: { backgroundColor: Colors.primary },
  secondaryBtn: { backgroundColor: Colors.cardDark, borderWidth: 1, borderColor: Colors.borderDark },
  primaryBtnText: { color: Colors.allWhite, fontSize: 16, fontWeight: '700' },
  secondaryBtnText: { color: Colors.textDark, fontSize: 16, fontWeight: '700' }
});
