import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { ChevronLeft } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { useNavigation, useRoute } from '@react-navigation/native';
import Svg, { Path, Circle, Polyline } from 'react-native-svg';

export const ResultDetailScreen = () => {
  const navigation = useNavigation();
  const route = useRoute<any>();
  const { exerciseName = 'Egzersiz Detayı', isKg = true } = route.params || {};

  const maxWeight = 100;
  const maxReps = 12;
  const oneRM = 115;

  const getVal = (val: number) => isKg ? val : Math.round(val * 2.20462);
  const unit = isKg ? 'kg' : 'lbs';

  const percentages = [
    { p: 100, val: oneRM },
    { p: 90, val: oneRM * 0.9 },
    { p: 80, val: oneRM * 0.8 },
    { p: 70, val: oneRM * 0.7 },
    { p: 60, val: oneRM * 0.6 },
    { p: 50, val: oneRM * 0.5 },
  ];

  // 90 günlük canvas (SVG) için örnek noktalar (X: gün, Y: ağırlık)
  const chartPoints = "0,100 20,80 40,90 60,60 80,40 100,20";
  
  const history = [
    { year: '2026', sessions: [
      { date: '12 Ağu', sets: '3 set x 10 tekrar', weight: 90 },
      { date: '05 Ağu', sets: '4 set x 8 tekrar', weight: 95 },
    ]},
    { year: '2025', sessions: [
      { date: '20 Tem', sets: '3 set x 12 tekrar', weight: 80 },
    ]},
  ];

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ChevronLeft color={Colors.textPrimaryDark} size={24} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{exerciseName}</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* En Ağır Kaldırma Kartı */}
        <View style={styles.maxCard}>
          <Text style={styles.cardTitle}>En İyi Performans</Text>
          <View style={styles.maxGrid}>
            <View style={styles.maxItem}>
              <Text style={styles.maxLabel}>Max Ağırlık</Text>
              <Text style={styles.maxValue}>{getVal(maxWeight)}{unit}</Text>
            </View>
            <View style={styles.maxItem}>
              <Text style={styles.maxLabel}>Max Tekrar</Text>
              <Text style={styles.maxValue}>{maxReps}</Text>
            </View>
            <View style={styles.maxItem}>
              <Text style={styles.maxLabel}>Tahmini 1RM</Text>
              <Text style={styles.maxValue}>{getVal(oneRM)}{unit}</Text>
            </View>
          </View>
        </View>

        {/* 1RM Yüzdelikler Grid */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>1RM Yüzdeleri</Text>
          <View style={styles.percentagesGrid}>
            {percentages.map((item, index) => (
              <View key={index} style={styles.percentCard}>
                <Text style={styles.percentText}>%{item.p}</Text>
                <Text style={styles.percentVal}>{Math.round(getVal(item.val))}{unit}</Text>
              </View>
            ))}
          </View>
        </View>

        {/* 90 Günlük İlerleme Çizgi Grafiği (SVG) */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>90 Günlük İlerleme</Text>
          <View style={styles.chartContainer}>
            <Svg height="150" width="100%" viewBox="0 0 100 120" preserveAspectRatio="none">
              <Polyline
                points={chartPoints}
                fill="none"
                stroke={Colors.primary}
                strokeWidth="2"
              />
              <Circle cx="0" cy="100" r="3" fill={Colors.primary} />
              <Circle cx="20" cy="80" r="3" fill={Colors.primary} />
              <Circle cx="40" cy="90" r="3" fill={Colors.primary} />
              <Circle cx="60" cy="60" r="3" fill={Colors.primary} />
              <Circle cx="80" cy="40" r="3" fill={Colors.primary} />
              <Circle cx="100" cy="20" r="3" fill={Colors.primary} />
            </Svg>
            <View style={styles.chartLabels}>
              <Text style={styles.chartLabel}>3 Ay Önce</Text>
              <Text style={styles.chartLabel}>Bugün</Text>
            </View>
          </View>
        </View>

        {/* Yıllara Göre Geçmiş */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Geçmiş</Text>
          {history.map((h, i) => (
            <View key={i} style={styles.historyGroup}>
              <Text style={styles.historyYear}>{h.year}</Text>
              {h.sessions.map((session, j) => (
                <View key={j} style={styles.historyRow}>
                  <Text style={styles.historyDate}>{session.date}</Text>
                  <Text style={styles.historySets}>{session.sets}</Text>
                  <Text style={styles.historyWeight}>{getVal(session.weight)}{unit}</Text>
                </View>
              ))}
            </View>
          ))}
        </View>
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
});
