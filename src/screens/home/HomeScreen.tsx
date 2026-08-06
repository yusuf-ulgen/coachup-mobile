import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { Colors } from '../../theme/colors';
import { useAuth } from '../../context/AuthContext';
import { GYM_CONFIG } from '../../config/gym';

export const HomeScreen: React.FC = () => {
  const { profile, signOut } = useAuth();

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* Top Bar Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.welcomeText}>Hoş Geldin 👋</Text>
          <Text style={styles.nameText}>{profile?.name || 'Sporcu'}</Text>
        </View>
        <TouchableOpacity style={styles.gymBadge}>
          <Text style={styles.gymBadgeText}>{GYM_CONFIG.GYM_NAME}</Text>
        </TouchableOpacity>
      </View>

      {/* Quick Workout Card */}
      <View style={styles.workoutHeroCard}>
        <Text style={styles.workoutHeroTitle}>Bugünün Antrenmanı</Text>
        <Text style={styles.workoutHeroSub}>Hypertrophy Upper Body A</Text>
        <TouchableOpacity style={styles.startButton}>
          <Text style={styles.startButtonText}>Antrenmana Başla</Text>
        </TouchableOpacity>
      </View>

      {/* Quick Stats Grid */}
      <Text style={styles.sectionTitle}>Hızlı Durum</Text>
      <View style={styles.statsGrid}>
        <View style={styles.statCard}>
          <Text style={styles.statNumber}>🔥 12</Text>
          <Text style={styles.statLabel}>Günlük Seri</Text>
        </View>

        <View style={styles.statCard}>
          <Text style={styles.statNumber}>🏋️ 24</Text>
          <Text style={styles.statLabel}>Tamamlanan</Text>
        </View>

        <View style={styles.statCard}>
          <Text style={styles.statNumber}>⏱️ 45dk</Text>
          <Text style={styles.statLabel}>Ort. Süre</Text>
        </View>
      </View>

      {/* Quick Actions */}
      <Text style={styles.sectionTitle}>Hızlı İşlemler</Text>
      <View style={styles.actionRow}>
        <TouchableOpacity style={styles.actionBtn}>
          <Text style={styles.actionBtnIcon}>📅</Text>
          <Text style={styles.actionBtnText}>Ders Programı</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.actionBtn}>
          <Text style={styles.actionBtnIcon}>🤝</Text>
          <Text style={styles.actionBtnText}>Koç Randevusu</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  content: {
    padding: 20,
    paddingTop: 60,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  welcomeText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
  },
  nameText: {
    fontSize: 22,
    fontWeight: '800',
    color: Colors.textDark,
  },
  gymBadge: {
    backgroundColor: Colors.primaryLight,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: Colors.primary,
  },
  gymBadgeText: {
    color: Colors.primary,
    fontWeight: '700',
    fontSize: 12,
  },
  workoutHeroCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 20,
    marginBottom: 28,
    borderWidth: 1,
    borderColor: Colors.primary,
  },
  workoutHeroTitle: {
    fontSize: 14,
    color: Colors.primary,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  workoutHeroSub: {
    fontSize: 20,
    fontWeight: '800',
    color: Colors.textDark,
    marginVertical: 8,
  },
  startButton: {
    backgroundColor: Colors.primary,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 8,
  },
  startButtonText: {
    color: Colors.allWhite,
    fontWeight: '700',
    fontSize: 15,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 14,
  },
  statsGrid: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 28,
  },
  statCard: {
    flex: 1,
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  statNumber: {
    fontSize: 18,
    fontWeight: '800',
    color: Colors.textDark,
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
  },
  actionBtn: {
    flex: 1,
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 18,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  actionBtnIcon: {
    fontSize: 24,
    marginBottom: 8,
  },
  actionBtnText: {
    color: Colors.textDark,
    fontWeight: '600',
    fontSize: 13,
  },
});
