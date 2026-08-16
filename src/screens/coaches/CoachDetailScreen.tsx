import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, Image, TouchableOpacity, ScrollView, ActivityIndicator } from 'react-native';
import { ArrowLeft, MessageCircle, Calendar } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { CoachService, Coach } from '../../services/coachService';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export const CoachDetailScreen: React.FC<any> = ({ route, navigation }) => {
  const insets = useSafeAreaInsets();
  const { coachId } = route.params;
  const [coach, setCoach] = useState<Coach | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchCoachDetail = async () => {
      try {
        const found = await CoachService.fetchCoachDetail(coachId);
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
  const bio = coach.bio || `${fullName}, salonumuzun uzman eğitmenidir.`;
  
  // Format specializations
  let specialties: string[] = [];
  if (Array.isArray(coach.specializations)) {
    specialties = coach.specializations;
  } else if (typeof coach.specializations === 'string' && coach.specializations) {
    specialties = coach.specializations.split(',').map((s) => s.trim());
  } else if (coach.specialty) {
    specialties = [coach.specialty];
  }

  // Format certifications
  let certificates: string[] = [];
  if (Array.isArray(coach.certifications)) {
    certificates = coach.certifications;
  } else if (typeof coach.certifications === 'string' && coach.certifications) {
    certificates = coach.certifications.split(',').map((c) => c.trim());
  }

  const experienceYears = coach.experience_years || null;

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ArrowLeft size={24} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Koç Profili</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
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
            <Text style={styles.branch}>{coach.specialty || 'Fitness Eğitmeni'}</Text>
            <View style={styles.statsRow}>
              {coach.rating ? (
                <View style={styles.statChip}>
                  <Text style={styles.statChipText}>{coach.rating} ⭐ Rating</Text>
                </View>
              ) : null}
              {experienceYears ? (
                <View style={styles.statChip}>
                  <Text style={styles.statChipText}>{experienceYears} Yıl Deneyim</Text>
                </View>
              ) : null}
            </View>
          </View>
        </View>

        {bio ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Biyografi</Text>
            <Text style={styles.bioText}>{bio}</Text>
          </View>
        ) : null}

        {specialties.length > 0 ? (
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
        ) : null}

        {certificates.length > 0 ? (
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
        ) : null}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(20, insets.bottom + 12) }]}>
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
    backgroundColor: '#0F172A',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#0F172A',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: '#1E293B',
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
  },
  backButton: {
    marginRight: 12,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  content: {
    padding: 20,
    paddingBottom: 40,
    gap: 16,
  },
  profileHeader: {
    flexDirection: 'row',
    marginBottom: 8,
  },
  avatar: {
    width: 80,
    height: 80,
    borderRadius: 40,
    marginRight: 16,
  },
  avatarPlaceholder: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  avatarPlaceholderText: {
    fontSize: 32,
    color: Colors.allWhite,
    fontWeight: '700',
  },
  basicInfo: {
    flex: 1,
    justifyContent: 'center',
    gap: 4,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  name: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  genderBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    backgroundColor: '#334155',
    borderRadius: 4,
  },
  genderText: {
    fontSize: 12,
    color: Colors.textDark,
  },
  branch: {
    fontSize: 13,
    color: Colors.primary,
    fontWeight: '600',
  },
  statsRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 4,
  },
  statChip: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#334155',
  },
  statChipText: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
    fontWeight: '500',
  },
  section: {
    backgroundColor: '#1E293B',
    padding: 16,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#334155',
    gap: 8,
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textDark,
  },
  bioText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    lineHeight: 20,
  },
  flowContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  flowChip: {
    backgroundColor: '#0F172A',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  flowChipText: {
    fontSize: 12,
    color: Colors.textDark,
  },
  footer: {
    flexDirection: 'row',
    padding: 16,
    backgroundColor: '#1E293B',
    borderTopWidth: 1,
    borderTopColor: '#334155',
    gap: 12,
  },
  messageButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: Colors.primary,
    gap: 8,
  },
  messageButtonText: {
    color: Colors.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  bookButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 10,
    backgroundColor: Colors.primary,
    gap: 8,
  },
  bookButtonText: {
    color: Colors.allWhite,
    fontSize: 14,
    fontWeight: '600',
  },
});
