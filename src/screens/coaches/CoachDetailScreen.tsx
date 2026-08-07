import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, Image, TouchableOpacity, ScrollView, ActivityIndicator } from 'react-native';
import { ArrowLeft, MessageCircle, Calendar } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { CoachService, Coach } from '../../services/coachService';

export const CoachDetailScreen: React.FC<any> = ({ route, navigation }) => {
  const { coachId } = route.params;
  const [coach, setCoach] = useState<Coach | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchCoachDetail = async () => {
      try {
        const data = await CoachService.fetchCoaches();
        const found = data.find(c => c.id === coachId);
        if (found) setCoach(found);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchCoachDetail();
  }, [coachId]);

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={Colors.primary} />
      </View>
    );
  }

  if (!coach) {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
            <ArrowLeft size={24} color={Colors.textDark} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Koç Bulunamadı</Text>
        </View>
      </View>
    );
  }

  const fullName = `${coach.name || ''} ${coach.surname || ''}`.trim() || 'Koç';
  const bio = (coach as any).bio || `${fullName}, alanında uzman ve deneyimli bir eğitmendir. Hedeflerinize ulaşmanız için size özel programlar hazırlar.`;
  const specialties = (coach as any).specialties || ['Fitness', 'Crossfit', 'Kilo Verme', 'Kas Geliştirme'];
  const certificates = (coach as any).certificates || ['ACE Certified Personal Trainer', 'CrossFit Level 1', 'First Aid / CPR'];
  const experienceYears = (coach as any).experience_years || 5;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ArrowLeft size={24} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Koç Profili</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.profileHeader}>
          {coach.avatar_url ? (
            <Image source={{ uri: coach.avatar_url }} style={styles.avatar} />
          ) : (
            <View style={styles.avatarPlaceholder}>
              <Text style={styles.avatarPlaceholderText}>{fullName.charAt(0)}</Text>
            </View>
          )}
          <View style={styles.basicInfo}>
            <View style={styles.nameRow}>
              <Text style={styles.name}>{fullName}</Text>
              {coach.gender && (
                <View style={styles.genderBadge}>
                  <Text style={styles.genderText}>{coach.gender === 'male' ? '♂' : '♀'}</Text>
                </View>
              )}
            </View>
            <Text style={styles.branch}>{coach.speciality || 'Fitness Eğitmeni'}</Text>
            <View style={styles.statsRow}>
              <View style={styles.statChip}>
                <Text style={styles.statChipText}>{coach.rating || '4.8'} ⭐ Rating</Text>
              </View>
              <View style={styles.statChip}>
                <Text style={styles.statChipText}>{experienceYears} Yıl Deneyim</Text>
              </View>
            </View>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Biyografi</Text>
          <Text style={styles.bioText}>{bio}</Text>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Uzmanlık Alanları</Text>
          <View style={styles.flowContainer}>
            {specialties.map((item: string, index: number) => (
              <View key={index} style={styles.flowChip}>
                <Text style={styles.flowChipText}>{item}</Text>
              </View>
            ))}
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Sertifikalar</Text>
          <View style={styles.flowContainer}>
            {certificates.map((item: string, index: number) => (
              <View key={index} style={styles.flowChip}>
                <Text style={styles.flowChipText}>{item}</Text>
              </View>
            ))}
          </View>
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity 
          style={styles.messageButton}
          onPress={() => navigation.navigate('CoachChat', { coachId: coach.id, coachName: fullName })}
        >
          <MessageCircle size={20} color={Colors.primary} />
          <Text style={styles.messageButtonText}>Mesaj Gönder</Text>
        </TouchableOpacity>
        
        <TouchableOpacity 
          style={styles.bookButton}
          onPress={() => navigation.navigate('Appointments', { coachId: coach.id })}
        >
          <Calendar size={20} color={Colors.allWhite} />
          <Text style={styles.bookButtonText}>Randevu Al</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
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
  content: {
    padding: 20,
    paddingBottom: 40,
  },
  profileHeader: {
    flexDirection: 'row',
    marginBottom: 24,
  },
  avatar: {
    width: 90,
    height: 90,
    borderRadius: 45,
    marginRight: 16,
  },
  avatarPlaceholder: {
    width: 90,
    height: 90,
    borderRadius: 45,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  avatarPlaceholderText: {
    fontSize: 36,
    color: Colors.allWhite,
    fontWeight: '700',
  },
  basicInfo: {
    flex: 1,
    justifyContent: 'center',
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  name: {
    fontSize: 22,
    fontWeight: '700',
    color: Colors.textDark,
    marginRight: 8,
  },
  genderBadge: {
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  genderText: {
    color: Colors.primary,
    fontSize: 14,
    fontWeight: 'bold',
  },
  branch: {
    fontSize: 16,
    color: Colors.textSecondaryDark,
    marginTop: 4,
    marginBottom: 8,
  },
  statsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  statChip: {
    backgroundColor: Colors.cardDark,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  statChipText: {
    color: Colors.textDark,
    fontSize: 12,
    fontWeight: '600',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 12,
  },
  bioText: {
    fontSize: 15,
    color: Colors.textSecondaryDark,
    lineHeight: 22,
  },
  flowContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  flowChip: {
    backgroundColor: Colors.cardDark,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  flowChipText: {
    color: Colors.textDark,
    fontSize: 14,
  },
  footer: {
    flexDirection: 'row',
    padding: 20,
    backgroundColor: Colors.cardDark,
    borderTopWidth: 1,
    borderTopColor: Colors.borderDark,
    gap: 12,
  },
  messageButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.backgroundDark,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Colors.primary,
    paddingVertical: 14,
    gap: 8,
  },
  messageButtonText: {
    color: Colors.primary,
    fontSize: 16,
    fontWeight: '700',
  },
  bookButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.primary,
    borderRadius: 16,
    paddingVertical: 14,
    gap: 8,
  },
  bookButtonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '700',
  },
});
