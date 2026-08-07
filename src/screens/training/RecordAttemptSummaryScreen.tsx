import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '../../theme/colors';
import { Trophy, Home, RotateCcw } from 'lucide-react-native';

export const RecordAttemptSummaryScreen = ({ route, navigation }: any) => {
  const { exercise, recordType, targetValue, success, rpe } = route.params || {};

  const epley1RM = success && recordType === 'weight' ? Math.round(targetValue * (1 + 1 / 30)) : targetValue;

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        {success ? (
          <Trophy size={100} color="#FFD700" style={styles.icon} />
        ) : (
          <View style={[styles.icon, { width: 100, height: 100, borderRadius: 50, backgroundColor: Colors.cardDark, justifyContent: 'center', alignItems: 'center' }]}>
            <Text style={{ fontSize: 40 }}>💪</Text>
          </View>
        )}
        
        <Text style={styles.title}>
          {success ? 'YENİ REKOR!' : 'GÜZEL DENEME!'}
        </Text>
        
        <Text style={styles.subtitle}>
          {exercise?.name} - {targetValue} {recordType}
        </Text>

        <View style={styles.statsCard}>
          {success && recordType === 'weight' && (
            <View style={styles.statRow}>
              <Text style={styles.statLabel}>Tahmini 1RM (Epley):</Text>
              <Text style={styles.statValue}>{epley1RM} kg</Text>
            </View>
          )}
          <View style={styles.statRow}>
            <Text style={styles.statLabel}>RPE Zorluk:</Text>
            <Text style={styles.statValue}>{rpe}/10</Text>
          </View>
        </View>

        <View style={styles.actionRow}>
          <TouchableOpacity 
            style={[styles.btn, styles.secondaryBtn]} 
            onPress={() => navigation.navigate('HomeTab')}
          >
            <Home size={20} color={Colors.textDark} />
            <Text style={styles.secondaryBtnText}>Ana Sayfa</Text>
          </TouchableOpacity>
          
          <TouchableOpacity 
            style={[styles.btn, styles.primaryBtn]} 
            onPress={() => navigation.navigate('PersonalRecords')}
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
