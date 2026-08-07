import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Image,
  TextInput,
  ScrollView,
} from 'react-native';
import { ArrowLeft, User as UserIcon, Search } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { CoachService, Coach } from '../../services/coachService';

interface CoachListScreenProps {
  navigation?: any;
}

export const CoachListScreen: React.FC<CoachListScreenProps> = ({ navigation }) => {
  const [coaches, setCoaches] = useState<Coach[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [genderFilter, setGenderFilter] = useState<'all' | 'male' | 'female'>('all');

  useEffect(() => {
    const loadCoaches = async () => {
      setLoading(true);
      try {
        const data = await CoachService.fetchCoaches();
        setCoaches(data);
      } catch (e) {
        console.error('Failed to load coaches:', e);
      } finally {
        setLoading(false);
      }
    };
    loadCoaches();
  }, []);

  const filteredCoaches = coaches.filter(coach => {
    const searchLower = searchQuery.toLowerCase();
    const matchesSearch = !searchQuery ||
      coach.name?.toLowerCase().includes(searchLower) ||
      coach.surname?.toLowerCase().includes(searchLower) ||
      coach.speciality?.toLowerCase().includes(searchLower) ||
      (coach as any).specialization?.toLowerCase().includes(searchLower);
    
    const matchesGender = genderFilter === 'all' || coach.gender === genderFilter;
    return matchesSearch && matchesGender;
  });

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        {navigation?.canGoBack() && (
          <TouchableOpacity
            onPress={() => navigation.goBack()}
            style={styles.backButton}
          >
            <ArrowLeft size={24} color={Colors.textDark} />
          </TouchableOpacity>
        )}
        <Text style={styles.headerTitle}>Kulüp Koçları</Text>
      </View>

      <View style={styles.searchContainer}>
        <View style={styles.searchInputContainer}>
          <Search size={20} color={Colors.textSecondaryDark} style={styles.searchIcon} />
          <TextInput
            style={styles.searchInput}
            placeholder="Koç veya uzmanlık ara..."
            placeholderTextColor={Colors.textSecondaryDark}
            value={searchQuery}
            onChangeText={setSearchQuery}
          />
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterContainer}>
          {(['all', 'male', 'female'] as const).map(filter => (
            <TouchableOpacity
              key={filter}
              style={[styles.filterChip, genderFilter === filter && styles.filterChipActive]}
              onPress={() => setGenderFilter(filter)}
            >
              <Text style={[styles.filterChipText, genderFilter === filter && styles.filterChipTextActive]}>
                {filter === 'all' ? 'Tümü' : filter === 'male' ? 'Erkek' : 'Kadın'}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {loading ? (
        <View style={styles.loadingBox}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : coaches.length === 0 ? (
        <View style={styles.emptyBox}>
          <Text style={styles.emptyText}>Henüz kayıtlı koç bulunmuyor.</Text>
        </View>
      ) : (
        <FlatList
          data={filteredCoaches}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => {
            const fullName = `${item.name || ''} ${item.surname || ''}`.trim() || 'Koç';
            return (
              <TouchableOpacity 
                style={styles.coachCard}
                activeOpacity={0.7}
                onPress={() => navigation?.navigate('CoachDetail', { coachId: item.id })}
              >
                {item.avatar_url ? (
                  <Image source={{ uri: item.avatar_url }} style={styles.avatar} />
                ) : (
                  <View style={styles.avatarPlaceholder}>
                    <UserIcon size={24} color={Colors.allWhite} />
                  </View>
                )}

                <View style={styles.coachInfo}>
                  <Text style={styles.coachName}>{fullName}</Text>
                  <Text style={styles.coachBranch}>
                    {item.speciality || 'Fitness & Vücut Geliştirme'}
                  </Text>
                  {item.rating ? (
                    <Text style={styles.coachRating}>{item.rating} ⭐</Text>
                  ) : null}
                </View>

                <TouchableOpacity 
                  style={styles.bookButton} 
                  activeOpacity={0.8}
                  onPress={() => navigation?.navigate('Appointments', { coachId: item.id })}
                >
                  <Text style={styles.bookButtonText}>Randevu</Text>
                </TouchableOpacity>
              </TouchableOpacity>
            );
          }}
        />
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
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
  },
  backButton: {
    marginRight: 12,
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: Colors.textDark,
  },
  searchContainer: {
    paddingHorizontal: 20,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
  },
  searchInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.backgroundDark,
    borderRadius: 12,
    paddingHorizontal: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    height: 44,
    color: Colors.textDark,
    fontSize: 15,
  },
  filterContainer: {
    flexDirection: 'row',
  },
  filterChip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: Colors.backgroundDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    marginRight: 8,
  },
  filterChipActive: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  filterChipText: {
    color: Colors.textSecondaryDark,
    fontSize: 14,
    fontWeight: '600',
  },
  filterChipTextActive: {
    color: Colors.allWhite,
  },
  loadingBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  emptyText: {
    fontSize: 15,
    color: Colors.textSecondaryDark,
  },
  listContent: {
    padding: 20,
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
  avatar: {
    width: 50,
    height: 50,
    borderRadius: 25,
    marginRight: 14,
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
