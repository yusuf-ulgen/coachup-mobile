import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Share,
} from 'react-native';
import { Colors } from '../../theme/colors';
import {
  CheckCircle2,
  Clock,
  Flame,
  Heart,
  Activity,
  Watch,
  Share2,
  X,
} from 'lucide-react-native';
import { useNavigation, useRoute } from '@react-navigation/native';

export const WorkoutSummaryScreen: React.FC = () => {
  const navigation = useNavigation<any>();
  const route = useRoute<any>();

  const {
    training = { title: 'Fitness', category: { emoji: '🏋️' } },
    durationSeconds = 1800,
    calories = 1,
    avgHeartRate = null,
    maxHeartRate = null,
    perceivedEffort = 'Normal',
    perceivedEmoji = '😐',
  } = route.params || {};

  const formatDuration = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const secs = sec % 60;
    return `${mins}:${String(secs).padStart(2, '0')}`;
  };

  const todayStr = new Date().toISOString().slice(0, 10);

  const handleShare = async () => {
    try {
      await Share.share({
        message: `CoachUP ile ${training.title} antrenmanımı tamamladım! Süre: ${formatDuration(
          durationSeconds
        )}, Kalori: ${calories} kcal 🔥`,
      });
    } catch (e) {
      console.error('Share error:', e);
    }
  };

  return (
    <View style={styles.container}>
      {/* ── Header ────────────────────────────────────────────────────────── */}
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => navigation.navigate('MainTabs')} style={styles.backBtn}>
          <X size={20} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Antrenman Özeti</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* ── Activity Header Banner (Image 4 Matching) ───────────────────── */}
        <View style={styles.activityBanner}>
          <View style={styles.emojiBadgeCircle}>
            <Text style={{ fontSize: 24 }}>{training.category?.emoji || '🏋️'}</Text>
          </View>

          <View style={{ flex: 1 }}>
            <Text style={styles.activityBannerTitle}>{training.title || 'Fitness'}</Text>
            <Text style={styles.activityBannerSub}>{todayStr}</Text>
          </View>

          <View style={styles.checkCircleBadge}>
            <CheckCircle2 size={24} color="#4CAF50" fill="rgba(76,175,80,0.2)" />
          </View>
        </View>

        {/* ── ÖZET Section Title ─────────────────────────────────────────── */}
        <Text style={styles.sectionHeaderLabel}>ÖZET</Text>

        {/* ── 2x2 Metric Cards Grid (Image 4 Matching) ───────────────────── */}
        <View style={styles.metricGrid}>
          {/* Top-Left: Duration */}
          <View style={styles.metricCard}>
            <View style={styles.metricIconCircle}>
              <Clock size={18} color={Colors.primary} />
            </View>
            <Text style={styles.metricVal}>{formatDuration(durationSeconds)}</Text>
            <Text style={styles.metricLbl}>Süre</Text>
          </View>

          {/* Top-Right: Calories */}
          <View style={styles.metricCard}>
            <View style={styles.metricIconCircle}>
              <Flame size={18} color={Colors.primary} />
            </View>
            <Text style={styles.metricVal}>{calories}</Text>
            <Text style={styles.metricLbl}>Kalori</Text>
          </View>

          {/* Bottom-Left: Avg Heart Rate */}
          <View style={styles.metricCard}>
            <View style={[styles.metricIconCircle, { backgroundColor: 'rgba(255,59,48,0.12)' }]}>
              <Heart size={18} color="#FF3B30" />
            </View>
            <Text style={styles.metricVal}>{avgHeartRate ? avgHeartRate : '—'}</Text>
            <Text style={styles.metricLbl}>Ort. Nabız (bpm)</Text>
          </View>

          {/* Bottom-Right: Max Heart Rate */}
          <View style={styles.metricCard}>
            <View style={[styles.metricIconCircle, { backgroundColor: 'rgba(255,96,71,0.12)' }]}>
              <Activity size={18} color={Colors.primary} />
            </View>
            <Text style={styles.metricVal}>{maxHeartRate ? maxHeartRate : '—'}</Text>
            <Text style={styles.metricLbl}>Maks. Nabız (bpm)</Text>
          </View>
        </View>

        {/* ── Perceived Effort Feeling Card (Image 4 Matching) ───────────── */}
        <View style={styles.feelingCard}>
          <Text style={{ fontSize: 32 }}>{perceivedEmoji}</Text>
          <View style={{ flex: 1 }}>
            <Text style={styles.feelingTitle}>Nasıl hissettin?</Text>
            <Text style={styles.feelingVal}>{perceivedEffort}</Text>
          </View>
        </View>

        {/* ── Wearable Prompt Banner (Image 4 Matching) ─────────────────── */}
        <View style={styles.wearableBanner}>
          <View style={styles.watchIconCircle}>
            <Watch size={20} color={Colors.primary} />
          </View>
          <Text style={styles.wearableBannerText}>
            Akıllı saat bağlayarak daha detaylı analizlere ulaşabilirsiniz.
          </Text>
        </View>
      </ScrollView>

      {/* ── Bottom Actions Row (Share & Kapat) ──────────────────────────── */}
      <View style={styles.bottomBarRow}>
        <TouchableOpacity style={styles.shareBtnCircle} onPress={handleShare} activeOpacity={0.8}>
          <Share2 size={20} color={Colors.primary} />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.closeMainBtn}
          onPress={() => navigation.navigate('MainTabs')}
          activeOpacity={0.85}
        >
          <Text style={styles.closeMainBtnText}>Kapat</Text>
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
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
  },
  backBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingBottom: 120,
    gap: 16,
  },
  activityBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 16,
    gap: 14,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  emojiBadgeCircle: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: 'rgba(255,255,255,0.06)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  activityBannerTitle: {
    fontSize: 20,
    fontWeight: '800',
    color: Colors.textDark,
  },
  activityBannerSub: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  checkCircleBadge: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sectionHeaderLabel: {
    fontSize: 12,
    fontWeight: '800',
    color: Colors.textSecondaryDark,
    letterSpacing: 1.5,
    marginTop: 6,
  },
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  metricCard: {
    width: '48%',
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 18,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  metricIconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255,96,71,0.12)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 14,
  },
  metricVal: {
    fontSize: 24,
    fontWeight: '800',
    color: Colors.textDark,
  },
  metricLbl: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
  feelingCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 18,
    gap: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  feelingTitle: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  feelingVal: {
    fontSize: 18,
    fontWeight: '800',
    color: Colors.textDark,
    marginTop: 2,
  },
  wearableBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,96,71,0.08)',
    borderRadius: 16,
    padding: 16,
    gap: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,96,71,0.2)',
  },
  watchIconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255,96,71,0.15)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  wearableBannerText: {
    flex: 1,
    fontSize: 13,
    color: Colors.textDark,
    lineHeight: 18,
  },
  bottomBarRow: {
    position: 'absolute',
    bottom: 30,
    left: 20,
    right: 20,
    flexDirection: 'row',
    gap: 12,
  },
  shareBtnCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Colors.cardDark,
    borderWidth: 1,
    borderColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  closeMainBtn: {
    flex: 1,
    height: 56,
    borderRadius: 100,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
  },
  closeMainBtnText: {
    color: Colors.allWhite,
    fontSize: 17,
    fontWeight: '700',
  },
});
