import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator } from 'react-native';
import { ChevronLeft, Trophy, Calendar } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { useNavigation, useRoute, useFocusEffect } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAuth } from '../../context/AuthContext';
import { ResultsService, ExerciseDetailData } from '../../services/resultsService';
import Svg, { Circle, Polyline } from 'react-native-svg';

export const ResultDetailScreen = () => {
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  const { session } = useAuth();
  const userId = session?.user?.id;

  const { exerciseId, exerciseName = 'Egzersiz Detayı', isKg = true } = route.params || {};

  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<ExerciseDetailData | null>(null);

  const loadDetail = useCallback(async () => {
    if (!userId || !exerciseId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const res = await ResultsService.fetchExerciseDetail(userId, exerciseId, exerciseName);
      setData(res);
    } catch (e) {
      console.error('[ResultDetailScreen] Error loading detail:', e);
    } finally {
      setLoading(false);
    }
  }, [userId, exerciseId, exerciseName]);

  useFocusEffect(
    useCallback(() => {
      loadDetail();
    }, [loadDetail])
  );

  const getVal = (val: number) => (isKg ? val : Math.round(val * 2.20462));
  const unit = isKg ? 'kg' : 'lbs';

  const isWeight = data?.isWeight ?? true;
  const maxWeight = data?.maxWeight ?? 0;
  const maxReps = data?.maxReps ?? 1;
  const oneRM = data?.oneRM ?? 0;
  const percentages = data?.percentages ?? [];
  const chartPointsList = data?.chartPointsList ?? [];
  const chartPointsString = data?.chartPointsString ?? '0,100';
  const history = data?.history ?? [];

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ChevronLeft color={Colors.textPrimaryDark} size={24} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{data?.exerciseName || exerciseName}</Text>
        <View style={{ width: 24 }} />
      </View>

      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={[styles.scrollContent, { paddingBottom: 24 + insets.bottom }]}>
          {/* En İyi Performans Kartı */}
          <View style={styles.maxCard}>
            <Text style={styles.cardTitle}>En İyi Performans</Text>
            {isWeight ? (
              <View style={styles.maxGrid}>
                <View style={styles.maxItem}>
                  <Text style={styles.maxLabel}>Max Ağırlık</Text>
                  <Text style={styles.maxValue}>
                    {maxWeight > 0 ? `${getVal(maxWeight)} ${unit}` : '-'}
                  </Text>
                </View>
                <View style={styles.maxItem}>
                  <Text style={styles.maxLabel}>Max Tekrar</Text>
                  <Text style={styles.maxValue}>{maxWeight > 0 ? maxReps : '-'}</Text>
                </View>
                <View style={styles.maxItem}>
                  <Text style={styles.maxLabel}>Tahmini 1RM</Text>
                  <Text style={styles.maxValue}>
                    {oneRM > 0 ? `${getVal(oneRM)} ${unit}` : '-'}
                  </Text>
                </View>
              </View>
            ) : (
              <View style={styles.maxGrid}>
                <View style={styles.maxItem}>
                  <Text style={styles.maxLabel}>En İyi Sonuç</Text>
                  <Text style={styles.maxValue}>{data?.bestValueDisplay || '-'}</Text>
                </View>
                <View style={styles.maxItem}>
                  <Text style={styles.maxLabel}>Kategori</Text>
                  <Text style={styles.maxValue}>{data?.category || 'Genel'}</Text>
                </View>
                <View style={styles.maxItem}>
                  <Text style={styles.maxLabel}>Toplam Kayıt</Text>
                  <Text style={styles.maxValue}>
                    {history.reduce((acc, h) => acc + h.sessions.length, 0)}
                  </Text>
                </View>
              </View>
            )}
          </View>

          {/* 1RM Yüzdelikler Grid — ONLY for Weight exercises */}
          {isWeight && oneRM > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>1RM Yüzdeleri</Text>
              <View style={styles.percentagesGrid}>
                {percentages.map((item, index) => (
                  <View key={index} style={styles.percentCard}>
                    <Text style={styles.percentText}>%{item.p}</Text>
                    <Text style={styles.percentVal}>
                      {Math.round(getVal(item.val))} {unit}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          )}

          {/* 90 Günlük İlerleme Çizgi Grafiği (SVG) */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>90 Günlük İlerleme</Text>
            <View style={styles.chartContainer}>
              {chartPointsList.length >= 2 ? (
                <>
                  <Svg height="150" width="100%" viewBox="0 0 100 120" preserveAspectRatio="none">
                    <Polyline
                      points={chartPointsString}
                      fill="none"
                      stroke={Colors.primary}
                      strokeWidth="2"
                    />
                    {chartPointsList.map((pt, idx) => (
                      <Circle key={idx} cx={pt.x} cy={pt.y} r="3" fill={Colors.primary} />
                    ))}
                  </Svg>
                  <View style={styles.chartLabels}>
                    <Text style={styles.chartLabel}>3 Ay Önce</Text>
                    <Text style={styles.chartLabel}>Bugün</Text>
                  </View>
                </>
              ) : chartPointsList.length === 1 ? (
                <View style={{ paddingVertical: 24, alignItems: 'center' }}>
                  <Text style={{ color: Colors.primary, fontWeight: '700', fontSize: 18, marginBottom: 4 }}>
                    {isWeight ? `${getVal(chartPointsList[0].value)} ${unit}` : chartPointsList[0].formattedValue}
                  </Text>
                  <Text style={{ color: Colors.textSecondaryDark, fontSize: 12 }}>
                    Kayıt tarihi: {chartPointsList[0].date}
                  </Text>
                </View>
              ) : (
                <View style={{ paddingVertical: 24, alignItems: 'center' }}>
                  <Text style={{ color: Colors.textSecondaryDark, fontSize: 13 }}>
                    Son 90 günde kayıtlı antrenman verisi bulunmuyor.
                  </Text>
                </View>
              )}
            </View>
          </View>

          {/* Yıllara Göre Geçmiş */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Geçmiş</Text>
            {history.length === 0 ? (
              <View style={styles.emptyHistoryBox}>
                <Calendar size={32} color={Colors.textSecondaryDark} style={{ marginBottom: 8, opacity: 0.6 }} />
                <Text style={styles.emptyHistoryText}>Bu egzersiz için henüz geçmiş kayıt bulunmuyor.</Text>
              </View>
            ) : (
              history.map((h, i) => (
                <View key={i} style={styles.historyGroup}>
                  <Text style={styles.historyYear}>{h.year}</Text>
                  {h.sessions.map((session, j) => (
                    <View key={j} style={styles.historyRow}>
                      <Text style={styles.historyDate}>{session.date}</Text>
                      <Text style={styles.historySets}>{session.sets}</Text>
                      <Text style={styles.historyWeight}>
                        {isWeight && session.weight > 0
                          ? `${getVal(session.weight)} ${unit}`
                          : session.valueDisplay}
                      </Text>
                    </View>
                  ))}
                </View>
              ))
            )}
          </View>
        </ScrollView>
      )}
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
    justifyContent: 'space-between',
    padding: 16,
    paddingTop: 48,
    backgroundColor: Colors.cardDark,
  },
  backButton: {
    padding: 4,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: Colors.textPrimaryDark,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  maxCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 16,
    marginBottom: 24,
  },
  cardTitle: {
    color: Colors.textPrimaryDark,
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 16,
  },
  maxGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  maxItem: {
    alignItems: 'center',
  },
  maxLabel: {
    color: Colors.textSecondaryDark,
    fontSize: 12,
    marginBottom: 4,
  },
  maxValue: {
    color: Colors.primary,
    fontSize: 20,
    fontWeight: 'bold',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: Colors.textPrimaryDark,
    marginBottom: 12,
  },
  percentagesGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  percentCard: {
    width: '30%',
    backgroundColor: Colors.cardDark,
    paddingVertical: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  percentText: {
    color: Colors.textSecondaryDark,
    fontSize: 12,
    marginBottom: 4,
  },
  percentVal: {
    color: Colors.textPrimaryDark,
    fontSize: 16,
    fontWeight: 'bold',
  },
  chartContainer: {
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  chartLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
    marginTop: 8,
  },
  chartLabel: {
    color: Colors.textSecondaryDark,
    fontSize: 11,
  },
  historyGroup: {
    marginBottom: 16,
  },
  historyYear: {
    color: Colors.primary,
    fontSize: 14,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  historyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: Colors.cardDark,
    padding: 12,
    borderRadius: 8,
    marginBottom: 8,
  },
  historyDate: {
    color: Colors.textPrimaryDark,
    width: 60,
  },
  historySets: {
    color: Colors.textSecondaryDark,
    flex: 1,
    textAlign: 'center',
  },
  historyWeight: {
    color: Colors.textPrimaryDark,
    fontWeight: 'bold',
  },
  emptyHistoryBox: {
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyHistoryText: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
    textAlign: 'center',
  },
});
