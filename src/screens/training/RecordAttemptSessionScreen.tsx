import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import { Colors } from '../../theme/colors';
import { ArrowLeft, Check, X } from 'lucide-react-native';

export const RecordAttemptSessionScreen = ({ route, navigation }: any) => {
  const { exercise, recordType, targetValue, plan } = route.params || {};
  const [currentSetIndex, setCurrentSetIndex] = useState(0);
  const [rpe, setRpe] = useState(8);

  const currentSet = plan?.[currentSetIndex];
  const isMainSet = currentSet?.type === 'main';

  const handleNext = (success: boolean) => {
    if (currentSetIndex < plan.length - 1) {
      setCurrentSetIndex(currentSetIndex + 1);
    } else {
      navigation.navigate('RecordAttemptSummary', {
        exercise,
        recordType,
        targetValue,
        success,
        rpe
      });
    }
  };

  const renderPlateVisualizer = (weight: number) => {
    return (
      <View style={styles.plateContainer}>
        <View style={styles.bar} />
        <View style={[styles.plate, { height: 80, width: 20 }]} />
        <View style={[styles.plate, { height: 60, width: 15 }]} />
        <Text style={{ color: Colors.textDark, marginLeft: 10 }}>Bar + Plakalar: {weight}kg</Text>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{isMainSet ? '🔥 ANA SET 🔥' : 'Isınma Seti'}</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.card}>
          <Text style={styles.exerciseName}>{exercise?.name || 'Egzersiz'}</Text>
          <Text style={styles.setDetail}>
            Set {currentSetIndex + 1} / {plan?.length}
          </Text>
          
          <View style={styles.targetBox}>
            <Text style={styles.targetVal}>{currentSet?.weight || targetValue}</Text>
            <Text style={styles.targetLabel}>{recordType === 'weight' ? 'KG' : recordType}</Text>
          </View>
          
          {recordType === 'weight' && renderPlateVisualizer(currentSet?.weight || targetValue)}
        </View>

        {isMainSet && (
          <View style={styles.rpeCard}>
            <Text style={styles.rpeTitle}>RPE Zorluk (1-10)</Text>
            <View style={styles.rpeRow}>
              {[6,7,8,9,10].map(val => (
                <TouchableOpacity 
                  key={val} 
                  style={[styles.rpeBtn, rpe === val && styles.rpeBtnActive]}
                  onPress={() => setRpe(val)}
                >
                  <Text style={[styles.rpeBtnText, rpe === val && { color: Colors.allWhite }]}>{val}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}

        <View style={styles.actionRow}>
          <TouchableOpacity style={[styles.actionBtn, styles.failBtn]} onPress={() => handleNext(false)}>
            <X size={24} color={Colors.allWhite} />
            <Text style={styles.actionBtnText}>Yapamadım</Text>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.actionBtn, styles.successBtn]} onPress={() => handleNext(true)}>
            <Check size={24} color={Colors.allWhite} />
            <Text style={styles.actionBtnText}>Yaptım!</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.backgroundDark },
  header: { flexDirection: 'row', alignItems: 'center', padding: 20, paddingTop: 50, backgroundColor: Colors.cardDark },
  backBtn: { marginRight: 14 },
  headerTitle: { fontSize: 20, fontWeight: '700', color: Colors.primary },
  content: { padding: 20, alignItems: 'center' },
  card: { backgroundColor: Colors.cardDark, borderRadius: 20, padding: 30, width: '100%', alignItems: 'center', marginBottom: 20, borderWidth: 1, borderColor: Colors.borderDark },
  exerciseName: { fontSize: 24, fontWeight: 'bold', color: Colors.textDark, marginBottom: 10 },
  setDetail: { fontSize: 16, color: Colors.textSecondaryDark, marginBottom: 20 },
  targetBox: { backgroundColor: Colors.backgroundDark, padding: 30, borderRadius: 20, alignItems: 'center', minWidth: 150 },
  targetVal: { fontSize: 48, fontWeight: '900', color: Colors.primary },
  targetLabel: { fontSize: 18, color: Colors.textSecondaryDark, fontWeight: '600', marginTop: 5 },
  plateContainer: { flexDirection: 'row', alignItems: 'center', marginTop: 30, height: 100, justifyContent: 'center' },
  bar: { width: 100, height: 10, backgroundColor: '#666', position: 'absolute', zIndex: -1 },
  plate: { backgroundColor: Colors.primary, borderRadius: 5, marginHorizontal: 2 },
  rpeCard: { backgroundColor: Colors.cardDark, borderRadius: 20, padding: 20, width: '100%', marginBottom: 30 },
  rpeTitle: { fontSize: 16, fontWeight: '600', color: Colors.textDark, marginBottom: 15, textAlign: 'center' },
  rpeRow: { flexDirection: 'row', justifyContent: 'space-between' },
  rpeBtn: { width: 45, height: 45, borderRadius: 22.5, backgroundColor: Colors.backgroundDark, justifyContent: 'center', alignItems: 'center' },
  rpeBtnActive: { backgroundColor: Colors.primary },
  rpeBtnText: { color: Colors.textDark, fontWeight: '700', fontSize: 16 },
  actionRow: { flexDirection: 'row', width: '100%', justifyContent: 'space-between', gap: 15 },
  actionBtn: { flex: 1, paddingVertical: 20, borderRadius: 20, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 10 },
  successBtn: { backgroundColor: Colors.success },
  failBtn: { backgroundColor: Colors.error },
  actionBtnText: { color: Colors.allWhite, fontSize: 18, fontWeight: '700' }
});
