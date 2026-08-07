import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, ActivityIndicator } from 'react-native';
import { ArrowLeft, Flame, ChevronRight, Check, Target, Clock, Ruler, Activity } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { supabase } from '../../services/supabaseClient';
import { useAuth } from '../../context/AuthContext';

export const RecordAttemptSetupScreen = ({ navigation }: any) => {
  const { session } = useAuth();
  const [step, setStep] = useState<'category' | 'exercise' | 'config'>('category');
  
  const [categories, setCategories] = useState<any[]>([]);
  const [exercises, setExercises] = useState<any[]>([]);
  const [pastPRs, setPastPRs] = useState<any[]>([]);
  
  const [loading, setLoading] = useState(true);
  
  const [selectedCategory, setSelectedCategory] = useState<any>(null);
  const [selectedExercise, setSelectedExercise] = useState<any>(null);
  const [recordType, setRecordType] = useState<'weight' | 'reps' | 'time' | 'distance' | 'calories'>('weight');
  const [targetValue, setTargetValue] = useState(100);
  const [includeWarmup, setIncludeWarmup] = useState(true);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const { data: cats } = await supabase.from('categories').select('*');
      if (cats) setCategories(cats);
      
      const { data: exers } = await supabase.from('exercises').select('*');
      if (exers) setExercises(exers);
      
      if (session?.user?.id) {
        const { data: prs } = await supabase
          .from('personal_records')
          .select('*')
          .eq('user_id', session.user.id);
        if (prs) setPastPRs(prs);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const getRecordTypeIcon = (type: string) => {
    switch(type) {
      case 'weight': return <Target size={20} color={Colors.primary} />;
      case 'reps': return <Activity size={20} color={Colors.primary} />;
      case 'time': return <Clock size={20} color={Colors.primary} />;
      case 'distance': return <Ruler size={20} color={Colors.primary} />;
      case 'calories': return <Flame size={20} color={Colors.primary} />;
      default: return <Target size={20} color={Colors.primary} />;
    }
  };
  
  const getRecordTypeLabel = (type: string) => {
    switch(type) {
      case 'weight': return 'Ağırlık (kg)';
      case 'reps': return 'Tekrar';
      case 'time': return 'Süre (dk)';
      case 'distance': return 'Mesafe (km)';
      case 'calories': return 'Kalori (kcal)';
      default: return '';
    }
  };

  const calculateWarmupPlan = (targetWeight: number) => {
    if (!includeWarmup || recordType !== 'weight') return [];
    return [
      { weight: Math.round(targetWeight * 0.4), reps: 10, type: 'warmup' },
      { weight: Math.round(targetWeight * 0.6), reps: 5, type: 'warmup' },
      { weight: Math.round(targetWeight * 0.8), reps: 3, type: 'warmup' },
      { weight: Math.round(targetWeight * 0.9), reps: 1, type: 'warmup' },
      { weight: targetWeight, reps: 1, type: 'main' },
    ];
  };

  const handleStart = () => {
    const plan = calculateWarmupPlan(targetValue);
    navigation.navigate('RecordAttemptSession', {
      exercise: selectedExercise,
      recordType,
      targetValue,
      plan: plan.length ? plan : [{ weight: targetValue, reps: 1, type: 'main' }]
    });
  };

  const currentCategoryExercises = exercises.filter(e => e.category_id === selectedCategory?.id || e.category === selectedCategory?.id || e.category === selectedCategory?.name);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => {
            if (step === 'config') setStep('exercise');
            else if (step === 'exercise') setStep('category');
            else navigation?.goBack();
          }}
          style={styles.backBtn}
        >
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Rekor Denemesi</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {loading ? (
           <ActivityIndicator size="large" color={Colors.primary} style={{marginTop: 50}} />
        ) : (
          <>
            {step === 'category' && (
              <View>
                <Text style={styles.stepTitle}>Kategori Seçin</Text>
                <Text style={styles.stepSubtitle}>Hangi branşta rekor denemek istiyorsunuz?</Text>
                <View style={styles.list}>
                  {(categories.length ? categories : [
                    { id: 'bodybuilding', name: 'Vücut Geliştirme', icon: '🏋️' },
                    { id: 'powerlifting', name: 'Powerlifting', icon: '⚡' }
                  ]).map((cat) => (
                    <TouchableOpacity
                      key={cat.id}
                      style={styles.cardBtn}
                      onPress={() => {
                        setSelectedCategory(cat);
                        setStep('exercise');
                      }}
                    >
                      <Text style={styles.cardEmoji}>{cat.icon || '🔥'}</Text>
                      <Text style={styles.cardName}>{cat.name}</Text>
                      <ChevronRight size={18} color={Colors.textSecondaryDark} />
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            )}

            {step === 'exercise' && (
              <View>
                <Text style={styles.stepTitle}>Hareket Seçin</Text>
                <View style={styles.list}>
                  {(currentCategoryExercises.length ? currentCategoryExercises : [
                    { id: 'bench', name: 'Bench Press', category: 'bodybuilding', defaultVal: 100 }
                  ]).map((ex) => {
                    const pr = pastPRs.find(p => p.exercise_id === ex.id);
                    return (
                      <TouchableOpacity
                        key={ex.id}
                        style={styles.cardBtn}
                        onPress={() => {
                          setSelectedExercise(ex);
                          setTargetValue(ex.defaultVal || 100);
                          setStep('config');
                        }}
                      >
                        <Flame size={20} color={Colors.primary} />
                        <View style={{ flex: 1, marginLeft: 10 }}>
                          <Text style={styles.cardName}>{ex.name}</Text>
                          {pr && <Text style={{ color: Colors.textSecondaryDark, fontSize: 12 }}>Geçmiş PR: {pr.record_value}</Text>}
                        </View>
                        <ChevronRight size={18} color={Colors.textSecondaryDark} />
                      </TouchableOpacity>
                    );
                  })}
                </View>
              </View>
            )}

            {step === 'config' && (
              <View>
                <Text style={styles.stepTitle}>{selectedExercise?.name}</Text>
                
                <Text style={styles.inputLabel}>Rekor Tipi</Text>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 20 }}>
                  {['weight', 'reps', 'time', 'distance', 'calories'].map((type: any) => (
                    <TouchableOpacity
                      key={type}
                      style={[styles.typeBtn, recordType === type && styles.typeBtnActive]}
                      onPress={() => setRecordType(type)}
                    >
                      {getRecordTypeIcon(type)}
                      <Text style={[styles.typeBtnText, recordType === type && { color: Colors.allWhite }]}>
                        {getRecordTypeLabel(type)}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </ScrollView>

                <View style={styles.configCard}>
                  <Text style={styles.inputLabel}>Hedef {getRecordTypeLabel(recordType)}</Text>
                  <View style={styles.stepperRow}>
                    <TouchableOpacity style={styles.stepBtn} onPress={() => setTargetValue(Math.max(1, targetValue - 1))}>
                      <Text style={styles.stepBtnText}>-</Text>
                    </TouchableOpacity>
                    <Text style={styles.valText}>{targetValue}</Text>
                    <TouchableOpacity style={styles.stepBtn} onPress={() => setTargetValue(targetValue + 1)}>
                      <Text style={styles.stepBtnText}>+</Text>
                    </TouchableOpacity>
                  </View>

                  {recordType === 'weight' && (
                    <TouchableOpacity style={styles.warmupToggle} onPress={() => setIncludeWarmup(!includeWarmup)}>
                      <View style={[styles.checkbox, includeWarmup && styles.checkboxActive]}>
                        {includeWarmup && <Check size={14} color={Colors.allWhite} />}
                      </View>
                      <Text style={styles.warmupText}>Isınma planı oluştur (Plate Calculator)</Text>
                    </TouchableOpacity>
                  )}
                </View>

                <TouchableOpacity style={styles.startBtn} onPress={handleStart}>
                  <Text style={styles.startBtnText}>Rekor Denemesini Başlat</Text>
                </TouchableOpacity>
              </View>
            )}
          </>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.backgroundDark },
  header: { flexDirection: 'row', alignItems: 'center', padding: 20, paddingTop: 50, backgroundColor: Colors.cardDark },
  backBtn: { marginRight: 14 },
  headerTitle: { fontSize: 20, fontWeight: '700', color: Colors.textDark },
  content: { padding: 20 },
  stepTitle: { fontSize: 22, fontWeight: '700', color: Colors.textDark, marginBottom: 8 },
  stepSubtitle: { fontSize: 13, color: Colors.textSecondaryDark, marginBottom: 20 },
  list: { gap: 12 },
  cardBtn: { flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.cardDark, borderRadius: 16, padding: 16, borderWidth: 1, borderColor: Colors.borderDark },
  cardEmoji: { fontSize: 22, marginRight: 12 },
  cardName: { fontSize: 16, fontWeight: '600', color: Colors.textDark },
  configCard: { backgroundColor: Colors.cardDark, borderRadius: 20, padding: 20, marginBottom: 24, borderWidth: 1, borderColor: Colors.borderDark },
  inputLabel: { fontSize: 14, fontWeight: '600', color: Colors.textDark, marginBottom: 10 },
  stepperRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: Colors.backgroundDark, borderRadius: 14, padding: 8 },
  stepBtn: { width: 44, height: 44, borderRadius: 10, backgroundColor: Colors.cardDark, justifyContent: 'center', alignItems: 'center' },
  stepBtnText: { fontSize: 20, fontWeight: '700', color: Colors.textDark },
  valText: { fontSize: 20, fontWeight: '800', color: Colors.primary },
  typeBtn: { flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.cardDark, paddingHorizontal: 16, paddingVertical: 12, borderRadius: 20, marginRight: 10, borderWidth: 1, borderColor: Colors.borderDark },
  typeBtnActive: { backgroundColor: Colors.primary, borderColor: Colors.primary },
  typeBtnText: { marginLeft: 8, color: Colors.textDark, fontWeight: '600' },
  warmupToggle: { flexDirection: 'row', alignItems: 'center', marginTop: 20, gap: 10 },
  checkbox: { width: 22, height: 22, borderRadius: 6, borderWidth: 2, borderColor: Colors.textSecondaryDark, justifyContent: 'center', alignItems: 'center' },
  checkboxActive: { backgroundColor: Colors.primary, borderColor: Colors.primary },
  warmupText: { fontSize: 14, color: Colors.textDark, fontWeight: '500' },
  startBtn: { backgroundColor: Colors.primary, borderRadius: 100, paddingVertical: 16, alignItems: 'center' },
  startBtnText: { color: Colors.allWhite, fontWeight: '700', fontSize: 16 }
});
