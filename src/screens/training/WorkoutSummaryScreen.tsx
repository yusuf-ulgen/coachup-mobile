import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
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
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { WorkoutShareSheet } from './WorkoutShareSheet';
import { ScreenContainer } from '../../components/ScreenContainer';

export const WorkoutSummaryScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  const [shareSheetVisible, setShareSheetVisible] = useState(false);

  const {
    training = { title: 'Fitness', category: { emoji: '🏋️' } },
    durationSeconds = 1800,
    distanceKm = 0,
    calories = 1,
    avgHeartRate = null,
    maxHeartRate = null,
    avgPaceMinPerKm = 0,
    avgSpeedKmh = 0,
    routePoints = [],
    perceivedEffort = 'Normal',
    perceivedEmoji = '😐',
  } = route.params || {};

  const formatDuration = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const secs = sec % 60;
    return `${mins}:${String(secs).padStart(2, '0')}`;
  };

  const todayStr = new Date().toLocaleDateString('tr-TR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  const handleShare = () => {
    setShareSheetVisible(true);
  };

  return (
    <ScreenContainer includeTopInset={true} includeBottomInset={true}>
      <View style={styles.container}>
        {/* ── Header ────────────────────────────────────────────────────────── */}
        <View style={styles.headerRow}>
          <TouchableOpacity onPress={() => navigation.navigate('MainTabs')} style={styles.backBtn} activeOpacity={0.8}>
            <X size={20} color={Colors.textDark} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Antrenman Özeti</Text>
          <View style={{ width: 40 }} />
        </View>

        <ScrollView contentContainerStyle={[styles.scrollContent, { paddingBottom: 100 + insets.bottom }]} showsVerticalScrollIndicator={false}>
          {/* ── Activity Header Banner ─────────────────────────────────── */}
          <View style={styles.activityBanner}>
            <View style={styles.emojiBadgeCircle}>
              <Text style={{ fontSize: 26 }}>{training.category?.emoji || '🏋️'}</Text>
            </View>

            <View style={{ flex: 1 }}>
              <Text style={styles.activityBannerTitle}>{training.title || 'Fitness'}</Text>
              <Text style={styles.activityBannerSub}>{todayStr}</Text>
            </View>

            <View style={styles.checkCircleBadge}>
              <CheckCircle2 size={26} color="#4CAF50" fill="rgba(76,175,80,0.18)" />
            </View>
          </View>

          {/* ── ÖZET Section Title ─────────────────────────────────────────── */}
          <View style={styles.sectionHeaderRow}>
            <Text style={styles.sectionHeaderLabel}>METRİKLER & ÖZET</Text>
          </View>

          {/* ── 2x2 Metric Cards Grid ────────────────────────────────────────── */}
          <View style={styles.metricGrid}>
            {/* Top-Left: Duration */}
            <View style={styles.metricCard}>
              <View style={styles.metricHeader}>
                <View style={styles.metricIconCircle}>
                  <Clock size={18} color={Colors.primary} />
                </View>
                <Text style={styles.metricUnit}>dk</Text>
              </View>
              <Text style={styles.metricVal}>{formatDuration(durationSeconds)}</Text>
              <Text style={styles.metricLbl}>Süre</Text>
            </View>

            {/* Top-Right: Calories */}
            <View style={styles.metricCard}>
              <View style={styles.metricHeader}>
                <View style={styles.metricIconCircle}>
                  <Flame size={18} color={Colors.primary} />
                </View>
                <Text style={styles.metricUnit}>kcal</Text>
              </View>
              <Text style={styles.metricVal}>{calories && calories > 0 ? calories : '—'}</Text>
              <Text style={styles.metricLbl}>Kalori</Text>
            </View>

            {/* Bottom-Left: Avg Heart Rate */}
            <View style={styles.metricCard}>
              <View style={styles.metricHeader}>
                <View style={[styles.metricIconCircle, { backgroundColor: 'rgba(255,59,48,0.12)' }]}>
                  <Heart size={18} color="#FF3B30" />
                </View>
                <Text style={styles.metricUnit}>bpm</Text>
              </View>
              <Text style={styles.metricVal}>{avgHeartRate ? avgHeartRate : '—'}</Text>
              <Text style={styles.metricLbl}>Ort. Nabız</Text>
            </View>

            {/* Bottom-Right: Max Heart Rate */}
            <View style={styles.metricCard}>
              <View style={styles.metricHeader}>
                <View style={[styles.metricIconCircle, { backgroundColor: 'rgba(255,96,71,0.12)' }]}>
                  <Activity size={18} color={Colors.primary} />
                </View>
                <Text style={styles.metricUnit}>bpm</Text>
              </View>
              <Text style={styles.metricVal}>{maxHeartRate ? maxHeartRate : '—'}</Text>
              <Text style={styles.metricLbl}>Maks. Nabız</Text>
            </View>
          </View>

          {/* ── Perceived Effort Feeling Card ───────────────────────────── */}
          <View style={styles.feelingCard}>
            <View style={styles.emojiContainer}>
              <Text style={{ fontSize: 32 }}>{perceivedEmoji}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.feelingTitle}>Nasıl hissettin?</Text>
              <Text style={styles.feelingVal}>{perceivedEffort}</Text>
            </View>
          </View>

          {/* ── Wearable Prompt Banner ─────────────────────────────────── */}
          <View style={styles.wearableBanner}>
            <View style={styles.watchIconCircle}>
              <Watch size={20} color={Colors.primary} />
            </View>
            <Text style={styles.wearableBannerText}>
              Akıllı saat bağlayarak daha detaylı nabız ve tempo analizlerine ulaşabilirsiniz.
            </Text>
          </View>
        </ScrollView>

        {/* ── Bottom Actions Row (Share & Kapat) ──────────────────────────── */}
        <View style={[styles.bottomBarRow, { bottom: Math.max(20, insets.bottom + 12) }]}>
          <TouchableOpacity style={styles.shareBtnCircle} onPress={handleShare} activeOpacity={0.8}>
            <Share2 size={22} color={Colors.primary} />
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.closeMainBtn}
            onPress={() => navigation.navigate('MainTabs')}
            activeOpacity={0.85}
          >
            <Text style={styles.closeMainBtnText}>Tamamla & Kapat</Text>
          </TouchableOpacity>
        </View>

        <WorkoutShareSheet
          visible={shareSheetVisible}
          onDismiss={() => setShareSheetVisible(false)}
          training={training}
          durationSeconds={durationSeconds}
          distanceKm={distanceKm}
          totalCalories={calories}
          avgPaceMinPerKm={avgPaceMinPerKm}
          avgSpeedKmh={avgSpeedKmh}
          routePoints={routePoints || training?.route_points || training?.routePoints || []}
        />
      </View>
    </ScreenContainer>
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
    paddingVertical: 14,
  },
  backBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: Colors.textDark,
    letterSpacing: -0.3,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingBottom: 110,
    gap: 16,
  },
  activityBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 22,
    padding: 18,
    gap: 14,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  emojiBadgeCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: 'rgba(255,96,71,0.1)',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,96,71,0.2)',
  },
  activityBannerTitle: {
    fontSize: 20,
    fontWeight: '800',
    color: Colors.textDark,
    letterSpacing: -0.3,
  },
  activityBannerSub: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  checkCircleBadge: {
    width: 42,
    height: 42,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sectionHeaderRow: {
    marginTop: 4,
    marginBottom: -4,
  },
  sectionHeaderLabel: {
    fontSize: 12,
    fontWeight: '800',
    color: Colors.textSecondaryDark,
    letterSpacing: 1.5,
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
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  metricHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  metricIconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255,96,71,0.12)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  metricUnit: {
    fontSize: 11,
    fontWeight: '700',
    color: Colors.textSecondaryDark,
  },
  metricVal: {
    fontSize: 26,
    fontWeight: '900',
    color: Colors.textDark,
    letterSpacing: -0.5,
  },
  metricLbl: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 4,
    fontWeight: '600',
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
  emojiContainer: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  feelingTitle: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    fontWeight: '600',
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
    borderRadius: 18,
    padding: 16,
    gap: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,96,71,0.2)',
  },
  watchIconCircle: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: 'rgba(255,96,71,0.15)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  wearableBannerText: {
    flex: 1,
    fontSize: 13,
    color: Colors.textDark,
    lineHeight: 18,
    fontWeight: '500',
  },
  bottomBarRow: {
    position: 'absolute',
    bottom: 20,
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
    borderRadius: 28,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
  },
  closeMainBtnText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '700',
  },
});
