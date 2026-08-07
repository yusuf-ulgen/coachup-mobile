import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, TextInput } from 'react-native';
import { Search, ChevronLeft, Dumbbell } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { useNavigation } from '@react-navigation/native';

export const ResultsScreen = () => {
  const navigation = useNavigation();
  const [isKg, setIsKg] = useState(true);
  const [activeTab, setActiveTab] = useState<'my_results' | 'all'>('my_results');
  const [searchQuery, setSearchQuery] = useState('');

  const summary = {
    totalWorkouts: 45,
    maxWeight: 140,
    max1RM: 155
  };

  const exercises = [
    { id: 1, name: 'Bench Press', category: 'Göğüs', best: 100 },
    { id: 2, name: 'Squat', category: 'Bacak', best: 140 },
    { id: 3, name: 'Deadlift', category: 'Sırt', best: 150 },
  ];

  const filteredExercises = exercises.filter(ex => ex.name.toLowerCase().includes(searchQuery.toLowerCase()));

  const toggleUnit = () => setIsKg(!isKg);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ChevronLeft color={Colors.textPrimaryDark} size={24} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Sonuçlar</Text>
        <TouchableOpacity onPress={toggleUnit} style={styles.unitToggle}>
          <Text style={styles.unitText}>{isKg ? 'KG' : 'LBS'}</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Üst Özet Bandı */}
        <View style={styles.summaryBand}>
          <View style={styles.summaryItem}>
            <Text style={styles.summaryValue}>{summary.totalWorkouts}</Text>
            <Text style={styles.summaryLabel}>Egzersiz</Text>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryValue}>
              {isKg ? summary.maxWeight : Math.round(summary.maxWeight * 2.20462)}
            </Text>
            <Text style={styles.summaryLabel}>Max {isKg ? 'kg' : 'lbs'}</Text>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryValue}>
              {isKg ? summary.max1RM : Math.round(summary.max1RM * 2.20462)}
            </Text>
            <Text style={styles.summaryLabel}>1RM {isKg ? 'kg' : 'lbs'}</Text>
          </View>
        </View>

        {/* Tablar */}
        <View style={styles.tabsContainer}>
          <TouchableOpacity 
            style={[styles.tab, activeTab === 'my_results' && styles.tabActive]}
            onPress={() => setActiveTab('my_results')}
          >
            <Text style={[styles.tabText, activeTab === 'my_results' && styles.tabTextActive]}>
              Sonuçlarım
            </Text>
          </TouchableOpacity>
          <TouchableOpacity 
            style={[styles.tab, activeTab === 'all' && styles.tabActive]}
            onPress={() => setActiveTab('all')}
          >
            <Text style={[styles.tabText, activeTab === 'all' && styles.tabTextActive]}>
              Tüm Egzersizler
            </Text>
          </TouchableOpacity>
        </View>

        {/* Egzersiz Arama Çubuğu */}
        <View style={styles.searchContainer}>
          <Search size={20} color={Colors.textSecondaryDark} style={styles.searchIcon} />
          <TextInput
            style={styles.searchInput}
            placeholder="Egzersiz ara..."
            placeholderTextColor={Colors.textSecondaryDark}
            value={searchQuery}
            onChangeText={setSearchQuery}
          />
        </View>

        {/* Egzersiz Kartları */}
        <View style={styles.listContainer}>
          {filteredExercises.map(ex => (
            <TouchableOpacity 
              key={ex.id} 
              style={styles.exerciseCard}
              onPress={() => (navigation as any).navigate('ResultDetail', { exerciseId: ex.id, exerciseName: ex.name, isKg })}
            >
              <View style={styles.exerciseIcon}>
                <Dumbbell size={24} color={Colors.primary} />
              </View>
              <View style={styles.exerciseInfo}>
                <Text style={styles.exerciseName}>{ex.name}</Text>
                <Text style={styles.exerciseCategory}>{ex.category}</Text>
              </View>
              <View style={styles.exerciseBest}>
                <Text style={styles.bestValue}>
                  {isKg ? ex.best : Math.round(ex.best * 2.20462)} {isKg ? 'kg' : 'lbs'}
                </Text>
                <Text style={styles.bestLabel}>En İyi</Text>
              </View>
            </TouchableOpacity>
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
  unitToggle: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: Colors.primary + '30',
    borderRadius: 8,
  },
  unitText: {
    color: Colors.primary,
    fontWeight: 'bold',
    fontSize: 14,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  summaryBand: {
    flexDirection: 'row',
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 16,
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  summaryItem: {
    flex: 1,
    alignItems: 'center',
  },
  summaryValue: {
    color: Colors.textPrimaryDark,
    fontSize: 24,
    fontWeight: 'bold',
  },
  summaryLabel: {
    color: Colors.textSecondaryDark,
    fontSize: 12,
    marginTop: 4,
  },
  summaryDivider: {
    width: 1,
    height: 40,
    backgroundColor: Colors.borderDark,
  },
  tabsContainer: {
    flexDirection: 'row',
    backgroundColor: Colors.cardDark,
    borderRadius: 8,
    padding: 4,
    marginBottom: 20,
  },
  tab: {
    flex: 1,
    paddingVertical: 8,
    alignItems: 'center',
    borderRadius: 6,
  },
  tabActive: {
    backgroundColor: Colors.primary,
  },
  tabText: {
    color: Colors.textSecondaryDark,
    fontWeight: '500',
  },
  tabTextActive: {
    color: Colors.allWhite,
    fontWeight: '600',
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 8,
    paddingHorizontal: 12,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    height: 44,
    color: Colors.textPrimaryDark,
  },
  listContainer: {
    gap: 12,
  },
  exerciseCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    padding: 16,
    borderRadius: 12,
  },
  exerciseIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: Colors.backgroundDark,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  exerciseInfo: {
    flex: 1,
  },
  exerciseName: {
    color: Colors.textPrimaryDark,
    fontSize: 16,
    fontWeight: '600',
  },
  exerciseCategory: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
    marginTop: 4,
  },
  exerciseBest: {
    alignItems: 'flex-end',
  },
  bestValue: {
    color: Colors.primary,
    fontSize: 16,
    fontWeight: 'bold',
  },
  bestLabel: {
    color: Colors.textSecondaryDark,
    fontSize: 11,
  },
});
