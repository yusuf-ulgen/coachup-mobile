import React from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity } from 'react-native';
import { Colors } from '../../theme/colors';

const SAMPLE_PROGRAMS = [
  { id: '1', title: 'Powerlifting - Basit Güç Programı', level: 'Orta Seviye', weeks: '8 Hafta' },
  { id: '2', title: 'Hypertrophy Bodybuilding A', level: 'İleri Seviye', weeks: '12 Hafta' },
  { id: '3', title: 'Yeni Başlayanlar İçin Full Body', level: 'Başlangıç', weeks: '4 Hafta' },
];

export const TrainingScreen: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.headerTitle}>Antrenman Programları</Text>

      <FlatList
        data={SAMPLE_PROGRAMS}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        renderItem={({ item }) => (
          <View style={styles.programCard}>
            <View style={styles.badgeRow}>
              <Text style={styles.levelBadge}>{item.level}</Text>
              <Text style={styles.weeksText}>{item.weeks}</Text>
            </View>
            <Text style={styles.cardTitle}>{item.title}</Text>

            <TouchableOpacity style={styles.detailButton}>
              <Text style={styles.detailButtonText}>Programı İncele</Text>
            </TouchableOpacity>
          </View>
        )}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
    paddingTop: 60,
    paddingHorizontal: 20,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '800',
    color: Colors.textDark,
    marginBottom: 20,
  },
  listContent: {
    paddingBottom: 24,
  },
  programCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 20,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  badgeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  levelBadge: {
    backgroundColor: Colors.primaryLight,
    color: Colors.primary,
    fontWeight: '700',
    fontSize: 12,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  weeksText: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 16,
  },
  detailButton: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  detailButtonText: {
    color: Colors.textDark,
    fontWeight: '600',
  },
});
