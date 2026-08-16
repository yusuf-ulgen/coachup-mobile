import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator } from 'react-native';
import { Flame, Trophy, CalendarCheck, Quote, ChevronLeft } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { useNavigation } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AuthService } from '../../services/authService';
import { StreakService, StreakDayInfo, StreakRecentActivity } from '../../services/streakService';

export const StreakScreen = () => {
  const insets = useSafeAreaInsets();
  const navigation = useNavigation();
  const [loading, setLoading] = useState(true);
  const [currentStreak, setCurrentStreak] = useState(0);
  const [longestStreak, setLongestStreak] = useState(0);
  const [last7Days, setLast7Days] = useState<StreakDayInfo[]>([]);
  const [recentActivities, setRecentActivities] = useState<StreakRecentActivity[]>([]);

  const milestones = [3, 7, 14, 30, 50, 100];

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        const user = await AuthService.getCurrentUser();
        if (user?.id) {
          const streakData = await StreakService.fetchStreakData(user.id);
          setCurrentStreak(streakData.currentStreak);
          setLongestStreak(streakData.longestStreak);
          setLast7Days(streakData.last7Days);
          setRecentActivities(streakData.recentActivities);
        }
      } catch (e) {
        console.error('Error loading streak data in StreakScreen:', e);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ChevronLeft color={Colors.textPrimaryDark} size={24} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Seri & İstatistikler</Text>
        <View style={{ width: 24 }} />
      </View>

      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={[styles.scrollContent, { paddingBottom: 24 + insets.bottom }]}>
          {/* Büyük Alev İkonu ve Gün Sayısı */}
          <View style={styles.fireContainer}>
            <Flame
              size={80}
              color={currentStreak > 0 ? Colors.primary : Colors.textSecondaryDark}
              fill={currentStreak > 0 ? Colors.primary : 'transparent'}
            />
            <Text style={styles.streakCount}>{currentStreak}</Text>
            <Text style={styles.streakLabel}>Günlük Seri</Text>
          </View>

          {/* En Uzun Seri */}
          <View style={styles.longestStreakContainer}>
            <Trophy size={24} color={Colors.warning} />
            <Text style={styles.longestStreakText}>En Uzun Seri: {longestStreak} Gün</Text>
          </View>

          {/* Milestone Rozetleri */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Rozetler</Text>
            <View style={styles.badgesGrid}>
              {milestones.map((milestone) => {
                const isUnlocked = longestStreak >= milestone || currentStreak >= milestone;
                return (
                  <View
                    key={milestone}
                    style={[styles.badgeCard, isUnlocked ? styles.badgeActive : styles.badgeLocked]}
                  >
                    <Trophy
                      size={32}
                      color={isUnlocked ? Colors.warning : Colors.textSecondaryDark}
                    />
                    <Text style={styles.badgeText}>{milestone} Gün</Text>
                  </View>
                );
              })}
            </View>
          </View>

          {/* Son 7 Gün Aktivite Izgara */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Bu Hafta</Text>
            <View style={styles.daysGrid}>
              {last7Days.map((d, idx) => (
                <View key={idx} style={styles.dayCol}>
                  <View style={[styles.dayCircle, d.active ? styles.dayActive : styles.dayInactive]}>
                    {d.active && <CalendarCheck size={16} color={Colors.allWhite} />}
                  </View>
                  <Text style={styles.dayLabel}>{d.day}</Text>
                </View>
              ))}
            </View>
          </View>

          {/* Son Tamamlanan Aktiviteler Listesi */}
          {recentActivities.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Son Aktiviteler</Text>
              {recentActivities.map((activity) => (
                <View key={activity.id} style={styles.activityItem}>
                  <Text style={styles.activityTitle}>{activity.title}</Text>
                  <Text style={styles.activityDate}>{activity.relativeDate}</Text>
                </View>
              ))}
            </View>
          )}

          {/* Motivasyon Mesajı Kartı */}
          <View style={styles.motivationCard}>
            <Quote size={24} color={Colors.primary} style={styles.quoteIcon} />
            <Text style={styles.motivationText}>
              "Başarı her gün tekrarlanan küçük çabaların toplamıdır."
            </Text>
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
  fireContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 24,
  },
  streakCount: {
    fontSize: 48,
    fontWeight: 'bold',
    color: Colors.textPrimaryDark,
    marginTop: 8,
  },
  streakLabel: {
    fontSize: 16,
    color: Colors.textSecondaryDark,
  },
  longestStreakContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.cardDark,
    padding: 16,
    borderRadius: 12,
    marginBottom: 24,
  },
  longestStreakText: {
    color: Colors.textPrimaryDark,
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
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
  badgesGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  badgeCard: {
    width: '30%',
    aspectRatio: 1,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  badgeActive: {
    backgroundColor: 'rgba(255, 193, 7, 0.1)',
    borderColor: Colors.warning,
  },
  badgeLocked: {
    backgroundColor: Colors.cardDark,
    borderColor: Colors.borderDark,
    opacity: 0.5,
  },
  badgeText: {
    color: Colors.textPrimaryDark,
    fontSize: 12,
    fontWeight: '500',
    marginTop: 8,
  },
  daysGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: Colors.cardDark,
    padding: 16,
    borderRadius: 12,
  },
  dayCol: {
    alignItems: 'center',
  },
  dayCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  dayActive: {
    backgroundColor: Colors.primary,
  },
  dayInactive: {
    backgroundColor: Colors.borderDark,
  },
  dayLabel: {
    color: Colors.textSecondaryDark,
    fontSize: 12,
  },
  activityItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: Colors.cardDark,
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  activityTitle: {
    color: Colors.textPrimaryDark,
    fontSize: 14,
    fontWeight: '500',
  },
  activityDate: {
    color: Colors.textSecondaryDark,
    fontSize: 12,
  },
  motivationCard: {
    backgroundColor: Colors.primary + '20',
    padding: 20,
    borderRadius: 12,
    alignItems: 'center',
    borderLeftWidth: 4,
    borderLeftColor: Colors.primary,
  },
  quoteIcon: {
    marginBottom: 8,
  },
  motivationText: {
    color: Colors.textPrimaryDark,
    fontSize: 14,
    fontStyle: 'italic',
    textAlign: 'center',
    lineHeight: 20,
  }
});
