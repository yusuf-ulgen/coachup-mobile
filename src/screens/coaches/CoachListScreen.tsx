import React from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity } from 'react-native';
import { Colors } from '../../theme/colors';

const SAMPLE_COACHES = [
  { id: '1', name: 'Mustafa Yılmaz', branch: 'Fitness & Vücut Geliştirme', rating: '4.9 ⭐' },
  { id: '2', name: 'Elif Kaya', branch: 'Pilates & Mobilite', rating: '5.0 ⭐' },
  { id: '3', name: 'Caner Demir', branch: 'CrossFit & Kondisyon', rating: '4.8 ⭐' },
];

export const CoachListScreen: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.headerTitle}>Kulüp Koçları</Text>

      <FlatList
        data={SAMPLE_COACHES}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <View style={styles.coachCard}>
            <View style={styles.avatarPlaceholder}>
              <Text style={styles.avatarText}>{item.name[0]}</Text>
            </View>
            <View style={styles.coachInfo}>
              <Text style={styles.coachName}>{item.name}</Text>
              <Text style={styles.coachBranch}>{item.branch}</Text>
              <Text style={styles.coachRating}>{item.rating}</Text>
            </View>
            <TouchableOpacity style={styles.bookButton}>
              <Text style={styles.bookButtonText}>Randevu</Text>
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
  coachCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  avatarPlaceholder: {
    width: 50,
    height: 50,
    borderRadius: 25,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },
  avatarText: {
    color: Colors.allWhite,
    fontWeight: '800',
    fontSize: 20,
  },
  coachInfo: {
    flex: 1,
  },
  coachName: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  coachBranch: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginVertical: 2,
  },
  coachRating: {
    fontSize: 12,
    color: Colors.warning,
    fontWeight: '600',
  },
  bookButton: {
    backgroundColor: Colors.primary,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  bookButtonText: {
    color: Colors.allWhite,
    fontWeight: '700',
    fontSize: 13,
  },
});
